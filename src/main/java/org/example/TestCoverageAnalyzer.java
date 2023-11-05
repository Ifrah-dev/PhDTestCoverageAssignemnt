package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestCoverageAnalyzer {

    public static void main(String[] args) throws IOException, InterruptedException, GitAPIException {
        if (args.length != 1) {
            System.out.println("Usage: TestCoverageAnalyzer <git_repo_directory>");
            return;
        }

        String gitRepoUrl = args[0];
        String destinationDirectory = "test_coverage_projects";

        File gitRepoDirectory = new File(destinationDirectory, getRepositoryName(gitRepoUrl));

        if (!gitRepoDirectory.exists()) {
            // Clone the repository if it's public
            // Step 0: (Clone repository as not exists)
            long step0StartTime = System.nanoTime();
            Git.cloneRepository().setURI(gitRepoUrl).setDirectory(gitRepoDirectory).call();
            System.out.println("Repository cloned from " + gitRepoUrl + " to " + gitRepoDirectory.getAbsolutePath());
            long step0EndTime = System.nanoTime();
            long step0Time = (step0EndTime - step0StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 0: (Clone repository as not exists) Execution Time: " + step0Time + " ms");
        }

        Git git = Git.open(gitRepoDirectory);

        // Step 1: (Add Jacoco Dependency and Plugin to Cloned Repo)
        long step1StartTime = System.nanoTime();
        modifyPomXml(gitRepoDirectory + "/pom.xml");
        long step1EndTime = System.nanoTime();
        long step1Time = (step1EndTime - step1StartTime) / 1_000_000; // in milliseconds
        System.out.println("Step 1: (Add Jacoco Dependency and Plugin to Cloned Repo) Execution Time: " + step1Time + " ms");

        // Step 2: (Run Maven Test on project)
        long step2StartTime = System.nanoTime();
        runMavenTest(gitRepoDirectory.getPath(), "runMavenTest_" + getRepositoryName(gitRepoUrl) + "_output.log");
        long step2EndTime = System.nanoTime();
        long step2Time = (step2EndTime - step2StartTime) / 1_000_000; // in milliseconds
        System.out.println("Step 2: (Run Maven Test on project) Execution Time: " + step2Time + " ms");


        List<File> classFiles = new ArrayList<>();
        // Step 3: (Count Methods)
        long step3StartTime = System.nanoTime();
        int[] counts = countMethods(String.valueOf(gitRepoDirectory), classFiles);
        long step3EndTime = System.nanoTime();
        long step3Time = (step3EndTime - step3StartTime) / 1_000_000; // in milliseconds
        System.out.println("Step 3: (Count Methods) Execution Time: " + step3Time + " ms");
        try {
            // Step 4: (Coverage Info)
            long step4StartTime = System.nanoTime();
            String list = coverageInfo(gitRepoDirectory + "/target/jacoco.exec", classFiles);
            long step4EndTime = System.nanoTime();
            long step4Time = (step4EndTime - step4StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 4: (Coverage Info) Execution Time: " + step4Time + " ms");

            // Print the number of classes

            Root objectRoot = new Root();
            objectRoot.location = gitRepoDirectory.getPath();
            objectRoot.stat_of_repository = new StatOfRepository();
            objectRoot.stat_of_repository.num_classes = counts[2];
            // Step 5: (List Java Files)
            long step5StartTime = System.nanoTime();
            objectRoot.stat_of_repository.num_java_files = listjavaFiles(String.valueOf(gitRepoDirectory)).size();
            long step5EndTime = System.nanoTime();
            long step5Time = (step5EndTime - step5StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 5: (List Java Files) Execution Time: " + step5Time + " ms");
            objectRoot.stat_of_repository.num_methods = counts[0];
            objectRoot.stat_of_repository.num_test_methods = counts[1];

            //objectRoot.test_coverage_against_methods = new TestCoverageAgainstMethods();
            objectRoot.test_coverage_against_methods = list;

            ObjectMapper objectMapper = new ObjectMapper();

            // Serialize object to JSON
            String jsonString = objectMapper.writeValueAsString(objectRoot);

            try {
                String outputFileName = "test_coverage.json";
                Path path = Paths.get(outputFileName);
                byte[] jsonData = jsonString.getBytes();

                Files.write(path, jsonData);
                System.out.println("JSON data has been written to the file.");
            } catch (IOException e) {
                System.out.println("An error occurred while writing to the file: " + e.getMessage());
            }
            System.out.println("Final Result " + jsonString);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    public static void modifyPomXml(String pomXmlFilePath) throws IOException {
        // Read the existing pom.xml content
        String pomXmlContent = new String(Files.readAllBytes(Paths.get(pomXmlFilePath)));

        // Check if the specified dependency and plugin XML snippets already exist in the file
        String dependencyXml = "<dependency>\n" +
                "    <groupId>org.jacoco</groupId>\n" +
                "    <artifactId>jacoco-maven-plugin</artifactId>\n" +
                "    <version>0.8.5</version>\n" +
                "</dependency>";
        String pluginXml = "<plugin>\n" +
                "    <groupId>org.jacoco</groupId>\n" +
                "    <artifactId>jacoco-maven-plugin</artifactId>\n" +
                "    <version>0.8.5</version>\n" +
                "    <executions>\n" +
                "        <execution>\n" +
                "            <goals>\n" +
                "                <goal>prepare-agent</goal>\n" +
                "            </goals>\n" +
                "        </execution>\n" +
                "        <execution>\n" +
                "            <id>report</id>\n" +
                "            <phase>test</phase>\n" +
                "            <goals>\n" +
                "                <goal>report</goal>\n" +
                "            </goals>\n" +
                "        </execution>\n" +
                "    </executions>\n" +
                "</plugin>";

        if (pomXmlContent.contains(dependencyXml) && pomXmlContent.contains(pluginXml)) {
            // The specified dependency and plugin already exist; no need to modify the file.
            return;
        }

        // Append the dependency and plugin XML to the pom.xml content
        String newPomXmlContent = pomXmlContent.replace("</dependencies>", dependencyXml + "\n</dependencies>")
                .replace("</plugins>\n" +
                        "  </build>", pluginXml + "</plugins>\n" +
                        "  </build>");

        // Write the modified content back to the pom.xml file
        Files.write(Paths.get(pomXmlFilePath), newPomXmlContent.getBytes());
    }

    public static void runMavenTest(String projectDirectory, String logFilePath) throws IOException, InterruptedException {
        // Create a log file to save the command output
        File logFile = new File(logFilePath);

        // Specify the path to your Java 11 installation
        String java11Path = "/opt/homebrew/Cellar/openjdk@11/11.0.21/libexec/openjdk.jdk/Contents/Home";

        // Create a ProcessBuilder to run the 'mvn test' command
        ProcessBuilder processBuilder = new ProcessBuilder("mvn", "clean", "install");
        processBuilder.directory(new File(projectDirectory));
        processBuilder.redirectErrorStream(true);

        // Redirect the process output to a log file
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

        // Set the JAVA_HOME environment variable to point to Java 11
        processBuilder.environment().put("JAVA_HOME", java11Path);

        // Start the process
        Process process = processBuilder.start();

        // Wait for the process to complete
        int exitCode = process.waitFor();

        // Check the exit code to determine if the command was successful
        if (exitCode == 0) {
            System.out.println("Maven test completed successfully.");
        } else {
            System.err.println("Maven test failed with exit code: " + exitCode);
        }

        // Read and print the log file content
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        // Close the log file
        logFile.createNewFile();
        process.destroy();
    }

    private static String getRepositoryName(String gitRepoUrl) {
        String[] parts = gitRepoUrl.split("/");
        return parts[parts.length - 1];
    }

    private static List<String> listjavaFiles(String directoryPath) throws IOException {
        try (Stream<Path> walk = Files.walk(Paths.get(directoryPath))) {
            return walk
                    .filter(path -> path.toString().endsWith(".class"))
                    .map(path -> {
                        String className = path.toString()
                                .replace(File.separator, ".") // Convert path separator to package separator
                                .replace(".java", ""); // Remove .class extension
                        return className.substring(className.lastIndexOf(".") + 1); // Extract class name
                    })
                    .collect(Collectors.toList());
        }
    }

    private static int[] countMethods(String directoryPath, List<File> classFiles) throws IOException {
        File projectDirectory = new File(directoryPath);

        List<Integer> methodCounts = new ArrayList<>();
        if (!projectDirectory.exists() || !projectDirectory.isDirectory()) {
            System.out.println("Invalid project directory path.");
        }

        findClasses(projectDirectory, classFiles);
        FileFilter classFileFilter = file -> file.isFile() && file.getName().endsWith(".class");

        int totalMethodCount = 0;
        int totalTestMethodsCount = 0;
        int totalClassesCount = 0;
        totalClassesCount = classFiles.size();
        // Iterate through each class file and extract package information
        for (File classFile : classFiles) {
            ClassParser classParser = new ClassParser(classFile.getAbsolutePath());
            JavaClass javaClass = classParser.parse();
            // Get package name
            String packageName = javaClass.getPackageName();

            String className = javaClass.getClassName();
            int methodsCount = Arrays.asList(javaClass.getMethods()).size();
            Method[] methods = javaClass.getMethods();
            for (Method method : methods) {
                String methodName = method.getName();
                if (methodName.startsWith("test") && methodName.length() > 4 &&
                        Character.isUpperCase(methodName.charAt(4))) {
                    totalTestMethodsCount++;
                }
            }
            totalMethodCount = totalMethodCount + methodsCount;

        }
        int[] result = new int[3];
        result[0] = totalMethodCount;
        result[1] = totalTestMethodsCount;
        result[2] = totalClassesCount;
        return result;
    }

    private static void findClasses(File directory, List<File> classNames) {
        File[] classFiles = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".class"));

        if (classFiles != null) {
            for (File classFile : classFiles) {
                classNames.add(classFile);
            }
        }

        File[] subdirectories = directory.listFiles(File::isDirectory);
        if (subdirectories != null) {
            for (File subdir : subdirectories) {
                findClasses(subdir, classNames);
            }
        }
    }

    private static String coverageInfo(String execPath, List<File> classFiles) throws Exception {
        File execFile = new File(execPath);
        ExecutionDataStore executionDataStore = new ExecutionDataStore();
        SessionInfoStore sessionInfoStore = new SessionInfoStore();

        try (FileInputStream execInputStream = new FileInputStream(execFile)) {
            ExecutionDataReader reader = new ExecutionDataReader(execInputStream);
            reader.setExecutionDataVisitor(executionDataStore);
            reader.setSessionInfoVisitor(sessionInfoStore);
            reader.read();
        }

        Map<String, List<String>> testCoverageMap = new HashMap<>();

        for (File classFile : classFiles) {
            CoverageBuilder coverageBuilder = new CoverageBuilder();
            Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
            byte[] classBytes = Files.readAllBytes(classFile.toPath());
            analyzer.analyzeClass(classBytes, classFile.getName());

            for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
                String className = classCoverage.getName();

                for (IMethodCoverage methodCoverage : classCoverage.getMethods()) {
                    String methodName = methodCoverage.getName();
                    boolean isMethodCovered = methodCoverage.getLineCounter().getCoveredCount() > 0;

                    if (isMethodCovered) {
                        String fullMethodName = className + "#" + methodName;
                        testCoverageMap.computeIfAbsent(fullMethodName, k -> new ArrayList<>()).add(fullMethodName);
                    }
                }
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();
        List<String> jsonStringList = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : testCoverageMap.entrySet()) {
            jsonStringList.add(objectMapper.writeValueAsString(entry.getValue()));
        }

        return jsonStringList.toString();
    }
}
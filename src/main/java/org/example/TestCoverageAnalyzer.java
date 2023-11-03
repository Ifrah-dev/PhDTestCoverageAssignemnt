package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestCoverageAnalyzer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: TestCoverageAnalyzer <git_repo_directory>");
            return;
        }

        String gitRepoUrl = args[0];
        String destinationDirectory = "test_coverage_projects";

        File gitRepoDirectory = new File(destinationDirectory, getRepositoryName(gitRepoUrl));

        try {
            if (!gitRepoDirectory.exists()) {
                // Clone the repository if it's public
                Git.cloneRepository().setURI(gitRepoUrl).setDirectory(gitRepoDirectory).call();
                System.out.println("Repository cloned from " + gitRepoUrl + " to " + gitRepoDirectory.getAbsolutePath());
            }

            Git git = Git.open(gitRepoDirectory);

            // Step 1: Get class names
            long step1StartTime = System.nanoTime();
            List<String> classNames = getAllClassNames(git);
            long step1EndTime = System.nanoTime();
            long step1Time = (step1EndTime - step1StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 1 (Get class names) Execution Time: " + step1Time + " ms");

            // Step 2: Get test method names
            long step2StartTime = System.nanoTime();
            List<String> testMethodNames = getAllTestMethods(git);
            long step2EndTime = System.nanoTime();
            long step2Time = (step2EndTime - step2StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 2 (Get test method names) Execution Time: " + step2Time + " ms");

            // Step 3: Count main method names
            long step3StartTime = System.nanoTime();
            List<String> mainMethodNames = getAllMainMethods(git);
            long step3EndTime = System.nanoTime();
            long step3Time = (step3EndTime - step3StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 3 (Count main method names) Execution Time: " + step3Time + " ms");

            // Step 4: Analyze test coverage
            long step4StartTime = System.nanoTime();
            Map<String, List<String>> testCoverage = analyzeTestCoverage(git, classNames, testMethodNames);
            long step4EndTime = System.nanoTime();
            long step4Time = (step4EndTime - step4StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 4 (Analyze test coverage) Execution Time: " + step4Time + " ms");

            JsonObject result = new JsonObject();
            result.addProperty("location", gitRepoDirectory.getAbsolutePath());

            JsonObject repoStats = new JsonObject();
            repoStats.addProperty("num_java_files", classNames.size());
            repoStats.addProperty("num_classes", classNames.size());
            repoStats.addProperty("num_methods", mainMethodNames.size());
            repoStats.addProperty("num_test_methods", testMethodNames.size());
            result.add("stat_of_repository", repoStats);

            result.add("test_coverage_against_methods", convertToJsonObject(testCoverage));

            // Step 5: Create JSON result
            long step5StartTime = System.nanoTime();
            String outputFileName = "test_coverage.json";
            Gson gson = new Gson();
            String jsonResult = gson.toJson(result);
            System.out.println(jsonResult);
            long step5EndTime = System.nanoTime();
            long step5Time = (step5EndTime - step5StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 5 (Create JSON result) Execution Time: " + step5Time + " ms");

            // Optionally, save the JSON result to a file
            long step6StartTime = System.nanoTime();
            Files.write(Paths.get(outputFileName), jsonResult.getBytes(StandardCharsets.UTF_8));
            long step6EndTime = System.nanoTime();
            long step6Time = (step6EndTime - step6StartTime) / 1_000_000; // in milliseconds
            System.out.println("Step 6 (save the JSON result to a file) Execution Time: " + step6Time + " ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getRepositoryName(String gitRepoUrl) {
        String[] parts = gitRepoUrl.split("/");
        return parts[parts.length - 1];
    }

    private static List<String> getAllClassNames(Git git) throws GitAPIException, IOException {
        List<String> classNames = new ArrayList<>();

        Repository repo = git.getRepository();
        File projectDir = repo.getWorkTree();

        // Define the test source directory
        File testSourceDir = new File(projectDir, "src/test/java");
        if (testSourceDir.exists()) {
            classNames.addAll(Files.walk(testSourceDir.toPath())
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        String filePath = path.toString();
                        filePath = filePath.replaceAll(projectDir.toString() + "/", "")
                                .replaceFirst("\\.java", "")
                                .replace("/", ".");
                        return filePath;
                    })
                    .collect(Collectors.toList()));
        }

        File sourceDir = new File(projectDir, "src/main/java");
        if (sourceDir.exists()) {
            classNames.addAll(Files.walk(sourceDir.toPath())
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        String filePath = path.toString();
                        filePath = filePath.replaceAll(projectDir.toString() + "/", "")
                                .replaceFirst("\\.java", "")
                                .replace("/", ".");
                        return filePath;
                    })
                    .collect(Collectors.toList()));
        }
        return classNames;
    }

    private static List<String> getAllTestMethods(Git git) throws IOException {
        List<String> testMethodNames = new ArrayList<>();

        Repository repo = git.getRepository();
        File projectDir = repo.getWorkTree();

        // Define the test source directory
        File testSourceDir = new File(projectDir, "src/test/java");
        if (!testSourceDir.exists()) {
            return testMethodNames; // No test directory found
        }

        List<Path> javaFiles = Files.walk(testSourceDir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

        String currentPackage = "";
        String currentClass = "";

        for (Path javaFile : javaFiles) {
            String[] parts = javaFile.toString().split("/");
            String fileName = parts[parts.length - 1];
            currentClass = fileName.replace(".java", "");

            List<String> lines = Files.readAllLines(javaFile);
            boolean insideTestMethod = false;

            for (String line : lines) {
                if (line.trim().startsWith("package ")) {
                    currentPackage = line.trim().substring(8).replace(";", "");
                } else if (line.contains("@Test")) {
                    insideTestMethod = true;
                }

                if (insideTestMethod && line.trim().startsWith("public void")) {
                    String methodName = line.trim().split(" ")[2].split("\\(")[0];
                    testMethodNames.add(currentPackage + "." + currentClass + "." + methodName);
                }

                if (insideTestMethod && line.trim().startsWith("}")) {
                    insideTestMethod = false;
                }
            }
        }

        return testMethodNames;
    }

    private static List<String> getAllMainMethods(Git git) throws IOException {
        List<String> mainMethodNames = new ArrayList<>();

        Repository repo = git.getRepository();
        File projectDir = repo.getWorkTree();

        // Define the source directory
        File sourceDir = new File(projectDir, "src/main/java");
        if (!sourceDir.exists()) {
            return mainMethodNames; // No source directory found
        }

        List<Path> javaFiles = Files.walk(sourceDir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

        String currentPackage = "";
        String currentClass = "";

        for (Path javaFile : javaFiles) {
            String[] parts = javaFile.toString().split("/");
            String fileName = parts[parts.length - 1];
            currentClass = fileName.replace(".java", "");

            List<String> lines = Files.readAllLines(javaFile);
            boolean insideMethod = false;

            for (String line : lines) {
                if (line.trim().startsWith("package ")) {
                    currentPackage = line.trim().substring(8).replace(";", "");
                }

                // Use the regular expression to identify method declarations
                Pattern pattern = Pattern.compile("^(\\s*\\w+\\s+)+\\w+\\s*\\([^\\)]*\\)\\s*\\{");
                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    insideMethod = true;
                    String methodName = line.trim().split(" ")[2].split("\\(")[0];
                    mainMethodNames.add(currentPackage + "." + currentClass + "." + methodName);
                }

                if (insideMethod && line.trim().startsWith("}")) {
                    insideMethod = false;
                }
            }
        }

        return mainMethodNames;
    }


    private static Map<String, List<String>> analyzeTestCoverage(Git git, List<String> classNames, List<String> testMethodNames) throws GitAPIException, IOException {
        Map<String, List<String>> testCoverage = new HashMap<>();

        // Directory to store JaCoCo execution data files
        File execDataDir = new File("exec-data");
        if (!execDataDir.exists()) {
            execDataDir.mkdirs();
        }

        Repository repo = git.getRepository();

        // Iterate through the test method names
        for (String testMethod : testMethodNames) {
            // Checkout a specific commit to analyze
            git.checkout().setName("master").call(); // Replace with the desired branch or commit

            // Instrument the code for coverage tracking
            ExecFileLoader execFileLoader = new ExecFileLoader();
            CoverageBuilder coverageBuilder = new CoverageBuilder();

            // Iterate through the class names
            for (String className : classNames) {
                String javaFilePath = className.replace(".", "/") + ".java";

                File javaFile = new File(repo.getWorkTree(), javaFilePath);

                // Create an analyzer for each class
                Analyzer analyzer = new Analyzer(execFileLoader.getExecutionDataStore(), coverageBuilder);

                // Analyze the class with JaCoCo
                try {
                    analyzer.analyzeAll(javaFile);
                } catch (FileNotFoundException e) {
                    System.out.println(e.getMessage());
                }

                // Collect coverage data
                for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
                    if (classCoverage.getName().equals(className)) {
                        List<String> coveredMethods = new ArrayList<>();
                        for (int i = classCoverage.getFirstLine(); i <= classCoverage.getLastLine(); i++) {
                            if (execFileLoader.getExecutionDataStore().get(classCoverage.getId()) != null
                                    && execFileLoader.getExecutionDataStore().get(classCoverage.getId()).getId() > 0) {
                                coveredMethods.add(classCoverage.getName() + "#" + i);
                            }
                        }
                        testCoverage.put(testMethod, coveredMethods);
                    }
                }
            }

            // Save execution data for this test
            execFileLoader.save(new File(execDataDir, testMethod + ".exec"), true);
        }

        return testCoverage;
    }

    private static JsonObject convertToJsonObject(Map<String, List<String>> coverageMap) {
        JsonObject jsonObject = new JsonObject();
        for (Map.Entry<String, List<String>> entry : coverageMap.entrySet()) {
            jsonObject.add(entry.getKey(), new Gson().toJsonTree(entry.getValue()));
        }
        return jsonObject;
    }
}
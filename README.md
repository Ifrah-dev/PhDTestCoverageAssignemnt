# Getting Started

This is a simple guide to help you run this project locally. Make sure you have the necessary prerequisites installed before you begin.

## Prerequisites

Before you can run this project, you need to have the following software installed on your system:

- **Java 11:** You should have Java Development Kit (JDK) 11 or a compatible version installed. You can download and install it from [Oracle's website](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html) or use OpenJDK.

- **Maven:** This project uses Maven as the build tool. Make sure you have Maven installed. You can download it from [here](https://maven.apache.org/download.cgi) or use a package manager relevant to your operating system.

## Running the Project

To run the project locally, follow these steps:

1. Clone this repository to your local machine using Git:
   ```sh
   git clone https://github.com/Ifrah-dev/PhDTestCoverageAssignemnt.git
   ```
2. Navigate to the project directory:
    ```sh
   cd PhDTestCoverageAssignemnt
    ```
3. Build the project using Maven:
    ```sh
   mvn clean install
    ```
3. Run the application:
    ```sh
   java -jar target/test-coverage-analyzer-1.0-SNAPSHOT.jar https://github.com/apache/commons-lang.git
    ```
   Instead of https://github.com/apache/commons-lang.git you can pass any public GitHub repository and check the code coverage.
   
Happy coding!
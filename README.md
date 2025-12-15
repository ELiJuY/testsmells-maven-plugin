# testsmells-maven-plugin

Maven plugin that integrates **tsDetect** (command-line version of jNose) to detect **test smells** in Java projects.

The plugin analyzes test classes during the **test phase**, reports detected test smells on the console, and stores a detailed CSV report when smells are found.

---

## Requirements

- Java 8 or higher  
- Maven 3.x  
- A Java project with tests under `src/test/java`

---

## Installation

### 1. Add the JitPack repository

Add the following repositories to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<pluginRepositories>
    <pluginRepository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </pluginRepository>
</pluginRepositories>
```

---

### 2. Add the plugin to your build

Add the plugin inside the `<build><plugins>` section of your `pom.xml`:

```xml
<plugin>
    <groupId>com.github.ELiJuY</groupId>
    <artifactId>testsmells-maven-plugin</artifactId>
    <!--you can check the latest version in "https://jitpack.io/#ELiJuY/testsmells-maven-plugin"-->
    <version>v2.0.1</version>
    <executions>
        <execution>
            <phase>test</phase>
            <goals>
                <goal>detect</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

> Replace the version with the appropriate released version if needed.

---

## Usage

### Run as part of the Maven lifecycle

Once configured, run:

```bash
mvn test
```

The plugin will be executed automatically during the **test phase**.

---

### Run the plugin manually

You can also execute the plugin directly without running tests:

```bash
mvn testsmells:detect
```

---

## Output

### Console output

For each test class, the plugin prints detected test smells and their number of occurrences.

Example:

```
Code smells for file SUTTest.java:
Assertion Roulette: 2
Conditional Test Logic: 1
Magic Number Test: 5
```

If no test smells are found:

```
Code smells for file SUTTest.java:
No code smells found.
```

---

### CSV report

If test smells are detected, a detailed CSV report is generated and stored in:

```
target/testsmells/
```

Example:

```
target/testsmells/Output_TestSmellDetection_<timestamp>.csv
```

The plugin also prints the full path to the generated report:

```
Full analysis in: target/testsmells/Output_TestSmellDetection_<timestamp>.csv
```

---

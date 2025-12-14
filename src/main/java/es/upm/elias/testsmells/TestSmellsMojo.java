package es.upm.elias.testsmells;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@Mojo(
        name = "detect",
        defaultPhase = LifecyclePhase.TEST
)
public class TestSmellsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {

        File baseDir = project.getBasedir();
        File testSourceDir = new File(baseDir, "src/test/java");
        File mainSourceDir = new File(baseDir, "src/main/java");
        File targetDir = new File(project.getBuild().getDirectory());

        getLog().info("Running TestSmells Maven Plugin");

        if (!testSourceDir.exists()) {
            getLog().warn("No test sources found. Skipping tsDetect.");
            return;
        }

        targetDir.mkdirs();
        File csvFile = new File(targetDir, "testsmells-input.csv");

        try {
            generateCsv(testSourceDir, mainSourceDir, csvFile);
            File csvResult = runTsDetect(csvFile);
            printTestSmells(csvResult);

            runTsDetect(csvFile);
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Error running tsDetect", e);
        }
    }

    private File extractTsDetectJar() throws IOException {

        File tempJar;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("tsDetect.jar")) {

            if (in == null) {
                throw new IOException("tsDetect.jar not found in plugin resources");
            }

            tempJar = File.createTempFile("tsDetect", ".jar");
            tempJar.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempJar)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }

        return tempJar;
    }


    /**
     * Genera el CSV requerido por tsDetect:
     * appName,pathToTestFile,pathToProductionFile
     */
    private void generateCsv(File testDir, File mainDir, File csvFile) throws IOException {

        List<File> testFiles = Files.walk(testDir.toPath())
                .filter(p -> p.toString().endsWith("Test.java"))
                .map(java.nio.file.Path::toFile)
                .collect(Collectors.toList());

        if (testFiles.isEmpty()) {
            getLog().warn("No test files found ending in *Test.java");
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            for (File testFile : testFiles) {

                String testName = testFile.getName().replace("Test.java", ".java");
                File productionFile = findProductionFile(mainDir, testName);

                writer.write(project.getArtifactId());
                writer.write(",");
                writer.write(testFile.getAbsolutePath());
                writer.write(",");

                if (productionFile != null) {
                    writer.write(productionFile.getAbsolutePath());
                }

                writer.newLine();
            }
        }

        getLog().info("Generated tsDetect input CSV at: " + csvFile.getAbsolutePath());
    }

    /**
     * Busca un fichero de producción con el mismo nombre que el test (sin Test)
     */
    private File findProductionFile(File mainDir, String fileName) throws IOException {

        List<File> matches = Files.walk(mainDir.toPath())
                .filter(p -> p.getFileName().toString().equals(fileName))
                .map(java.nio.file.Path::toFile)
                .collect(Collectors.toList());

        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Ejecuta tsDetect CLI y muestra la salida estándar
     */
    private File runTsDetect(File csvInput)
            throws IOException, InterruptedException {

        File tsDetectJar = extractTsDetectJar();
        File outputCsv = new File(
                tsDetectJar.getParentFile(),
                "TestSmellDetectionResults.csv"
        );

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-jar",
                tsDetectJar.getAbsolutePath(),
                csvInput.getAbsolutePath()
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // ignoramos salida cruda
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("tsDetect exited with code " + exitCode);
        }

        if (!outputCsv.exists()) {
            throw new IOException("tsDetect output CSV not found");
        }

        return outputCsv;
    }

    private void printTestSmells(File resultCsv) throws IOException {

        try (BufferedReader reader = new BufferedReader(new FileReader(resultCsv))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }

            String[] headers = headerLine.split(",");

            String line;
            while ((line = reader.readLine()) != null) {

                String[] values = line.split(",", -1);

                String testFilePath = values[1];
                String testFileName = new File(testFilePath).getName();

                StringBuilder smells = new StringBuilder();

                for (int i = 3; i < values.length; i++) {
                    if ("true".equalsIgnoreCase(values[i])) {
                        smells.append("  - ").append(headers[i]).append("\n");
                    }
                }

                if (smells.length() > 0) {
                    getLog().info("Test file: " + testFileName);
                    getLog().info(smells.toString());
                }
            }
        }
    }


}

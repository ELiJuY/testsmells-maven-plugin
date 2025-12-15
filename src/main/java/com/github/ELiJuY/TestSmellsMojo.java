package com.github.ELiJuY;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        File inputCsv = new File(targetDir, "testsmells-input.csv");

        try {
            generateCsv(testSourceDir, mainSourceDir, inputCsv);

            File outputCsv = runTsDetect(inputCsv);

            boolean hasSmells = printTestSmells(outputCsv);

            if (hasSmells) {
                File resultDir = new File(targetDir, "testsmells");
                resultDir.mkdirs();

                File storedCsv = new File(resultDir, outputCsv.getName());
                Files.copy(outputCsv.toPath(), storedCsv.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                getLog().info("Test smell report saved at: " + storedCsv.getAbsolutePath());
            } else {
                getLog().info("No test smells detected.");
            }

        } catch (Exception e) {
            throw new MojoExecutionException("Error running tsDetect", e);
        }
    }

    /* ------------------------------------------------------------ */

    private void generateCsv(File testDir, File mainDir, File csvFile) throws IOException {

        List<File> testFiles = Files.walk(testDir.toPath())
                .filter(p -> p.toString().endsWith(".java"))
                .map(java.nio.file.Path::toFile)
                .collect(Collectors.toList());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {

            for (File testFile : testFiles) {

                File productionFile = findProductionFile(mainDir, testFile.getName());

                writer.write(project.getArtifactId());
                writer.write(",");
                writer.write(testFile.getAbsolutePath().replace("\\", "/"));

                if (productionFile != null) {
                    writer.write(",");
                    writer.write(productionFile.getAbsolutePath().replace("\\", "/"));
                }

                writer.newLine();
            }
        }
    }

    private File findProductionFile(File mainDir, String fileName) throws IOException {

        List<File> matches = Files.walk(mainDir.toPath())
                .filter(p -> p.getFileName().toString().equals(fileName))
                .map(java.nio.file.Path::toFile)
                .collect(Collectors.toList());

        return matches.isEmpty() ? null : matches.get(0);
    }

    /* ------------------------------------------------------------ */

    private File extractTsDetectJar() throws IOException {

        try (InputStream in =
                     getClass().getClassLoader().getResourceAsStream("tsDetect.jar")) {

            if (in == null) {
                throw new IOException("tsDetect.jar not found in plugin resources");
            }

            File tempJar = File.createTempFile("tsDetect", ".jar");
            tempJar.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempJar)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            return tempJar;
        }
    }

    private File runTsDetect(File inputCsv)
            throws IOException, InterruptedException {

        File tsDetectJar = extractTsDetectJar();

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-jar",
                tsDetectJar.getAbsolutePath(),
                inputCsv.getAbsolutePath()
        );

        File workDir = inputCsv.getParentFile();
        pb.directory(workDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader r =
                     new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (r.readLine() != null) {

            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("tsDetect exited with code " + exitCode);
        }

        File[] outputs = workDir.listFiles((dir, name) ->
                name.startsWith("Output_TestSmellDetection") && name.endsWith(".csv")
        );

        if (outputs == null || outputs.length == 0) {
            throw new IOException("tsDetect output CSV not found");
        }

        // el output de tsDetect más reciente
        File outputCsv = outputs[0];
        for (File f : outputs) {
            if (f.lastModified() > outputCsv.lastModified()) {
                outputCsv = f;
            }
        }

        return outputCsv;
    }


    /* ------------------------------------------------------------ */

    /**
     * @return true if any smell is detected
     */
    private boolean printTestSmells(File resultCsv) throws IOException {

        boolean smellsFoundGlobal = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(resultCsv))) {

            // Leer cabecera
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return false;
            }

            String[] headers = headerLine.split(",");

            String line;
            while ((line = reader.readLine()) != null) {

                String[] values = line.split(",", -1);

                String testFilePath = values[2];
                String testFileName = new File(testFilePath).getName();

                getLog().info("Code smells for file " + testFileName + ":");

                boolean smellsFoundInTest = false;

                // Los smells empiezan en la columna 7
                for (int i = 7; i < headers.length && i < values.length; i++) {

                    String smellName = headers[i].trim();
                    String rawValue = values[i].trim();

                    if (rawValue.isEmpty()) {
                        continue;
                    }

                    int count;
                    try {
                        count = Integer.parseInt(rawValue);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    if (count > 0) {
                        smellsFoundInTest = true;
                        smellsFoundGlobal = true;
                        getLog().info(smellName + ": " + count);
                    }
                }

                if (!smellsFoundInTest) {
                    getLog().info("No code smells found.");
                }

                getLog().info(""); // línea en blanco entre tests
            }
        }
        return smellsFoundGlobal;
    }

}


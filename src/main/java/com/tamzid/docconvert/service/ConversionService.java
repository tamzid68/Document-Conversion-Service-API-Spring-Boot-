package com.tamzid.docconvert.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ConversionService implements IConversionService {

    @Value("${docconvert.upload-dir:uploads}")
    private String uploadDir;
    @Value("${docconvert.converted-dir:converted}")
    private String convertedDir;
    @Value("${docconvert.soffice-path:soffice}")
    private String sofficePath;

    @Override
    public String handleConversion(MultipartFile file, String from, String to) throws IOException {
        String uuid = UUID.randomUUID().toString();
        String inputExt = getExtension(file.getOriginalFilename());
        if (inputExt == null) {
            throw new IllegalArgumentException("Uploaded file must have an extension");
        }
        String outputExt = to.toLowerCase();

        // Validate extensions
        if (!(inputExt.equalsIgnoreCase("pdf") || inputExt.equalsIgnoreCase("docx"))) {
            throw new IllegalArgumentException("Unsupported input file type: " + inputExt);
        }
        if (!(outputExt.equals("pdf") || outputExt.equals("docx"))) {
            throw new IllegalArgumentException("Unsupported output file type: " + outputExt);
        }

        // Ensure folders exist
        Files.createDirectories(Paths.get(uploadDir));
        Files.createDirectories(Paths.get(convertedDir));

        //save original file
        Path inputPath = Paths.get(uploadDir, uuid + "." + inputExt);
        Files.copy(file.getInputStream(), inputPath, StandardCopyOption.REPLACE_EXISTING);

        // Convert
        Path outputPath = Paths.get(convertedDir, uuid + "." + outputExt);
        try {
            runConversion(inputPath.toFile(), outputPath.toFile(), from, to);
        } finally {
            // Clean up uploaded file
            Files.deleteIfExists(inputPath);
        }

        // Optionally: schedule deletion of output file after a period, or provide a cleanup endpoint

        // Return download URL
        return "/api/download/" + outputPath.getFileName();
    }

    private void runConversion(File input, File output, String from, String to) {
        // Ensure output directory exists
        File outDir = new File(output.getParent());
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        if (from.equalsIgnoreCase("pdf") && to.equalsIgnoreCase("docx")) {
            // Two-step conversion: PDF -> ODT -> DOCX
            try {
                // Step 1: PDF to ODT
                ProcessBuilder pb1 = new ProcessBuilder(
                        sofficePath,
                        "--headless",
                        "--convert-to", "odt",
                        "--outdir", output.getParent(),
                        input.getAbsolutePath()
                );
                pb1.redirectErrorStream(true);
                System.out.println("[LibreOffice CMD 1] " + pb1.command());
                Process process1 = pb1.start();
                StringBuilder processOutput1 = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process1.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processOutput1.append(line).append(System.lineSeparator());
                    }
                }
                int exitCode1 = process1.waitFor();
                System.out.println("[LibreOffice OUTPUT 1] " + processOutput1);
                if (exitCode1 != 0) {
                    throw new RuntimeException("LibreOffice PDF->ODT conversion failed with exit code: " + exitCode1 + " Output:" + processOutput1);
                }
                // Find the ODT file (same name as input, but .odt)
                String odtFileName = input.getName().replaceAll("(?i)\\.pdf$", ".odt");
                File odtFile = new File(output.getParent(), odtFileName);
                if (!odtFile.exists()) {
                    throw new RuntimeException("ODT file not found after PDF->ODT conversion: " + odtFile.getAbsolutePath());
                }
                // Step 2: ODT to DOCX
                ProcessBuilder pb2 = new ProcessBuilder(
                        sofficePath,
                        "--headless",
                        "--convert-to", "docx",
                        "--outdir", output.getParent(),
                        odtFile.getAbsolutePath()
                );
                pb2.redirectErrorStream(true);
                System.out.println("[LibreOffice CMD 2] " + pb2.command());
                Process process2 = pb2.start();
                StringBuilder processOutput2 = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process2.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processOutput2.append(line).append(System.lineSeparator());
                    }
                }
                int exitCode2 = process2.waitFor();
                System.out.println("[LibreOffice OUTPUT 2] " + processOutput2);
                if (exitCode2 != 0) {
                    throw new RuntimeException("LibreOffice ODT->DOCX conversion failed with exit code: " + exitCode2 + " Output:" + processOutput2);
                }
                // Optionally, delete the intermediate ODT file
                if (!odtFile.delete()) {
                    System.out.println("Warning: Could not delete intermediate ODT file: " + odtFile.getAbsolutePath());
                }
            } catch (Exception e) {
                throw new RuntimeException("Conversion failed: " + e.getMessage(), e);
            }
        } else if (from.equalsIgnoreCase("docx") && to.equalsIgnoreCase("pdf")) {
            // Use LibreOffice to convert DOCX to PDF
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        sofficePath,
                        "--headless",
                        "--convert-to", "pdf",
                        "--outdir", output.getParent(),
                        input.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                System.out.println("[LibreOffice CMD] " + pb.command());
                Process process = pb.start();
                StringBuilder processOutput = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        processOutput.append(line).append(System.lineSeparator());
                    }
                }
                int exitCode = process.waitFor();
                System.out.println("[LibreOffice OUTPUT] " + processOutput);
                if (exitCode != 0) {
                    throw new RuntimeException("LibreOffice conversion failed with exit code: " + exitCode + " Output: " + processOutput);
                }
                File converted = new File(output.getParent(), input.getName().replaceAll("\\.docx$", ".pdf"));
                if (!converted.exists()) {
                    throw new RuntimeException("Converted PDF file not found: " + converted.getAbsolutePath() + " LibreOffice Output: " + processOutput);
                }
                if (!converted.renameTo(output)) {
                    throw new RuntimeException("Failed to move converted PDF to output location");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert DOCX to PDF using LibreOffice: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported conversion from " + from + " to " + to);
        }
    }

    private String getExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) return null;
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
    }

}

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
        runConversion(inputPath.toFile(), outputPath.toFile(), from, to);

        // Clean up uploaded file
        Files.deleteIfExists(inputPath);

        // Optionally: schedule deletion of output file after a period, or provide a cleanup endpoint

        // Return download URL
        return "/download/" + outputPath.getFileName();
    }

    private void runConversion(File input, File output, String from, String to) {
        // Ensure output directory exists
        File outDir = new File(output.getParent());
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        if (from.equalsIgnoreCase("pdf") && to.equalsIgnoreCase("docx")) {
            // Use LibreOffice to convert PDF to DOCX
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        sofficePath,
                        "--headless",
                        "--convert-to", "docx",
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
                    throw new RuntimeException("LibreOffice conversion failed with exit code: " + exitCode + "\nOutput:\n" + processOutput);
                }
                // Try both .docx and .doc extensions
                File convertedDocx = new File(output.getParent(), input.getName().replaceAll("\\.pdf$", ".docx"));
                File convertedDoc = new File(output.getParent(), input.getName().replaceAll("\\.pdf$", ".doc"));
                File converted = convertedDocx.exists() ? convertedDocx : (convertedDoc.exists() ? convertedDoc : null);
                if (converted == null || !converted.exists()) {
                    throw new RuntimeException("Converted DOCX/DOC file not found. Checked: " + convertedDocx.getAbsolutePath() + " and " + convertedDoc.getAbsolutePath() + "\nLibreOffice Output:\n" + processOutput);
                }
                if (!converted.renameTo(output)) {
                    throw new RuntimeException("Failed to move converted DOCX/DOC to output location");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert PDF to DOCX using LibreOffice: " + e.getMessage(), e);
            }
        }
        else if (from.equalsIgnoreCase("docx") && to.equalsIgnoreCase("pdf")) {
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
                    throw new RuntimeException("LibreOffice conversion failed with exit code: " + exitCode + "\nOutput:\n" + processOutput);
                }
                File converted = new File(output.getParent(), input.getName().replaceAll("\\.docx$", ".pdf"));
                if (!converted.exists()) {
                    throw new RuntimeException("Converted PDF file not found: " + converted.getAbsolutePath() + "\nLibreOffice Output:\n" + processOutput);
                }
                if (!converted.renameTo(output)) {
                    throw new RuntimeException("Failed to move converted PDF to output location");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert DOCX to PDF using LibreOffice: " + e.getMessage(), e);
            }
        }
        else {
            throw new IllegalArgumentException("Unsupported conversion from " + from + " to " + to);
        }
    }

    private String getExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) return null;
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
    }

}

package com.tamzid.docconvert.service;

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

    @Override
    public String handleConversion(MultipartFile file, String from, String to) throws IOException {
        String uuid = UUID.randomUUID().toString();
        String inputExt = getExtension(file.getOriginalFilename());
        String outputExt = to.toLowerCase();

        // Ensure folders exist
        Files.createDirectories(Paths.get("uploads"));
        Files.createDirectories(Paths.get("converted"));

        //save original file
        Path inputPath = Paths.get("uploads", uuid + "." + inputExt);
        Files.copy(file.getInputStream(), inputPath, StandardCopyOption.REPLACE_EXISTING);

        // Convert
        Path outputPath = Paths.get("converted", uuid + "." + outputExt);
        runConversion(inputPath.toFile(), outputPath.toFile(), from, to);

        // Return download URL
        return "/download/" + outputPath.getFileName();

    }

    private void runConversion(File input, File output, String from, String to) {
        if (from.equalsIgnoreCase("pdf") && to.equalsIgnoreCase("docx")) {
            // Use LibreOffice to convert PDF to DOCX
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "soffice",
                        "--headless",
                        "--convert-to", "docx",
                        "--outdir", output.getParent(),
                        input.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("LibreOffice conversion failed with exit code: " + exitCode);
                }
                // Move the converted file to the expected output path if needed
                File converted = new File(output.getParent(), input.getName().replaceAll("\\.pdf$", ".docx"));
                if (!converted.exists()) {
                    throw new RuntimeException("Converted DOCX file not found: " + converted.getAbsolutePath());
                }
                if (!converted.renameTo(output)) {
                    throw new RuntimeException("Failed to move converted DOCX to output location");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert PDF to DOCX using LibreOffice", e);
            }
        }

        else if (from.equalsIgnoreCase("docx") && to.equalsIgnoreCase("pdf")) {
            // Use LibreOffice to convert DOCX to PDF
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "soffice",
                        "--headless",
                        "--convert-to", "pdf",
                        "--outdir", output.getParent(),
                        input.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("LibreOffice conversion failed with exit code: " + exitCode);
                }
                // Move the converted file to the expected output path if needed
                File converted = new File(output.getParent(), input.getName().replaceAll("\\.docx$", ".pdf"));
                if (!converted.exists()) {
                    throw new RuntimeException("Converted PDF file not found: " + converted.getAbsolutePath());
                }
                if (!converted.renameTo(output)) {
                    throw new RuntimeException("Failed to move converted PDF to output location");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert DOCX to PDF using LibreOffice", e);
            }
        }
        else {
            throw new IllegalArgumentException("Unsupported conversion from " + from + " to " + to);
        }
    }


    private String getExtension(String originalFilename) {
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
    }


}

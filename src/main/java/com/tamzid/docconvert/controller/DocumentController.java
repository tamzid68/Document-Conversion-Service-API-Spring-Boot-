package com.tamzid.docconvert.controller;
import com.tamzid.docconvert.service.IConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

//@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class DocumentController {

    private final IConversionService conversionService;

    @Autowired
    public DocumentController(IConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @GetMapping("/")
    public ResponseEntity<String> index() {
        return ResponseEntity.ok("Welcome to Document Conversion API");
    }

    @PostMapping("/convert")
    public ResponseEntity<?> convertFile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "fromFormat", required = false) String from,
            @RequestParam(value = "toFormat", required = false) String to
    ) {
        // Validate input
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must not be empty"));
        }
        if (from == null || from.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fromFormat must not be empty"));
        }
        if (to == null || to.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "toFormat must not be empty"));
        }
        try {
            String downloadUrl = conversionService.handleConversion(file, from, to);
            return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Conversion failed: " + e.getMessage()));
        }
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) throws MalformedURLException, FileNotFoundException {
        Path file = Paths.get("converted").resolve(filename).normalize();
        Resource resource = new UrlResource(file.toUri());

        if (!resource.exists()) {
            throw new FileNotFoundException("File not found");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}

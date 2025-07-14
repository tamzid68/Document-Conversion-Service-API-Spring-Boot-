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

    @PostMapping("/")
    public ResponseEntity<String> index() {
        return ResponseEntity.ok("Welcome to Document Conversion API");
    }

    @PostMapping("/convert")
    public ResponseEntity<?> covertFile(
            @RequestParam("file")MultipartFile file,
            @RequestParam("fromFormat") String from,
            @RequestParam("toFormat") String to
            ) throws IOException{
        String downloadUrl = conversionService.handleConversion(file, from, to);

        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
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

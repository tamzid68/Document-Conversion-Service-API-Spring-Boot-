package com.tamzid.docconvert.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface IConversionService {
    String handleConversion(MultipartFile file, String from, String to) throws IOException;
}


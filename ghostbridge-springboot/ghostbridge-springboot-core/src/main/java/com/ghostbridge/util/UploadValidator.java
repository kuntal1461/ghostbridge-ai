package com.ghostbridge.util;

import com.ghostbridge.config.GhostBridgeProperties;
import com.ghostbridge.exception.InvalidInputException;

import java.util.Locale;

/** Validates upload metadata (name, size, content type) before the content is read. */
public class UploadValidator {
    private static final int MAX_FILENAME_LENGTH = 255;

    private final GhostBridgeProperties.Upload limits;

    public UploadValidator(GhostBridgeProperties.Upload limits) {
        this.limits = limits;
    }

    public void validate(String filename, String contentType, long sizeBytes) {
        validateFilename(filename);
        validateSize(sizeBytes);
        validateContentType(contentType);
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidInputException("Invalid file name");
        }
        boolean unsafe = filename.contains("..") || filename.contains("/") || filename.contains("\\")
                || filename.chars().anyMatch(Character::isISOControl);
        if (unsafe) {
            throw new InvalidInputException("Invalid file name");
        }
    }

    private void validateSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > limits.maxFileSize().toBytes()) {
            throw new InvalidInputException("File size must be between 1 byte and " + limits.maxFileSize());
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null) {
            throw new InvalidInputException("Content type is required");
        }
        String baseType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        boolean allowed = limits.allowedContentTypes().stream()
                .anyMatch(type -> type.equalsIgnoreCase(baseType));
        if (!allowed) {
            throw new InvalidInputException("Content type not allowed");
        }
    }
}

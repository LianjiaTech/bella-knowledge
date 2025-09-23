package com.ke.bella.files.db;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.ke.bella.files.enums.FileType;

/**
 * File ID generator with purpose-based suffix support
 * Also provides ID parsing utilities
 */
public class FileIdGenerator {

    private static final String DATE_PATTERN = "yyMMddHHmmss";

    private final IDGenerator idGenerator;

    /**
     * Create FileIdGenerator with default file ID strategy
     */
    public FileIdGenerator() {
        this.idGenerator = new IDGenerator("file-", IDGenerator.SPACE_CODE_STRATEGY);
    }

    public String generateDirId() {
        return generateWithType(FileType.DIRECTORY);
    }

    /**
     * Generate file ID with FileType directly
     */
    public String generateWithType(FileType fileType) {
        String baseId = idGenerator.generate();
        return baseId + fileType.getSuffix();
    }

    // ID Parsing Utilities

    /**
     * Extract time from file ID
     * Format: file-yyMMddHHmmssxxxxxx-hash[-suffix]
     */
    public static LocalDateTime extractTimeFromFileId(String fileId) {
        // 格式: file-yyMMddHHmmssxxxxxx-hash 或
        // file-yyMMddHHmmssxxxxxx-hash-suffix
        int index = fileId.indexOf('-') + 1;
        if(index <= 0 || index + 12 > fileId.length()) {
            throw new IllegalArgumentException("Invalid file ID format: " + fileId);
        }
        String timeStr = fileId.substring(index, index + 12);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        return LocalDateTime.parse(timeStr, formatter);
    }

    /**
     * Extract spaceCodeHash from file ID
     */
    public static String extractSpaceCodeHash(String fileId) {
        String[] parts = fileId.split("-");
        // SPACE_CODE_STRATEGY格式: file-yyMMddHHmmssxxxxxx-spaceCodeHash[-suffix]
        if(parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException("Invalid file ID format: " + fileId);
        }
        return parts[2];
    }
}

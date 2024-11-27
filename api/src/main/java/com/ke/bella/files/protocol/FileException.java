package com.ke.bella.files.protocol;

import static com.ke.bella.files.configuration.Configs.MAX_SIZE_IN_MB;

public class FileException {

    public static class AuthorizationException extends IllegalArgumentException {
        public AuthorizationException(String auth) {
            super("Authorization failed: " + auth);
        }
    }

    public static class FileNotFoundException extends IllegalArgumentException {
        public FileNotFoundException(String fileId) {
            super("File not found: " + fileId);
        }
    }

    public static class ProgressNotFoundException extends IllegalArgumentException {
        public ProgressNotFoundException(String fileId, String progressName) {
            super(String.format("Progress %s not found for file %s.", progressName, fileId));
        }
    }

    public static class FileTooLargeException extends IllegalStateException {
        public FileTooLargeException(Long fileSize, String fileName) {
            super(String.format("File size exceeds the maximum limit of %dMB. File size: %.1fMB. Filename: %s.", MAX_SIZE_IN_MB,
                    fileSize / (1024.0 * 1024.0), fileName));
        }
    }
}

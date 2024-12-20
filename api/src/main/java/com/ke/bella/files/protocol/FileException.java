package com.ke.bella.files.protocol;

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
}

package com.ke.bella.files.utils;

public class FileExtensionDetectUtil {

    public static String detectExtension(String filename) {
        if(filename == null || filename.lastIndexOf(".") == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}

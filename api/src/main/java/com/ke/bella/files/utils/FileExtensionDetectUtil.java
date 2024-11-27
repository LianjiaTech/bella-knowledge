package com.ke.bella.files.utils;

public class FileExtensionDetectUtil {

    public static String detectExtension(String fileName) {
        if(fileName == null || fileName.lastIndexOf(".") == -1) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}

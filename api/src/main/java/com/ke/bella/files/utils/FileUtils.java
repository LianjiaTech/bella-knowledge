package com.ke.bella.files.utils;

import okhttp3.MediaType;

public class FileUtils {

    public static String detectExtension(String filename) {
        if(filename == null || filename.lastIndexOf(".") == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    public static String getFileType(MediaType mimeType) {
        String t = extraPureMediaType(mimeType).toLowerCase();
        if(t.startsWith("image")) {
            return "image";
        } else if(t.startsWith("audio")) {
            return "audio";
        } else if(t.startsWith("video")) {
            return "video";
        } else if(t.startsWith("text")) {
            return "text";
        } else {
            return "binary";
        }
    }

    public static String extraPureMediaType(MediaType source) {
        return source.type() + "/" + source.subtype();
    }
}

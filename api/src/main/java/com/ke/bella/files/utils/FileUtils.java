package com.ke.bella.files.utils;

import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;

public class FileUtils {
    private static final Map<String, String> MIME_TO_EXTENSION = new HashMap<>();

    static {
        MIME_TO_EXTENSION.put("text/html", "html");
        MIME_TO_EXTENSION.put("text/css", "css");
        MIME_TO_EXTENSION.put("text/javascript", "js");
        MIME_TO_EXTENSION.put("image/jpg", "jpg");
        MIME_TO_EXTENSION.put("image/jpeg", "jpg");
        MIME_TO_EXTENSION.put("image/png", "png");
        MIME_TO_EXTENSION.put("image/gif", "gif");
        MIME_TO_EXTENSION.put("image/webp", "webp");
        MIME_TO_EXTENSION.put("image/svg+xml", "svg");
        MIME_TO_EXTENSION.put("application/pdf", "pdf");
        MIME_TO_EXTENSION.put("application/zip", "zip");
        MIME_TO_EXTENSION.put("audio/mpeg", "mp3");
        MIME_TO_EXTENSION.put("audio/mp3", "mp3");
        MIME_TO_EXTENSION.put("audio/mp4", "m4a");
        MIME_TO_EXTENSION.put("audio/wav", "wav");
        MIME_TO_EXTENSION.put("audio/wave", "wav");
        MIME_TO_EXTENSION.put("audio/webm", "webm");
        MIME_TO_EXTENSION.put("audio/amr", "amr");
        MIME_TO_EXTENSION.put("video/mp4", "mp4");
        MIME_TO_EXTENSION.put("video/quicktime", "mov");
        MIME_TO_EXTENSION.put("video/mpeg", "mpeg");
        MIME_TO_EXTENSION.put("application/msword", "doc");
        MIME_TO_EXTENSION.put("application/json", "json");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        MIME_TO_EXTENSION.put("application/vnd.ms-excel", "xls");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        MIME_TO_EXTENSION.put("application/vnd.ms-powerpoint", "ppt");
        MIME_TO_EXTENSION.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
        MIME_TO_EXTENSION.put("text/plain", "txt");
        MIME_TO_EXTENSION.put("text/markdown", "md");
        MIME_TO_EXTENSION.put("text/csv", "csv");
        MIME_TO_EXTENSION.put("application/xml", "xml");
        MIME_TO_EXTENSION.put("application/epub+zip", "epub");
        MIME_TO_EXTENSION.put("message/rfc822", "eml");
        MIME_TO_EXTENSION.put("application/vnd.ms-outlook", "msg");
    }

    public static String getType(String mediaType) {
        MediaType parsedMediaType = MediaType.parse(mediaType);
        return getType(parsedMediaType);
    }

    public static String getType(MediaType mediaType) {
        String mediaTypeValue = extraPureMediaType(mediaType).toLowerCase();
        if(mediaTypeValue.startsWith("image")) {
            return "image";
        } else if(mediaTypeValue.startsWith("audio")) {
            return "audio";
        } else if(mediaTypeValue.startsWith("video")) {
            return "video";
        } else if(mediaTypeValue.startsWith("text")) {
            return "text";
        } else {
            return MIME_TO_EXTENSION.getOrDefault(mediaTypeValue, "binary");
        }
    }

    public static String extraPureMediaType(MediaType mediaType) {
        return mediaType.type() + "/" + mediaType.subtype();
    }

    public static String getFileExtension(String filename) {
        if(filename == null || filename.lastIndexOf(".") == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}

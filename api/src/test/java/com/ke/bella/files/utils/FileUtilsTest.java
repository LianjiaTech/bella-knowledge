package com.ke.bella.files.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import okhttp3.MediaType;

public class FileUtilsTest {
    @Test
    public void preservesFilenameExtensionSemantics() {
        assertNull(FileUtils.getFileExtension(null));
        assertNull(FileUtils.getFileExtension("README"));
        assertEquals("gz", FileUtils.getFileExtension("archive.tar.gz"));
        assertEquals("PDF", FileUtils.getFileExtension("file.PDF"));
        assertEquals("", FileUtils.getFileExtension("file."));
    }

    @Test
    public void preservesMediaTypeAndDocumentTypeClassification() {
        assertEquals("text/plain", FileUtils.extraPureMediaType(MediaType.parse("text/plain; charset=utf-8")));
        assertEquals("text", FileUtils.getType("text/plain; charset=utf-8"));
        assertEquals("image", FileUtils.getType("image/png"));
        assertEquals("audio", FileUtils.getType("audio/mpeg"));
        assertEquals("video", FileUtils.getType("video/mp4"));
        assertEquals("pdf", FileUtils.getType("application/pdf"));
        assertEquals("docx", FileUtils.getType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("binary", FileUtils.getType("application/octet-stream"));
    }
}

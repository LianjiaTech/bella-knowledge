package com.ke.bella.files.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.service.storage.StorageService;

public class FileServiceImportSourceTest {

    private FileService fileService;
    private StorageService storageService;

    @Before
    public void setUp() {
        BucketConfig bucketConfig = new BucketConfig();
        bucketConfig.setPublicBucket("public-bucket");
        bucketConfig.setPrivateBucket("private-bucket");
        bucketConfig.setImportSources("biz-bucket, other-bucket,,private-bucket");
        storageService = Mockito.mock(StorageService.class);
        fileService = new FileService();
        ReflectionTestUtils.setField(fileService, "bucketConfig", bucketConfig);
        ReflectionTestUtils.setField(fileService, "storageService", storageService);
    }

    @Test
    public void allowsConfiguredExternalBuckets() {
        assertTrue(fileService.isAllowedImportSource("biz-bucket"));
        assertTrue(fileService.isAllowedImportSource("other-bucket"));
    }

    @Test
    public void rejectsUnconfiguredBuckets() {
        assertFalse(fileService.isAllowedImportSource("unknown-bucket"));
        assertFalse(fileService.isAllowedImportSource(""));
        assertFalse(fileService.isAllowedImportSource(null));
    }

    @Test
    public void rejectsInternalBucketsEvenIfConfigured() {
        assertFalse(fileService.isAllowedImportSource("private-bucket"));
        assertFalse(fileService.isAllowedImportSource("public-bucket"));
    }

    @Test
    public void visionUrlIsPublicOnlyForPublicBucket() {
        when(storageService.getPublicUrl(anyString(), anyString())).thenReturn("public-url");
        when(storageService.getPresignedUrl(anyString(), anyString(), anyLong())).thenReturn("presigned-url");

        assertEquals("public-url", fileService.getUrl("public-bucket", "vision/a.png", "vision", 60L));
        assertEquals("presigned-url", fileService.getUrl("biz-bucket", "legacy/a.png", "vision", 60L));
        assertEquals("presigned-url", fileService.getUrl("private-bucket", "assistants/a.txt", "assistants", 60L));
    }
}

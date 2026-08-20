package com.ke.bella.files.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.configuration.BucketConfig;

public class FileServiceImportSourceTest {

    private FileService fileService;

    @Before
    public void setUp() {
        BucketConfig bucketConfig = new BucketConfig();
        bucketConfig.setPublicBucket("public-bucket");
        bucketConfig.setPrivateBucket("private-bucket");
        bucketConfig.setImportSources("biz-bucket, other-bucket,,private-bucket");
        fileService = new FileService();
        ReflectionTestUtils.setField(fileService, "bucketConfig", bucketConfig);
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
}

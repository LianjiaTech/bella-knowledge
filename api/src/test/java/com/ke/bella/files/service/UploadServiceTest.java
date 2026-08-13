package com.ke.bella.files.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.db.tables.pojos.FileUploadDB;
import com.ke.bella.files.protocol.UploadException;
import com.ke.bella.files.protocol.UploadOps.CompleteUploadOp;
import com.ke.bella.files.service.storage.StoragePart;

public class UploadServiceTest {
    private UploadService uploadService;
    private FileUploadDB session;

    @Before
    public void setUp() {
        uploadService = new UploadService();
        session = FileUploadDB.builder().declaredBytes(11L).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void validatePartsAcceptsContinuousPartsWithMatchingBytesAndIds() {
        List<StoragePart> parts = Arrays.asList(new StoragePart(2, 6L, "\"etag-2\""), new StoragePart(1, 5L, "etag-1"));
        CompleteUploadOp op = new CompleteUploadOp();
        op.setPartIds(Arrays.asList("part_1_etag-1", "part_2_etag-2"));

        List<StoragePart> result = (List<StoragePart>) ReflectionTestUtils.invokeMethod(uploadService, "validateParts", session, parts, op);

        assertEquals(1, result.get(0).getPartNumber());
        assertEquals(2, result.get(1).getPartNumber());
    }

    @Test
    public void validatePartsRejectsGap() {
        List<StoragePart> parts = Arrays.asList(new StoragePart(1, 5L, "etag-1"), new StoragePart(3, 6L, "etag-3"));

        UploadException error = assertThrows(UploadException.class,
                () -> ReflectionTestUtils.invokeMethod(uploadService, "validateParts", session, parts, null));

        assertEquals("invalid_parts", error.getErrorCode());
    }

    @Test
    public void validatePartsRejectsDeclaredByteMismatch() {
        List<StoragePart> parts = Collections.singletonList(new StoragePart(1, 10L, "etag-1"));

        UploadException error = assertThrows(UploadException.class,
                () -> ReflectionTestUtils.invokeMethod(uploadService, "validateParts", session, parts, null));

        assertEquals("invalid_parts", error.getErrorCode());
    }

    @Test
    public void validatePartsRejectsEtagMismatch() {
        List<StoragePart> parts = Arrays.asList(new StoragePart(1, 5L, "etag-1"), new StoragePart(2, 6L, "etag-2"));
        CompleteUploadOp op = new CompleteUploadOp();
        op.setPartIds(Arrays.asList("part_1_etag-1", "part_2_wrong"));

        UploadException error = assertThrows(UploadException.class,
                () -> ReflectionTestUtils.invokeMethod(uploadService, "validateParts", session, parts, op));

        assertEquals("invalid_parts", error.getErrorCode());
    }
}

package com.ke.bella.files.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.db.repo.FileUploadRepo;
import com.ke.bella.files.db.tables.pojos.FileUploadDB;
import com.ke.bella.files.protocol.Upload;
import com.ke.bella.files.protocol.UploadException;
import com.ke.bella.files.protocol.UploadOps.CompleteUploadOp;
import com.ke.bella.files.service.storage.StoragePart;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class UploadServiceTest {
    private static final String SPACE_CODE = "sp-a";

    private UploadService uploadService;
    private FileUploadRepo fileUploadRepo;
    private FileUploadDB session;

    @Before
    public void setUp() {
        uploadService = new UploadService();
        fileUploadRepo = mock(FileUploadRepo.class);
        ReflectionTestUtils.setField(uploadService, "fileUploadRepo", fileUploadRepo);
        session = new FileUploadDB();
        session.setDeclaredBytes(11L);
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(SPACE_CODE).build());
    }

    @After
    public void tearDown() {
        BellaContext.clearAll();
    }

    private FileUploadDB sessionWith(String status, LocalDateTime expiresAt) {
        FileUploadDB row = new FileUploadDB();
        row.setUploadId("upload-1");
        row.setSpaceCode(SPACE_CODE);
        row.setFilename("a.txt");
        row.setPurpose("temp");
        row.setDeclaredBytes(11L);
        row.setStatus(status);
        row.setCtime(LocalDateTime.now());
        row.setExpiresAt(expiresAt);
        return row;
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

    @Test
    public void loadSessionQueriesByCallerSpaceAndRejectsMissingSession() {
        when(fileUploadRepo.queryByUploadId("upload-1", SPACE_CODE)).thenReturn(null);

        UploadException error = assertThrows(UploadException.class, () -> uploadService.cancel("upload-1"));

        assertEquals("upload_not_found", error.getErrorCode());
        assertEquals(404, error.getHttpStatus());
    }

    @Test
    public void loadSessionRejectsExpiredPendingSession() {
        when(fileUploadRepo.queryByUploadId("upload-1", SPACE_CODE))
                .thenReturn(sessionWith("PENDING", LocalDateTime.now().minusMinutes(1)));

        UploadException error = assertThrows(UploadException.class, () -> uploadService.cancel("upload-1"));

        assertEquals("upload_expired", error.getErrorCode());
    }

    @Test
    public void loadSessionAllowsExpiredCompletingSessionForRecovery() {
        when(fileUploadRepo.queryByUploadId("upload-1", SPACE_CODE))
                .thenReturn(sessionWith("COMPLETING", LocalDateTime.now().minusMinutes(1)));

        // 过期不再拦截 COMPLETING：cancel 走到 CAS 才失败，报状态冲突而非 upload_expired
        UploadException error = assertThrows(UploadException.class, () -> uploadService.cancel("upload-1"));

        assertEquals("upload_state_conflict", error.getErrorCode());
    }

    @Test
    public void cancelStaysIdempotentForExpiredCancelledSession() {
        when(fileUploadRepo.queryByUploadId("upload-1", SPACE_CODE))
                .thenReturn(sessionWith("CANCELLED", LocalDateTime.now().minusMinutes(1)));

        Upload result = uploadService.cancel("upload-1");

        assertEquals("cancelled", result.getStatus());
    }
}

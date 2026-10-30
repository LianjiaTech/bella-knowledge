package com.ke.bella.files.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileUploadRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileUploadDB;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.UploadOps.CreateUploadOp;
import com.ke.bella.files.protocol.UploadOps.CompleteUploadOp;
import com.ke.bella.files.service.lock.FileUniquenessLock;
import com.ke.bella.files.service.storage.StorageService;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;

public class UploadAkOwnershipTest {
    private UploadService service;
    private FileUploadRepo repo;
    private FileService files;
    private StorageService storage;
    private FileUploadDB session;

    @Before
    public void setup() {
        BellaContext.clearAll();
        BellaContext.setOperator(Operator.builder().userId(7L).spaceCode("sp-a").managerAk("system-ak").build());
        BellaContext.setApikey(ApikeyInfo.builder().code("effective-ak").userId(7L).build());
        service = new UploadService();
        repo = mock(FileUploadRepo.class);
        files = mock(FileService.class);
        storage = mock(StorageService.class);
        FileUniquenessLock lock = mock(FileUniquenessLock.class);
        when(lock.executeWithLock(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        BucketConfig buckets = new BucketConfig();
        buckets.setPrivateBucket("private");
        ReflectionTestUtils.setField(service, "fileUploadRepo", repo);
        ReflectionTestUtils.setField(service, "fileService", files);
        ReflectionTestUtils.setField(service, "storageService", storage);
        ReflectionTestUtils.setField(service, "bucketConfig", buckets);
        ReflectionTestUtils.setField(service, "fileUniquenessLock", lock);
        ReflectionTestUtils.setField(service, "maxBytes", 1000L);
        ReflectionTestUtils.setField(service, "sessionTtlHours", 24L);
        session = new FileUploadDB();
        session.setUploadId("upload-1");
        session.setFileId("file-upload-1");
        session.setSpaceCode("sp-a");
        session.setAkCode("upload-owner-ak");
        session.setFilename("a.txt");
        session.setPurpose("assistants");
        session.setBucket("private");
        session.setPath("path");
        session.setType("text");
        session.setMimeType("text/plain");
        session.setExtension("txt");
        session.setMetadata("{}");
        session.setDescription("");
        session.setAncestorId("");
        session.setDeclaredBytes(11L);
        session.setStatus("PENDING");
        session.setCtime(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(repo.queryByUploadId("upload-1", "sp-a")).thenReturn(session);
        when(repo.casStatus("upload-1", "PENDING", "COMPLETING")).thenReturn(true);
    }

    @After
    public void cleanup() {
        BellaContext.clearAll();
    }

    @Test
    public void createCapturesEffectiveAkBeforeStorageCall() {
        when(storage.createMultipartUpload(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    BellaContext.setApikey(ApikeyInfo.builder().code("changed-system-ak").build());
                    return "storage-upload";
                });
        when(repo.insert(any(FileUploadDB.class))).thenAnswer(invocation -> {
            FileUploadDB row = invocation.getArgument(0);
            row.setCtime(LocalDateTime.now());
            return row;
        });
        CreateUploadOp op = new CreateUploadOp();
        op.setFilename("a.txt");
        op.setPurpose("assistants");
        op.setBytes(11L);
        service.create(op);
        ArgumentCaptor<FileUploadDB> captor = ArgumentCaptor.forClass(FileUploadDB.class);
        verify(repo).insert(captor.capture());
        assertEquals("effective-ak", captor.getValue().getAkCode());
    }

    @Test
    public void completePassesStoredAkToCreationAndEvent() {
        assertCompletionUsesStoredAk("PENDING");
    }

    @Test
    public void retryPassesStoredAkToCreationAndEvent() {
        assertCompletionUsesStoredAk("COMPLETING");
    }

    private void assertCompletionUsesStoredAk(String status) {
        session.setStatus(status);
        when(storage.objectExists("private", "path")).thenReturn(true);
        FileDB created = new FileDB();
        when(files.createFileWithId("sp-a", "file-upload-1", "private", "path", "a.txt", 11L, "assistants", "{}",
                "text/plain", "text", "txt", null, "", null, null, "upload-owner-ak"))
                .thenReturn(FileService.FileUploadContext.builder().fileDB(created).build());
        when(files.finalizeFileUpload(created, "{}", "upload-owner-ak")).thenReturn(OpenAIFile.builder().bytes(11L).build());
        assertEquals("completed", service.complete("upload-1", new CompleteUploadOp()).getStatus());
        verify(files).finalizeFileUpload(created, "{}", "upload-owner-ak");
        verify(repo).bindFileId("upload-1", "file-upload-1");
    }

    @Test
    public void recoveryOfExistingFilePassesStoredAkWithoutRecreatingFile() {
        FileDB existing = new FileDB();
        when(files.getFile0("file-upload-1")).thenReturn(existing);
        service.complete("upload-1", new CompleteUploadOp());
        verify(files).finalizeFileUpload(existing, "{}", "upload-owner-ak");
        verifyNoInteractions(storage);
        verify(repo).bindFileId("upload-1", "file-upload-1");
    }
}

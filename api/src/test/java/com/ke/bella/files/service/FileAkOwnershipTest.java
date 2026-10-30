package com.ke.bella.files.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.service.broadcast.BroadcastService;
import com.ke.bella.files.service.storage.StorageService;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;

public class FileAkOwnershipTest {
    private FileService service;
    private FileRepo repo;
    private BroadcastService broadcast;
    private final AtomicReference<FileDB> inserted = new AtomicReference<>();

    @Before
    public void setup() {
        BellaContext.clearAll();
        BellaContext.setOperator(Operator.builder().userId(7L).userName("user").spaceCode("sp-a").managerAk("system-ak").build());
        BellaContext.setApikey(ApikeyInfo.builder().code("effective-ak").userId(7L).build());
        service = new FileService();
        repo = mock(FileRepo.class);
        broadcast = mock(BroadcastService.class);
        BucketConfig buckets = new BucketConfig();
        buckets.setPrivateBucket("private");
        ReflectionTestUtils.setField(service, "fileRepo", repo);
        ReflectionTestUtils.setField(service, "broadcastService", broadcast);
        ReflectionTestUtils.setField(service, "bucketConfig", buckets);
        ReflectionTestUtils.setField(service, "storageService", mock(StorageService.class));
        ReflectionTestUtils.setField(service, "self", service);
        when(repo.addFile(any(FileDB.class), nullable(String.class), any(FileType.class))).thenAnswer(invocation -> {
            FileDB row = invocation.getArgument(0);
            row.setCtime(LocalDateTime.now());
            row.setMtime(LocalDateTime.now());
            inserted.set(row);
            return "1";
        });
        when(repo.queryFile(anyString(), any(FileType.class))).thenAnswer(invocation -> inserted.get());
    }

    @After
    public void cleanup() {
        BellaContext.clearAll();
    }

    @Test
    public void explicitCapturedAkIsNotOverwrittenByCurrentContext() {
        service.createFileWithId("sp-a", "file-captured-1", "private", "path", "a.txt", 1L,
                "assistants", "{}", "text/plain", "text", "txt", null, null, null, null, "captured-ak");
        assertEquals("captured-ak", inserted.get().getAkCode());
    }

    @Test
    public void compatibleCreateMethodCapturesEffectiveAk() {
        service.createFileWithId("sp-a", "file-ordinary-1", "private", "path", "a.txt", 1L,
                "assistants", "{}", "text/plain", "text", "txt", null, null, null, null);
        assertEquals("effective-ak", inserted.get().getAkCode());
    }

    @Test
    public void directoryAndResourceStoreAndBroadcastEffectiveAk() {
        service.mkdir("dir", "", "", "assistants", "{}", null, null);
        assertEquals("effective-ak", inserted.get().getAkCode());
        service.createResource("resource", "dataset:1", "", "assistants");
        assertEquals("effective-ak", inserted.get().getAkCode());
        ArgumentCaptor<FileBroadcasting> captor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcast, times(2)).broadcast(captor.capture(), any(Runnable.class), any(Runnable.class));
        for(FileBroadcasting event : captor.getAllValues()) {
            assertEquals("effective-ak", event.getAkCode());
        }
    }

    @Test
    public void importStoresAndBroadcastsEffectiveAk() {
        service.importObject("sp-a", "external", "path", 1L, "a.txt", "assistants", "{}",
                "text/plain", "text", "txt", null, null, null, null);
        assertEquals("effective-ak", inserted.get().getAkCode());
        assertEventAk("effective-ak");
    }

    @Test
    public void directUploadKeepsCompatibleApiAndEffectiveAk() throws Exception {
        File file = File.createTempFile("issue121-", ".txt");
        try {
            service.upload(file, "a.txt", "assistants", "{}", "text/plain", "text", "txt", "UTF-8");
            assertEquals("effective-ak", inserted.get().getAkCode());
            assertEventAk("effective-ak");
        } finally {
            file.delete();
        }
    }

    @Test
    public void streamUploadStoresAndBroadcastsEffectiveAk() {
        service.uploadFromStream(new ByteArrayInputStream(new byte[] {1}), 1L, "a.txt", "assistants", "{}",
                "text/plain", "text", "txt", "UTF-8", null, null, null, null);
        assertEquals("effective-ak", inserted.get().getAkCode());
        assertEventAk("effective-ak");
    }

    private void assertEventAk(String expected) {
        ArgumentCaptor<FileBroadcasting> captor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcast).broadcast(captor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(expected, captor.getValue().getAkCode());
    }
}

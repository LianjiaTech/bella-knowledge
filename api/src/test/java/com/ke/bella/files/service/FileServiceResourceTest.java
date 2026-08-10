package com.ke.bella.files.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.FileShardingCountUpdator;
import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.EventType;
import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.Scope;
import com.ke.bella.files.service.broadcast.BroadcastService;
import com.ke.bella.files.service.storage.StorageService;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileServiceResourceTest {
    private FileService fileService;
    private FileRepo fileRepo;
    private StorageService storageService;
    private BroadcastService broadcastService;

    @Before
    public void setup() {
        fileService = new FileService();
        fileRepo = Mockito.mock(FileRepo.class);
        storageService = Mockito.mock(StorageService.class);
        broadcastService = Mockito.mock(BroadcastService.class);
        ReflectionTestUtils.setField(fileService, "fileRepo", fileRepo);
        ReflectionTestUtils.setField(fileService, "storageService", storageService);
        ReflectionTestUtils.setField(fileService, "bucketConfig", Mockito.mock(BucketConfig.class));
        ReflectionTestUtils.setField(fileService, "broadcastService", broadcastService);
        ReflectionTestUtils.setField(fileService, "fileShardingCountUpdator", Mockito.mock(FileShardingCountUpdator.class));
        ReflectionTestUtils.setField(fileService, "self", fileService);
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-a").build());
    }

    @Test
    public void createResourceWritesDatabaseNodeAndBroadcastsCreatedEvent() {
        AtomicReference<FileDB> inserted = new AtomicReference<>();
        when(fileRepo.addFile(any(FileDB.class), anyString(), any(FileType.class))).thenAnswer(invocation -> {
            FileDB file = invocation.getArgument(0);
            file.setCtime(LocalDateTime.now());
            file.setMtime(LocalDateTime.now());
            inserted.set(file);
            return "1";
        });
        when(fileRepo.queryFile(anyString(), any(FileType.class))).thenAnswer(invocation -> inserted.get());

        OpenAIFile resource = fileService.createResource("Sales dataset", "dataset:12345", "file-parent-1-d", "assistants");

        assertEquals(NodeType.RESOURCE.getValue(), resource.getNodeType());
        assertEquals("dataset:12345", resource.getResourceId());
        assertEquals("assistants", resource.getPurpose());
        assertFalse(resource.getIsDir());
        assertEquals(Long.valueOf(0L), resource.getBytes());
        verify(fileRepo).addFile(any(FileDB.class), org.mockito.ArgumentMatchers.eq("file-parent-1-d"),
                org.mockito.ArgumentMatchers.eq(FileType.USER));
        ArgumentCaptor<FileBroadcasting> messageCaptor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(messageCaptor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(EventType.FILE_CREATED.getValue(), messageCaptor.getValue().getEvent());
        assertEquals(resource, messageCaptor.getValue().getData());
        verifyNoInteractions(storageService);
    }

    @Test
    public void resourceCannotResolveContentUrl() {
        FileDB resource = resourceFile();
        when(fileRepo.queryFile("file-resource-1", FileType.USER)).thenReturn(resource);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> fileService.getUrl("file-resource-1"));

        assertTrue(error.getMessage().contains("no file content"));
        verifyNoInteractions(storageService);
    }

    @Test
    public void updateResourceBroadcastsUpdatedEvent() {
        FileDB resource = resourceFile();
        when(fileRepo.queryFile("file-resource-1", FileType.USER)).thenReturn(resource);

        OpenAIFile updated = fileService.updateFile(FileOps.builder()
                .fileId("file-resource-1")
                .filename("Renamed dataset")
                .build(), false, Scope.FILENAME);

        ArgumentCaptor<FileBroadcasting> messageCaptor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(messageCaptor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(EventType.FILE_UPDATED.getValue(), messageCaptor.getValue().getEvent());
        assertEquals(Scope.FILENAME.getValue(), messageCaptor.getValue().getScope());
        assertEquals(updated, messageCaptor.getValue().getData());
        verifyNoInteractions(storageService);
    }

    @Test
    public void deleteResourceBroadcastsDeletedEvent() {
        FileDB resource = resourceFile();

        fileService.delete(resource);

        ArgumentCaptor<FileBroadcasting> messageCaptor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(messageCaptor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(EventType.FILE_DELETED.getValue(), messageCaptor.getValue().getEvent());
        OpenAIFile deleted = (OpenAIFile) messageCaptor.getValue().getData();
        assertEquals(NodeType.RESOURCE.getValue(), deleted.getNodeType());
        assertEquals("dataset:12345", deleted.getResourceId());
        verify(fileRepo).updateFile(org.mockito.ArgumentMatchers.argThat(op -> op.getStatus() == FileStatus.DELETED),
                org.mockito.ArgumentMatchers.eq(false));
        verify(fileRepo).deleteFileClosure("file-resource-1", FileType.USER);
        verifyNoInteractions(storageService);
    }

    private FileDB resourceFile() {
        FileDB resource = new FileDB();
        resource.setFileId("file-resource-1");
        resource.setFilename("Sales dataset");
        resource.setNodeType(NodeType.RESOURCE.getValue());
        resource.setResourceId("dataset:12345");
        resource.setIsDir(0);
        resource.setBytes(0L);
        resource.setPurpose("");
        resource.setMetaData("{}");
        resource.setCtime(LocalDateTime.now());
        resource.setMtime(LocalDateTime.now());
        return resource;
    }
}

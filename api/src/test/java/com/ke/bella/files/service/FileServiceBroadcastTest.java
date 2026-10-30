package com.ke.bella.files.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.Before;
import org.junit.After;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.enums.FilePurpose;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.protocol.EventType;
import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.Scope;
import com.ke.bella.files.service.broadcast.BroadcastService;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileServiceBroadcastTest {
    private FileService fileService;
    private FileRepo fileRepo;
    private BroadcastService broadcastService;

    @Before
    public void setup() {
        BellaContext.clearAll();
        fileService = new FileService();
        fileRepo = Mockito.mock(FileRepo.class);
        broadcastService = Mockito.mock(BroadcastService.class);
        ReflectionTestUtils.setField(fileService, "fileRepo", fileRepo);
        ReflectionTestUtils.setField(fileService, "broadcastService", broadcastService);
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-a").build());
    }

    @After
    public void cleanup() {
        BellaContext.clearAll();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void storedAkWinsForCreatedUpdatedAndDeletedEvents() {
        FileDB file = file("file-owned-1", FilePurpose.ASSISTANTS);
        file.setAkCode("owner-ak");
        BellaContext.setApikey(ApikeyInfo.builder().code("system-ak").build());
        when(fileRepo.queryFile(file.getFileId(), FileType.USER)).thenReturn(file);
        fileService.finalizeFileUpload(file, "{}");
        fileService.updateFile(FileOps.builder().fileId(file.getFileId()).filename("renamed").build(), false, Scope.FILENAME);
        fileService.delete(file);
        ArgumentCaptor<FileBroadcasting> captor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService, times(3)).broadcast(captor.capture(), any(Runnable.class), any(Runnable.class));
        for(FileBroadcasting message : captor.getAllValues()) {
            assertEquals("owner-ak", message.getAkCode());
        }
    }

    @Test
    public void derivedEventsUseOriginalSourceAk() {
        for(FilePurpose purpose : new FilePurpose[] {FilePurpose.DOM_TREE, FilePurpose.PDF}) {
            FileDB derived = file("file-derived-" + purpose.getValue(), purpose);
            derived.setAkCode("parser-ak");
            FileDB source = file("file-source-1", FilePurpose.ASSISTANTS);
            source.setAkCode("original-ak");
            org.mockito.Mockito.doReturn(source).when(fileRepo).queryFileByDomTreeFileId(derived.getFileId());
            when(fileRepo.queryFileByPdfFileId(derived.getFileId())).thenReturn(source);
            fileService.finalizeFileUpload(derived, "{}");
        }
        ArgumentCaptor<FileBroadcasting> captor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService, times(2)).broadcast(captor.capture(), any(Runnable.class), any(Runnable.class));
        for(FileBroadcasting message : captor.getAllValues()) {
            assertEquals("original-ak", message.getAkCode());
        }
    }

    @Test
    public void missingOrBrokenSourceLookupPreservesDerivedAk() {
        FileDB derived = systemFile(FilePurpose.DOM_TREE);
        derived.setAkCode("derived-ak");
        fileService.finalizeFileUpload(derived, "{}");
        when(fileRepo.queryFileByDomTreeFileId(derived.getFileId())).thenThrow(new IllegalStateException("lookup failed"));
        fileService.finalizeFileUpload(derived, "{}");
        FileDB source = file("file-empty-source", FilePurpose.ASSISTANTS);
        org.mockito.Mockito.doReturn(source).when(fileRepo).queryFileByDomTreeFileId(derived.getFileId());
        fileService.finalizeFileUpload(derived, "{}");
        ArgumentCaptor<FileBroadcasting> captor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService, times(3)).broadcast(captor.capture(), any(Runnable.class), any(Runnable.class));
        for(FileBroadcasting message : captor.getAllValues()) {
            assertEquals("derived-ak", message.getAkCode());
        }
    }

    @Test
    public void historicalEmptyAkOnlyFallsBackWithinSynchronousRequest() {
        FileDB file = file("file-historical-1", FilePurpose.ASSISTANTS);
        BellaContext.setApikey(ApikeyInfo.builder().code("effective-ak").build());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        fileService.finalizeFileUpload(file, "{}");
        fileService.finalizeFileUpload(file, "{}", false);
        RequestContextHolder.resetRequestAttributes();
        fileService.finalizeFileUpload(file, "{}");
        ArgumentCaptor<FileBroadcasting> captor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService, times(3)).broadcast(captor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals("effective-ak", captor.getAllValues().get(0).getAkCode());
        assertEquals(null, captor.getAllValues().get(1).getAkCode());
        assertEquals(null, captor.getAllValues().get(2).getAkCode());
        assertEquals(null, file.getAkCode());
    }

    @Test
    public void uploadRecoveryUsesCapturedAkWithoutRewritingHistoricalRecord() {
        FileDB file = file("file-retry-1", FilePurpose.ASSISTANTS);
        BellaContext.setApikey(ApikeyInfo.builder().code("system-ak").build());
        fileService.finalizeFileUpload(file, "{}", "upload-owner-ak");
        ArgumentCaptor<FileBroadcasting> captor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(captor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals("upload-owner-ak", captor.getValue().getAkCode());
        assertEquals(null, file.getAkCode());
    }

    @Test
    public void finalizeUploadSkipsBroadcastForNonAssistantTempFiles() {
        for (FilePurpose purpose : new FilePurpose[] {
                FilePurpose.BATCH,
                FilePurpose.TEMP,
                FilePurpose.FINE_TUNE,
                FilePurpose.EVALS,
                FilePurpose.VISION }) {
            fileService.finalizeFileUpload(tempFile(purpose), "{}");
        }

        verifyNoInteractions(broadcastService);
        verifyNoInteractions(fileRepo);
    }

    @Test
    public void finalizeUploadBroadcastsAssistantChatTempFile() {
        FileDB file = tempFile(FilePurpose.ASSISTANTS_CHAT);

        fileService.finalizeFileUpload(file, "{}");

        ArgumentCaptor<FileBroadcasting> messageCaptor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(messageCaptor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(EventType.FILE_CREATED.getValue(), messageCaptor.getValue().getEvent());
    }

    @Test
    public void finalizeUploadSkipsBroadcastForNonDomTreeSystemFiles() {
        for (FilePurpose purpose : new FilePurpose[] {
                FilePurpose.PDF,
                FilePurpose.DATASETS_EXPORT }) {
            fileService.finalizeFileUpload(systemFile(purpose), "{}");
        }

        verify(fileRepo).queryFileByPdfFileId("file-test-s");
        verifyNoMoreInteractions(fileRepo);
        verifyNoInteractions(broadcastService);
    }

    @Test
    public void finalizeUploadBroadcastsDomTreeSystemFile() {
        FileDB file = systemFile(FilePurpose.DOM_TREE);

        fileService.finalizeFileUpload(file, "{}");

        verify(fileRepo).queryFileByDomTreeFileId(file.getFileId());
        assertBroadcastEvent(EventType.FILE_CREATED);
    }

    @Test
    public void updateSkipsBroadcastForNonAssistantTempFile() {
        FileDB file = tempFile(FilePurpose.VISION);
        FileOps ops = FileOps.builder().fileId(file.getFileId()).filename("updated.png").build();
        when(fileRepo.queryFile(file.getFileId(), FileType.TEMP)).thenReturn(file);

        fileService.updateFile(ops, false, Scope.FILENAME);

        verify(fileRepo, times(1)).updateFile(ops, false);
        verifyNoInteractions(broadcastService);
    }

    @Test
    public void deleteSkipsBroadcastForNonAssistantTempFile() {
        FileDB file = tempFile(FilePurpose.FINE_TUNE);

        fileService.delete(file);

        ArgumentCaptor<FileOps> opsCaptor = ArgumentCaptor.forClass(FileOps.class);
        verify(fileRepo).updateFile(opsCaptor.capture(), eq(false));
        assertEquals(FileStatus.DELETED, opsCaptor.getValue().getStatus());
        verifyNoInteractions(broadcastService);
    }

    @Test
    public void updateSkipsBroadcastForSystemFile() {
        FileDB file = systemFile(FilePurpose.DATASETS_EXPORT);
        FileOps ops = FileOps.builder().fileId(file.getFileId()).filename("updated.json").build();
        when(fileRepo.queryFile(file.getFileId(), FileType.SYSTEM)).thenReturn(file);

        fileService.updateFile(ops, false, Scope.FILENAME);

        verify(fileRepo).updateFile(ops, false);
        verify(fileRepo).queryFile(file.getFileId(), FileType.SYSTEM);
        verifyNoMoreInteractions(fileRepo);
        verifyNoInteractions(broadcastService);
    }

    @Test
    public void updateBroadcastsDomTreeSystemFile() {
        FileDB file = systemFile(FilePurpose.DOM_TREE);
        FileOps ops = FileOps.builder().fileId(file.getFileId()).filename("updated.json").build();
        when(fileRepo.queryFile(file.getFileId(), FileType.SYSTEM)).thenReturn(file);

        fileService.updateFile(ops, false, Scope.FILENAME);

        verify(fileRepo).updateFile(ops, false);
        verify(fileRepo).queryFile(file.getFileId(), FileType.SYSTEM);
        verify(fileRepo).queryFileByDomTreeFileId(file.getFileId());
        assertBroadcastEvent(EventType.FILE_UPDATED);
    }

    @Test
    public void metadataUpdateBroadcastsMetadataScope() {
        FileDB file = file("file-test-u", FilePurpose.ASSISTANTS);
        file.setMetaData("{\"team\":\"search\"}");
        FileOps ops = FileOps.builder().fileId(file.getFileId()).metadata(file.getMetaData()).build();
        when(fileRepo.queryFile(file.getFileId(), FileType.USER)).thenReturn(file);

        fileService.updateFile(ops, false, Scope.METADATA);

        ArgumentCaptor<FileBroadcasting> messageCaptor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(messageCaptor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(EventType.FILE_UPDATED.getValue(), messageCaptor.getValue().getEvent());
        assertEquals(Scope.METADATA.getValue(), messageCaptor.getValue().getScope());
        assertEquals(file.getMetaData(), messageCaptor.getValue().getMetadata());
    }

    @Test
    public void updateCreatorBroadcastsDedicatedScope() {
        FileDB file = file("file-test-1", FilePurpose.ASSISTANTS);
        LocalDateTime ctime = LocalDateTime.of(2024, 1, 2, 3, 4, 5);
        file.setCuid(42L);
        file.setCuName("original creator");
        file.setCtime(ctime);
        when(fileRepo.queryFile(file.getFileId(), FileType.USER)).thenReturn(file);

        fileService.updateCreatorInfo(file.getFileId(), 42L, "original creator", ctime);

        verify(fileRepo).updateCreatorInfo(file.getFileId(), 42L, "original creator", ctime);
        ArgumentCaptor<FileBroadcasting> messageCaptor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(messageCaptor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(EventType.FILE_UPDATED.getValue(), messageCaptor.getValue().getEvent());
        assertEquals(Scope.CREATOR.getValue(), messageCaptor.getValue().getScope());
        assertEquals(Long.valueOf(42L), ((com.ke.bella.files.protocol.OpenAIFile) messageCaptor.getValue().getData()).getCuid());
    }

    @Test
    public void updateCreatorBroadcastsWhenUpdatingSingleField() {
        FileDB file = file("file-test-1", FilePurpose.ASSISTANTS);
        file.setCuid(42L);
        file.setCuName("creator");
        when(fileRepo.queryFile(file.getFileId(), FileType.USER)).thenReturn(file);

        fileService.updateCreatorInfo(file.getFileId(), 42L, null, null);

        verify(fileRepo).updateCreatorInfo(file.getFileId(), 42L, null, null);
        ArgumentCaptor<FileBroadcasting> messageCaptor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(messageCaptor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(Scope.CREATOR.getValue(), messageCaptor.getValue().getScope());
    }

    @Test
    public void deleteSkipsBroadcastForSystemFile() {
        FileDB file = systemFile(FilePurpose.DATASETS_EXPORT);

        fileService.delete(file);

        ArgumentCaptor<FileOps> opsCaptor = ArgumentCaptor.forClass(FileOps.class);
        verify(fileRepo).updateFile(opsCaptor.capture(), eq(false));
        assertEquals(FileStatus.DELETED, opsCaptor.getValue().getStatus());
        verifyNoMoreInteractions(fileRepo);
        verifyNoInteractions(broadcastService);
    }

    @Test
    public void deleteBroadcastsDomTreeSystemFile() {
        FileDB file = systemFile(FilePurpose.DOM_TREE);

        fileService.delete(file);

        ArgumentCaptor<FileOps> opsCaptor = ArgumentCaptor.forClass(FileOps.class);
        verify(fileRepo).updateFile(opsCaptor.capture(), eq(false));
        assertEquals(FileStatus.DELETED, opsCaptor.getValue().getStatus());
        verify(fileRepo).queryFileByDomTreeFileId(file.getFileId());
        assertBroadcastEvent(EventType.FILE_DELETED);
    }

    private void assertBroadcastEvent(EventType eventType) {
        ArgumentCaptor<FileBroadcasting> messageCaptor = ArgumentCaptor.forClass(FileBroadcasting.class);
        verify(broadcastService).broadcast(messageCaptor.capture(), any(Runnable.class), any(Runnable.class));
        assertEquals(eventType.getValue(), messageCaptor.getValue().getEvent());
    }

    private FileDB tempFile(FilePurpose purpose) {
        return file("file-test-t", purpose);
    }

    private FileDB systemFile(FilePurpose purpose) {
        return file("file-test-s", purpose);
    }

    private FileDB file(String fileId, FilePurpose purpose) {
        FileDB file = new FileDB();
        file.setFileId(fileId);
        file.setFilename("test.txt");
        file.setPurpose(purpose.getValue());
        file.setMetaData("{}");
        file.setBytes(1L);
        file.setIsDir(0);
        file.setCtime(LocalDateTime.now());
        file.setMtime(LocalDateTime.now());
        return file;
    }
}

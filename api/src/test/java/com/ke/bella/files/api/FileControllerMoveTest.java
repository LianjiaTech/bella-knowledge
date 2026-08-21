package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.files.api.interceptor.FileApiResponseAdvice;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileControllerMoveTest {

    private MockMvc mockMvc;
    private FileService fileService;
    private FileUniquenessLock fileUniquenessLock;

    @Before
    public void setup() {
        fileService = Mockito.mock(FileService.class);
        fileUniquenessLock = Mockito.mock(FileUniquenessLock.class);
        FileController fileController = new FileController();

        ReflectionTestUtils.setField(fileController, "fileService", fileService);
        ReflectionTestUtils.setField(fileController, "fl", fileUniquenessLock);
        when(fileUniquenessLock.executeWithMoveLock(any(), anyBoolean(), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get());

        mockMvc = MockMvcBuilders
                .standaloneSetup(fileController)
                .setControllerAdvice(new FileApiResponseAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    private static FileDB buildFile(String fileId, String filename, boolean isDir, String spaceCode) {
        FileDB f = new FileDB();
        f.setFileId(fileId);
        f.setFilename(filename);
        f.setIsDir(isDir ? 1 : 0);
        f.setSpaceCode(spaceCode);
        return f;
    }

    @Test
    public void moveSuccess() throws Exception {
        String ancestorId = "anc-1";
        String spaceCode = "sp-a";
        String fileId = "f-1";

        FileDB ancestor = buildFile(ancestorId, "anc", true, spaceCode);
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        OpenAIFile moved = OpenAIFile.builder()
                .id(fileId)
                .filename("name.txt")
                .spaceCode(spaceCode)
                .build();

        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq("name.txt"), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
        when(fileService.moveFile(fileId, ancestorId)).thenReturn(moved);

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId))
                .andExpect(jsonPath("$.filename").value("name.txt"))
                .andExpect(jsonPath("$.spaceCode").value(spaceCode));

        verify(fileUniquenessLock).executeWithMoveLock(eq(spaceCode), eq(false), anyLong(), any());
        verify(fileService).moveFile(fileId, ancestorId);
    }

    @Test
    public void moveDirectorySuccess() throws Exception {
        String ancestorId = "anc-1";
        String spaceCode = "sp-a";
        String fileId = "dir-1";

        FileDB ancestor = buildFile(ancestorId, "target", true, spaceCode);
        FileDB source = buildFile(fileId, "source", true, spaceCode);

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq("source"), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
        when(fileService.moveFile(fileId, ancestorId)).thenReturn(OpenAIFile.builder()
                .id(fileId)
                .filename("source")
                .spaceCode(spaceCode)
                .build());

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId));

        verify(fileUniquenessLock).executeWithMoveLock(eq(spaceCode), eq(true), anyLong(), any());
        verify(fileService).moveFile(fileId, ancestorId);
    }

    @Test
    public void moveToRootWhenAncestorIdMissing() throws Exception {
        String spaceCode = "sp-a";
        String fileId = "f-1";
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);
        OpenAIFile moved = OpenAIFile.builder()
                .id(fileId)
                .filename("name.txt")
                .spaceCode(spaceCode)
                .build();

        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq((String) null), eq("name.txt"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(fileService.getDirectAncestorId(fileId)).thenReturn("parent-1");
        when(fileService.moveFile(fileId, null)).thenReturn(moved);

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId));

        verify(fileService).exists(spaceCode, null, "name.txt");
        verify(fileService).moveFile(fileId, null);
    }

    @Test
    public void moveToRootWhenAncestorIdEmpty() throws Exception {
        String spaceCode = "sp-a";
        String fileId = "dir-1";
        FileDB source = buildFile(fileId, "source", true, spaceCode);

        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq((String) null), eq("source"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(fileService.getDirectAncestorId(fileId)).thenReturn("parent-1");
        when(fileService.moveFile(fileId, null)).thenReturn(OpenAIFile.builder().id(fileId).build());

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"\"}"))
                .andExpect(status().isOk());

        verify(fileUniquenessLock).executeWithMoveLock(eq(spaceCode), eq(true), anyLong(), any());
        verify(fileService).moveFile(fileId, null);
    }

    @Test
    public void moveToRootRejectsFileAlreadyAtRoot() throws Exception {
        String spaceCode = "sp-a";
        String fileId = "f-1";
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);

        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq((String) null), eq("name.txt"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(fileService.getDirectAncestorId(fileId)).thenReturn(null);

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("file already in target directory"));

        verify(fileService, never()).moveFile(any(), any());
    }

    @Test
    public void moveToRootRejectsDuplicateName() throws Exception {
        String spaceCode = "sp-a";
        String fileId = "f-1";
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);

        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq((String) null), eq("name.txt"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(fileService.getDirectAncestorId(fileId)).thenReturn("parent-1");
        when(fileService.exists(spaceCode, null, "name.txt")).thenReturn(true);

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("filename already exists"));

        verify(fileService, never()).moveFile(any(), any());
    }

    @Test
    public void moveToRootRejectsSourceFromAnotherSpace() throws Exception {
        String fileId = "f-1";
        when(fileService.getFile0(fileId)).thenReturn(buildFile(fileId, "name.txt", false, "sp-b"));

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", "sp-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("space mismatch between context and file_id"));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
    }

    @Test
    public void moveCurrentDirectoryCheckedInsideLock() throws Exception {
        String ancestorId = "anc-1";
        String spaceCode = "sp-a";
        String fileId = "dir-1";
        FileDB ancestor = buildFile(ancestorId, "target", true, spaceCode);
        FileDB source = buildFile(fileId, "source", true, spaceCode);

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileService.getDirectAncestorId(fileId)).thenReturn(ancestorId);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq("source"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("file already in target directory"));

        verify(fileService, never()).moveFile(any(), any());
    }

    @Test
    public void moveDuplicateNameCheckedInsideLock() throws Exception {
        String ancestorId = "anc-1";
        String spaceCode = "sp-a";
        String fileId = "dir-1";
        FileDB ancestor = buildFile(ancestorId, "target", true, spaceCode);
        FileDB source = buildFile(fileId, "source", true, spaceCode);

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileService.exists(spaceCode, ancestorId, "source")).thenReturn(true);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq("source"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("filename already exists"));

        verify(fileService, never()).moveFile(any(), any());
    }

    @Test
    public void moveCrossSpaceRejected() throws Exception {
        String ancestorId = "anc-1";
        String fileId = "dir-1";
        when(fileService.getFile0(ancestorId)).thenReturn(buildFile(ancestorId, "target", true, "sp-a"));
        when(fileService.getFile0(fileId)).thenReturn(buildFile(fileId, "source", true, "sp-b"));

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", "sp-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("space mismatch between context and file_id"));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
    }

    @Test
    public void moveFileNotFound() throws Exception {
        String ancestorId = "anc-1";
        String spaceCode = "sp-a";
        String fileId = "missing";

        when(fileService.getFile0(ancestorId)).thenReturn(buildFile(ancestorId, "anc", true, spaceCode));
        when(fileService.getFile0(fileId)).thenReturn(null);

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("File not found: " + fileId));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).moveFile(any(), any());
    }

    @Test
    public void moveLockConflict() throws Exception {
        String ancestorId = "anc-1";
        String spaceCode = "sp-a";
        String fileId = "f-1";
        FileDB ancestor = buildFile(ancestorId, "anc", true, spaceCode);
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq("name.txt"), anyLong(), any()))
                .thenThrow(new IllegalStateException("locked"));

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.message").value("move file failed"));
    }

    @Test
    public void moveInvalidAncestorId() throws Exception {
        String ancestorId = "invalid-anc";
        when(fileService.getFile0(ancestorId)).thenReturn(null);

        String body = "{\"file_id\":\"f-1\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", "sp-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("File not found: " + ancestorId));
    }

    @Test
    public void moveAncestorIsNotDir() throws Exception {
        String ancestorId = "anc-1";
        FileDB ancestor = buildFile(ancestorId, "anc", false, "sp-a");
        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);

        String body = "{\"file_id\":\"f-1\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", "sp-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("ancestor_id must refer to a directory"));
    }

    @Test
    public void moveServiceThrowsIllegalArgument() throws Exception {
        String ancestorId = "anc-1";
        String spaceCode = "sp-a";
        String fileId = "f-1";
        FileDB ancestor = buildFile(ancestorId, "anc", true, spaceCode);
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq("name.txt"), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });

        doThrow(new IllegalArgumentException("invalid request reason"))
                .when(fileService).moveFile(fileId, ancestorId);

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("invalid request reason"));
    }

    @Test
    public void moveMissingFileIdBadRequest() throws Exception {
        String body = "{\"ancestor_id\":\"anc-1\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", "sp-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("file_id is required and cannot be empty"));
    }

    @Test
    public void moveNullRequestBodyBadRequest() throws Exception {
        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .header("X-BELLA-SPACE-CODE", "sp-a")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}

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
import org.mockito.InOrder;
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

    private void stubNameLock(String spaceCode, String ancestorId, String filename) {
        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq(filename), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
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

        stubNameLock(spaceCode, ancestorId, "name.txt");
        when(fileService.moveFile(source, null, ancestor)).thenReturn(moved);

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId))
                .andExpect(jsonPath("$.filename").value("name.txt"))
                .andExpect(jsonPath("$.spaceCode").value(spaceCode));

        verify(fileUniquenessLock).executeWithMoveLock(eq(spaceCode), eq(false), anyLong(), any());
        verify(fileService).moveFile(source, null, ancestor);
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
        stubNameLock(spaceCode, ancestorId, "source");
        when(fileService.moveFile(source, null, ancestor)).thenReturn(OpenAIFile.builder()
                .id(fileId)
                .filename("source")
                .spaceCode(spaceCode)
                .build());

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId));

        verify(fileUniquenessLock).executeWithMoveLock(eq(spaceCode), eq(true), anyLong(), any());
        verify(fileService).moveFile(source, null, ancestor);
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
        stubNameLock(spaceCode, null, "name.txt");
        when(fileService.getDirectAncestorId(fileId)).thenReturn("parent-1");
        when(fileService.moveFile(source, null, null)).thenReturn(moved);

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId));

        verify(fileService).exists(spaceCode, null, "name.txt");
        verify(fileService).moveFile(source, null, null);
    }

    @Test
    public void moveToRootWhenAncestorIdEmpty() throws Exception {
        String spaceCode = "sp-a";
        String fileId = "dir-1";
        FileDB source = buildFile(fileId, "source", true, spaceCode);

        when(fileService.getFile0(fileId)).thenReturn(source);
        stubNameLock(spaceCode, null, "source");
        when(fileService.getDirectAncestorId(fileId)).thenReturn("parent-1");
        when(fileService.moveFile(source, null, null)).thenReturn(OpenAIFile.builder().id(fileId).build());

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"\"}"))
                .andExpect(status().isOk());

        verify(fileUniquenessLock).executeWithMoveLock(eq(spaceCode), eq(true), anyLong(), any());
        verify(fileService).moveFile(source, null, null);
    }

    @Test
    public void moveToRootRejectsFileAlreadyAtRoot() throws Exception {
        String spaceCode = "sp-a";
        String fileId = "f-1";
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);

        when(fileService.getFile0(fileId)).thenReturn(source);
        stubNameLock(spaceCode, null, "name.txt");
        when(fileService.getDirectAncestorId(fileId)).thenReturn(null);

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("file already in target directory"));

        verify(fileService, never()).moveFile(any(), any(), any());
    }

    @Test
    public void moveToRootRejectsDuplicateName() throws Exception {
        String spaceCode = "sp-a";
        String fileId = "f-1";
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);

        when(fileService.getFile0(fileId)).thenReturn(source);
        stubNameLock(spaceCode, null, "name.txt");
        when(fileService.getDirectAncestorId(fileId)).thenReturn("parent-1");
        when(fileService.exists(spaceCode, null, "name.txt")).thenReturn(true);

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"file_id\":\"" + fileId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("filename already exists"));

        verify(fileService, never()).moveFile(any(), any(), any());
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
        stubNameLock(spaceCode, ancestorId, "source");

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("file already in target directory"));

        verify(fileService, never()).moveFile(any(), any(), any());
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
        stubNameLock(spaceCode, ancestorId, "source");

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("filename already exists"));

        verify(fileService, never()).moveFile(any(), any(), any());
    }

    @Test
    public void moveCrossSpaceSuccess() throws Exception {
        String ancestorId = "anc-1";
        String fileId = "f-1";
        FileDB ancestor = buildFile(ancestorId, "target", true, "sp-b");
        FileDB source = buildFile(fileId, "name.txt", false, "sp-a");

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        stubNameLock("sp-b", ancestorId, "name.txt");
        when(fileService.moveFile(source, "sp-b", ancestor)).thenReturn(OpenAIFile.builder()
                .id(fileId)
                .filename("name.txt")
                .spaceCode("sp-b")
                .build());

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId
                + "\",\"target_space_code\":\"sp-b\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId))
                .andExpect(jsonPath("$.spaceCode").value("sp-b"));

        // 源/目标两个空间的 move 锁都必须持有；名称锁与重名检查落在目标空间
        verify(fileUniquenessLock).executeWithMoveLock(eq("sp-a"), eq(false), anyLong(), any());
        verify(fileUniquenessLock).executeWithMoveLock(eq("sp-b"), eq(false), anyLong(), any());
        verify(fileService).exists("sp-b", ancestorId, "name.txt");
        verify(fileService).moveFile(source, "sp-b", ancestor);
        verify(fileService, never()).getDirectAncestorId(any());
    }

    @Test
    public void moveCrossSpaceInfersTargetSpaceFromAncestor() throws Exception {
        String ancestorId = "anc-1";
        String fileId = "dir-1";
        FileDB ancestor = buildFile(ancestorId, "target", true, "sp-a");
        FileDB source = buildFile(fileId, "source", true, "sp-b");

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        stubNameLock("sp-a", ancestorId, "source");
        when(fileService.moveFile(source, "sp-a", ancestor)).thenReturn(OpenAIFile.builder()
                .id(fileId)
                .spaceCode("sp-a")
                .build());

        // 未传 target_space_code：目标空间由 ancestor 所在空间推断
        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-b").build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId));

        verify(fileService).moveFile(source, "sp-a", ancestor);
    }

    @Test
    public void moveCrossSpaceToRootByExplicitTargetSpace() throws Exception {
        String fileId = "f-1";
        FileDB source = buildFile(fileId, "name.txt", false, "sp-a");

        when(fileService.getFile0(fileId)).thenReturn(source);
        stubNameLock("sp-b", null, "name.txt");
        when(fileService.moveFile(source, "sp-b", null)).thenReturn(OpenAIFile.builder()
                .id(fileId)
                .spaceCode("sp-b")
                .build());

        // 文件在源空间根目录：跨空间 root→root 迁移合法，不做 already-in-target 检查
        String body = "{\"file_id\":\"" + fileId + "\",\"target_space_code\":\"sp-b\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId));

        verify(fileService, never()).getDirectAncestorId(any());
        verify(fileService).exists("sp-b", null, "name.txt");
        verify(fileService).moveFile(source, "sp-b", null);
    }

    @Test
    public void moveCrossSpaceLockOrderIsLexicographic() throws Exception {
        String fileId = "f-1";
        FileDB source = buildFile(fileId, "name.txt", false, "sp-b");

        when(fileService.getFile0(fileId)).thenReturn(source);
        stubNameLock("sp-a", null, "name.txt");
        when(fileService.moveFile(source, "sp-a", null)).thenReturn(OpenAIFile.builder().id(fileId).build());

        String body = "{\"file_id\":\"" + fileId + "\",\"target_space_code\":\"sp-a\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-b").build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        // 源 sp-b → 目标 sp-a：仍按字典序先锁 sp-a 再锁 sp-b，消除 A→B 与 B→A 的环形等待
        InOrder inOrder = Mockito.inOrder(fileUniquenessLock);
        inOrder.verify(fileUniquenessLock).executeWithMoveLock(eq("sp-a"), eq(false), anyLong(), any());
        inOrder.verify(fileUniquenessLock).executeWithMoveLock(eq("sp-b"), eq(false), anyLong(), any());
    }

    @Test
    public void moveRejectsTargetSpaceMismatchWithAncestor() throws Exception {
        String ancestorId = "anc-1";
        String fileId = "f-1";
        when(fileService.getFile0(ancestorId)).thenReturn(buildFile(ancestorId, "target", true, "sp-b"));
        when(fileService.getFile0(fileId)).thenReturn(buildFile(fileId, "name.txt", false, "sp-a"));

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId
                + "\",\"target_space_code\":\"sp-c\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("space_code mismatch between target space and ancestor_id"));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).moveFile(any(), any(), any());
    }

    @Test
    public void moveBlankTargetSpaceCodeBehavesAsSameSpace() throws Exception {
        String ancestorId = "anc-1";
        String spaceCode = "sp-a";
        String fileId = "f-1";
        FileDB ancestor = buildFile(ancestorId, "anc", true, spaceCode);
        FileDB source = buildFile(fileId, "name.txt", false, spaceCode);

        when(fileService.getFile0(ancestorId)).thenReturn(ancestor);
        when(fileService.getFile0(fileId)).thenReturn(source);
        stubNameLock(spaceCode, ancestorId, "name.txt");
        when(fileService.moveFile(source, null, ancestor)).thenReturn(OpenAIFile.builder().id(fileId).build());

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId
                + "\",\"target_space_code\":\"   \"}";

        BellaContext.setOperator(Operator.builder().spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        // 空白 target_space_code 等价于未传：单空间锁 + 同空间移动
        verify(fileUniquenessLock).executeWithMoveLock(eq(spaceCode), eq(false), anyLong(), any());
        verify(fileService).moveFile(source, null, ancestor);
    }

    @Test
    public void moveFileNotFound() throws Exception {
        String ancestorId = "anc-1";
        String fileId = "missing";

        when(fileService.getFile0(fileId)).thenReturn(null);

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("File not found: " + fileId));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).moveFile(any(), any(), any());
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.message").value("move file failed"));
    }

    @Test
    public void moveInvalidAncestorId() throws Exception {
        String ancestorId = "invalid-anc";
        String fileId = "f-1";
        when(fileService.getFile0(fileId)).thenReturn(buildFile(fileId, "name.txt", false, "sp-a"));
        when(fileService.getFile0(ancestorId)).thenReturn(null);

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("File not found: " + ancestorId));
    }

    @Test
    public void moveAncestorIsNotDir() throws Exception {
        String ancestorId = "anc-1";
        String fileId = "f-1";
        when(fileService.getFile0(fileId)).thenReturn(buildFile(fileId, "name.txt", false, "sp-a"));
        when(fileService.getFile0(ancestorId)).thenReturn(buildFile(ancestorId, "anc", false, "sp-a"));

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
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
        stubNameLock(spaceCode, ancestorId, "name.txt");

        doThrow(new IllegalArgumentException("invalid request reason"))
                .when(fileService).moveFile(source, null, ancestor);

        String body = "{\"file_id\":\"" + fileId + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/move")
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("file_id is required and cannot be empty"));
    }

    @Test
    public void moveNullRequestBodyBadRequest() throws Exception {
        BellaContext.setOperator(Operator.builder().spaceCode("sp-a").build());
        mockMvc.perform(post("/v1/files/move")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}

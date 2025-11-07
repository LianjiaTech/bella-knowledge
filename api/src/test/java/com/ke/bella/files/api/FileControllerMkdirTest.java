package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;
import com.ke.bella.files.utils.FilePurposeClassifier;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileControllerMkdirTest {

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

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy.SNAKE_CASE);

        mockMvc = MockMvcBuilders
                .standaloneSetup(fileController)
                .setControllerAdvice(new FileApiResponseAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    public void mkdir_Success_WithoutPurpose() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = "test-dir";
        String description = "test description";

        OpenAIFile createdDir = OpenAIFile.builder()
                .id("dir-1")
                .filename(dirName)
                .spaceCode(spaceCode)
                .build();

        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq(dirName), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
        when(fileService.exists(spaceCode, ancestorId, dirName)).thenReturn(false);
        when(fileService.mkdir(dirName, ancestorId, description, null)).thenReturn(createdDir);

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\",\"description\":\"" + description + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("dir-1"))
                .andExpect(jsonPath("$.filename").value(dirName))
                .andExpect(jsonPath("$.space_code").value(spaceCode));

        verify(fileService).mkdir(dirName, ancestorId, description, null);
    }

    @Test
    public void mkdir_Success_WithPurpose() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = "test-dir";
        String description = "test description";
        String purpose = "assistants";

        OpenAIFile createdDir = OpenAIFile.builder()
                .id("dir-1")
                .filename(dirName)
                .spaceCode(spaceCode)
                .purpose(purpose)
                .build();

        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq(dirName), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
        when(fileService.exists(spaceCode, ancestorId, dirName)).thenReturn(false);
        when(fileService.mkdir(dirName, ancestorId, description, purpose)).thenReturn(createdDir);

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\",\"description\":\"" + description + "\",\"purpose\":\""
                + purpose + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("dir-1"))
                .andExpect(jsonPath("$.filename").value(dirName))
                .andExpect(jsonPath("$.space_code").value(spaceCode));

        verify(fileService).mkdir(dirName, ancestorId, description, purpose);
    }

    @Test
    public void mkdir_InvalidPurpose() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = "test-dir";
        String invalidPurpose = "invalid-purpose";

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\",\"purpose\":\"" + invalidPurpose + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Unsupported purpose")))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString(invalidPurpose)));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_DirectoryAlreadyExists() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = "existing-dir";

        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq(dirName), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
        when(fileService.exists(spaceCode, ancestorId, dirName)).thenReturn(true);

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("already exists")))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString(dirName)));

        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_LockConflict() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = "test-dir";

        when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq(dirName), anyLong(), any()))
                .thenThrow(new IllegalStateException("locked"));

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void mkdir_MissingName_BadRequest() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";

        String body = "{\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("name is required"));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_EmptyName_BadRequest() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";

        String body = "{\"name\":\"\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("name is required"));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_NullRequestBody_InternalServerError() throws Exception {
        String spaceCode = "sp-a";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_NameWithWhitespace_BadRequest() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = " test-dir ";

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("whitespace")));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_NameTooLong_BadRequest() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        // 创建一个超过最大长度的目录名 (假设最大长度为255)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("a");
        }
        String dirName = sb.toString();

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("too long")));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_DescriptionTooLong_BadRequest() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = "test-dir";
        // 创建一个超过最大长度的描述 (假设最大长度为512)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            sb.append("a");
        }
        String description = sb.toString();

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\",\"description\":\"" + description + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Description too long")));

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_InvalidDirectoryName_Dot_BadRequest() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = ".";

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_InvalidDirectoryName_DoubleDot_BadRequest() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = "..";

        String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\"}";

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
        mockMvc.perform(post("/v1/files/mkdir")
                .header("X-BELLA-SPACE-CODE", spaceCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).mkdir(any(), any(), any(), any());
    }

    @Test
    public void mkdir_Success_WithAllValidPurposes() throws Exception {
        String spaceCode = "sp-a";
        String ancestorId = "anc-1";
        String dirName = "test-dir";

        // 从 FilePurposeClassifier 动态获取所有有效的 purpose 值，确保与系统配置保持同步
        for (String purpose : FilePurposeClassifier.allowedPurposes()) {
            OpenAIFile createdDir = OpenAIFile.builder()
                    .id("dir-" + purpose)
                    .filename(dirName)
                    .spaceCode(spaceCode)
                    .purpose(purpose)
                    .build();

            when(fileUniquenessLock.executeWithLock(eq(spaceCode), eq(ancestorId), eq(dirName), anyLong(), any()))
                    .thenAnswer(invocation -> {
                        Supplier<?> supplier = invocation.getArgument(4);
                        return supplier.get();
                    });
            when(fileService.exists(spaceCode, ancestorId, dirName)).thenReturn(false);
            when(fileService.mkdir(dirName, ancestorId, null, purpose)).thenReturn(createdDir);

            String body = "{\"name\":\"" + dirName + "\",\"ancestor_id\":\"" + ancestorId + "\",\"purpose\":\"" + purpose + "\"}";

            BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
            mockMvc.perform(post("/v1/files/mkdir")
                    .header("X-BELLA-SPACE-CODE", spaceCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("dir-" + purpose))
                    .andExpect(jsonPath("$.filename").value(dirName))
                    .andExpect(jsonPath("$.space_code").value(spaceCode));
        }
    }
}

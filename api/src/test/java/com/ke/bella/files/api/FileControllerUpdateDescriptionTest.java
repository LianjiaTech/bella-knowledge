package com.ke.bella.files.api;

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
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.Scope;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FileControllerUpdateDescriptionTest {

    private MockMvc mockMvc;
    private FileService fileService;
    private FileUniquenessLock fileUniquenessLock;
    private FileController fileController;

    @Before
    public void setup() {
        fileService = Mockito.mock(FileService.class);
        fileUniquenessLock = Mockito.mock(FileUniquenessLock.class);
        fileController = new FileController();

        ReflectionTestUtils.setField(fileController, "fileService", fileService);
        ReflectionTestUtils.setField(fileController, "fl", fileUniquenessLock);

        mockMvc = MockMvcBuilders
            .standaloneSetup(fileController)
            .setControllerAdvice(new FileApiResponseAdvice())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
            .build();
    }

    @Test
    public void updateDescription_Success() throws Exception {
        // Given
        String fileId = "test-file-id";
        String description = "Updated description";

        OpenAIFile existingFile = OpenAIFile.builder()
            .id(fileId)
            .filename("test.txt")
            .description("Old description")
            .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
            .description(description)
            .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(false), eq(Scope.DESCRIPTION)))
            .thenReturn(updatedFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"" + description + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(fileId))
            .andExpect(jsonPath("$.description").value(description));

        // Verify service calls
        verify(fileService).getFile(fileId);
        verify(fileService).updateFile(any(FileOps.class), eq(false), eq(Scope.DESCRIPTION));
    }

    @Test
    public void updateDescription_FileNotFound() throws Exception {
        // Given
        String fileId = "non-existent-file";
        String description = "New description";

        when(fileService.getFile(fileId)).thenReturn(null);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"" + description + "\"}"))
            .andExpect(status().isNotFound()) // FileNotFoundException会返回404
            .andExpect(jsonPath("$.error.message").exists());

        verify(fileService).getFile(fileId);
    }

    @Test
    public void updateDescription_EmptyFileId() throws Exception {
        // Given
        String description = "New description";

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", "")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"" + description + "\"}"))
            .andExpect(status().isMethodNotAllowed()); // 空路径参数会导致405 Method Not Allowed
    }

    @Test
    public void updateDescription_NullRequestBody() throws Exception {
        // Given
        String fileId = "test-file-id";

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError()); // 缺少请求体会导致500
    }

    @Test
    public void updateDescription_NullDescription() throws Exception {
        // Given
        String fileId = "test-file-id";

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    public void updateDescription_EmptyDescription() throws Exception {
        // Given
        String fileId = "test-file-id";
        String description = "";

        OpenAIFile existingFile = OpenAIFile.builder()
            .id(fileId)
            .filename("test.txt")
            .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
            .description(description)
            .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(false), eq(Scope.DESCRIPTION)))
            .thenReturn(updatedFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value(""));
    }

    @Test
    public void updateDescription_DescriptionTooLong() throws Exception {
        // Given
        String fileId = "test-file-id";
        String longDescription = new String(new char[257]).replace('\0', 'x'); // 257字符

        OpenAIFile existingFile = OpenAIFile.builder()
            .id(fileId)
            .filename("test.txt")
            .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"" + longDescription + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    public void updateDescription_MaxLengthDescription() throws Exception {
        // Given
        String fileId = "test-file-id";
        String maxDescription = new String(new char[256]).replace('\0', 'x'); // 256字符

        OpenAIFile existingFile = OpenAIFile.builder()
            .id(fileId)
            .filename("test.txt")
            .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
            .description(maxDescription)
            .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(false), eq(Scope.DESCRIPTION)))
            .thenReturn(updatedFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"" + maxDescription + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value(maxDescription));
    }

    @Test
    public void updateDescription_WithSpecialCharacters() throws Exception {
        // Given
        String fileId = "test-file-id";
        String specialDescription = "测试描述 with special chars @#$%";

        OpenAIFile existingFile = OpenAIFile.builder()
            .id(fileId)
            .filename("test.txt")
            .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
            .description(specialDescription)
            .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(false), eq(Scope.DESCRIPTION)))
            .thenReturn(updatedFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"" + specialDescription + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value(specialDescription));
    }

    @Test
    public void updateDescription_ServiceThrowsException() throws Exception {
        // Given
        String fileId = "test-file-id";
        String description = "New description";

        OpenAIFile existingFile = OpenAIFile.builder()
            .id(fileId)
            .filename("test.txt")
            .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(false), eq(Scope.DESCRIPTION)))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"" + description + "\"}"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    public void updateDescription_VerifyFileOpsParameters() throws Exception {
        // Given
        String fileId = "test-file-id";
        String description = "Test description";

        OpenAIFile existingFile = OpenAIFile.builder()
            .id(fileId)
            .filename("test.txt")
            .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
            .description(description)
            .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(false), eq(Scope.DESCRIPTION)))
            .thenReturn(updatedFile);

        // When
        mockMvc.perform(put("/v1/files/{fileId}/description", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"" + description + "\"}"))
            .andExpect(status().isOk());

        // Then - 验证传递给service的FileOps参数
        verify(fileService).updateFile(argThat(ops ->
            ops.getFileId().equals(fileId) &&
                ops.getDescription().equals(description)
        ), eq(false), eq(Scope.DESCRIPTION));
    }
}

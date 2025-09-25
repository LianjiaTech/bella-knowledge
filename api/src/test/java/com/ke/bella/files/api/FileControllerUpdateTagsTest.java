package com.ke.bella.files.api;

import java.util.Arrays;
import java.util.Collections;
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

public class FileControllerUpdateTagsTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @Before
    public void setup() {
        fileService = Mockito.mock(FileService.class);
        FileUniquenessLock fileUniquenessLock = Mockito.mock(FileUniquenessLock.class);
        FileController fileController = new FileController();

        ReflectionTestUtils.setField(fileController, "fileService", fileService);
        ReflectionTestUtils.setField(fileController, "fl", fileUniquenessLock);

        mockMvc = MockMvcBuilders
                .standaloneSetup(fileController)
                .setControllerAdvice(new FileApiResponseAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @Test
    public void updateTags_Success() throws Exception {
        // Given
        String fileId = "test-file-id";
        String tagsJson = "[\"重要\", \"项目资料\", \"2024\"]";

        OpenAIFile existingFile = OpenAIFile.builder()
                .id(fileId)
                .filename("test.txt")
                .tags(Arrays.asList("旧标签"))
                .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
                .tags(Arrays.asList("重要", "项目资料", "2024"))
                .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.TAGS)))
                .thenReturn(updatedFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"tags\": %s}", tagsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId))
                .andExpect(jsonPath("$.tags[0]").value("重要"))
                .andExpect(jsonPath("$.tags[1]").value("项目资料"))
                .andExpect(jsonPath("$.tags[2]").value("2024"));

        // Verify service calls
        verify(fileService).getFile(fileId);
        verify(fileService).updateFile(any(FileOps.class), eq(true), eq(Scope.TAGS));
    }

    @Test
    public void updateTags_FileNotFound() throws Exception {
        // Given
        String fileId = "non-existent-file";
        String tagsJson = "[\"测试标签\"]";

        when(fileService.getFile(fileId)).thenReturn(null);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"tags\": %s}", tagsJson)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").exists());

        verify(fileService).getFile(fileId);
    }

    @Test
    public void updateTags_EmptyFileId() throws Exception {
        // Given
        String tagsJson = "[\"标签\"]";

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", "")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"tags\": %s}", tagsJson)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    public void updateTags_NullRequestBody() throws Exception {
        // Given
        String fileId = "test-file-id";

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void updateTags_NullTags() throws Exception {
        // Given
        String fileId = "test-file-id";

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    public void updateTags_EmptyTagsList() throws Exception {
        // Given
        String fileId = "test-file-id";

        OpenAIFile existingFile = OpenAIFile.builder()
                .id(fileId)
                .filename("test.txt")
                .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
                .tags(Collections.emptyList())
                .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.TAGS)))
                .thenReturn(updatedFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\": []}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    public void updateTags_TagsTooLong() throws Exception {
        // Given
        String fileId = "test-file-id";
        // 创建总长度超过512字符的标签列表
        String longTag = new String(new char[300]).replace('\0', 'x');
        String tagsJson = "[\"" + longTag + "\", \"" + longTag + "\"]";

        OpenAIFile existingFile = OpenAIFile.builder()
                .id(fileId)
                .filename("test.txt")
                .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"tags\": %s}", tagsJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("tags total length cannot exceed 512 characters"));
    }

    @Test
    public void updateTags_MaxLengthTags() throws Exception {
        // Given
        String fileId = "test-file-id";
        // 创建按JSON长度计算刚好512字符的标签列表（253+252+7=512）
        String tag1 = new String(new char[253]).replace('\0', 'a');
        String tag2 = new String(new char[252]).replace('\0', 'b');
        String tagsJson = "[\"" + tag1 + "\", \"" + tag2 + "\"]";

        OpenAIFile existingFile = OpenAIFile.builder()
                .id(fileId)
                .filename("test.txt")
                .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
                .tags(Arrays.asList(tag1, tag2))
                .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.TAGS)))
                .thenReturn(updatedFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"tags\": %s}", tagsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value(tag1))
                .andExpect(jsonPath("$.tags[1]").value(tag2));
    }

    @Test
    public void updateTags_WithSpecialCharacters() throws Exception {
        // Given
        String fileId = "test-file-id";
        String tagsJson = "[\"重要文档\", \"@priority\", \"#project-2024\", \"🔥热门\"]";

        OpenAIFile existingFile = OpenAIFile.builder()
                .id(fileId)
                .filename("test.txt")
                .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
                .tags(Arrays.asList("重要文档", "@priority", "#project-2024", "🔥热门"))
                .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.TAGS)))
                .thenReturn(updatedFile);

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"tags\": %s}", tagsJson)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("重要文档"))
                .andExpect(jsonPath("$.tags[1]").value("@priority"))
                .andExpect(jsonPath("$.tags[2]").value("#project-2024"))
                .andExpect(jsonPath("$.tags[3]").value("🔥热门"));
    }

    @Test
    public void updateTags_ServiceThrowsException() throws Exception {
        // Given
        String fileId = "test-file-id";
        String tagsJson = "[\"测试标签\"]";

        OpenAIFile existingFile = OpenAIFile.builder()
                .id(fileId)
                .filename("test.txt")
                .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.TAGS)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"tags\": %s}", tagsJson)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void updateTags_VerifyFileOpsParameters() throws Exception {
        // Given
        String fileId = "test-file-id";
        String tagsJson = "[\"测试标签1\", \"测试标签2\"]";

        OpenAIFile existingFile = OpenAIFile.builder()
                .id(fileId)
                .filename("test.txt")
                .build();

        OpenAIFile updatedFile = existingFile.toBuilder()
                .tags(Arrays.asList("测试标签1", "测试标签2"))
                .build();

        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.TAGS)))
                .thenReturn(updatedFile);

        // When
        mockMvc.perform(put("/v1/files/{fileId}/tags", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"tags\": %s}", tagsJson)))
                .andExpect(status().isOk());

        // Then - 验证传递给service的FileOps参数
        verify(fileService).updateFile(argThat(ops -> ops.getFileId().equals(fileId) &&
                ops.getTags() != null &&
                ops.getTags().size() == 2 &&
                ops.getTags().contains("测试标签1") &&
                ops.getTags().contains("测试标签2")), eq(true), eq(Scope.TAGS));
    }
}

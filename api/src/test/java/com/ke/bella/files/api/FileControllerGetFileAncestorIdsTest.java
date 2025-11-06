package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;

public class FileControllerGetFileAncestorIdsTest {

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
    public void getFileAncestorIds_Success_WithMultipleFiles() throws Exception {
        // Given
        Map<String, List<String>> expectedResult = new HashMap<>();
        expectedResult.put("file-2509301028080033000005-550887035",
                Arrays.asList("file-2509301028080033000004-550887035-d"));
        expectedResult.put("file-2509301028090033000009-550887035",
                Arrays.asList("file-2509301028080033000004-550887035-d",
                        "file-2509301028080033000006-550887035-d",
                        "file-2509301028080033000008-550887035-d"));
        expectedResult.put("file-2509301028110033000024-550887035-d",
                Arrays.asList("file-2509301028110033000022-550887035-d"));
        expectedResult.put("file-2509301028070033000001-550887035",
                Arrays.asList());
        expectedResult.put("file-2509301028110033000022-550887035-d",
                Arrays.asList());

        when(fileService.getFileAncestorIds(anyString(), anyList()))
                .thenReturn(expectedResult);

        // When & Then
        mockMvc.perform(post("/v1/files/ancestor-ids")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"550887035\",\n" +
                        "  \"file_ids\": [\n" +
                        "    \"file-2509301028080033000005-550887035\",\n" +
                        "    \"file-2509301028090033000009-550887035\",\n" +
                        "    \"file-2509301028110033000024-550887035-d\",\n" +
                        "    \"file-2509301028070033000001-550887035\",\n" +
                        "    \"file-2509301028110033000022-550887035-d\"\n" +
                        "  ]\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['file-2509301028080033000005-550887035'][0]")
                        .value("file-2509301028080033000004-550887035-d"))
                .andExpect(jsonPath("$.['file-2509301028090033000009-550887035'][0]")
                        .value("file-2509301028080033000004-550887035-d"))
                .andExpect(jsonPath("$.['file-2509301028090033000009-550887035'][1]")
                        .value("file-2509301028080033000006-550887035-d"))
                .andExpect(jsonPath("$.['file-2509301028090033000009-550887035'][2]")
                        .value("file-2509301028080033000008-550887035-d"))
                .andExpect(jsonPath("$.['file-2509301028110033000024-550887035-d'][0]")
                        .value("file-2509301028110033000022-550887035-d"))
                .andExpect(jsonPath("$.['file-2509301028070033000001-550887035']").isEmpty())
                .andExpect(jsonPath("$.['file-2509301028110033000022-550887035-d']").isEmpty());

        verify(fileService).getFileAncestorIds("550887035", Arrays.asList(
                "file-2509301028080033000005-550887035",
                "file-2509301028090033000009-550887035",
                "file-2509301028110033000024-550887035-d",
                "file-2509301028070033000001-550887035",
                "file-2509301028110033000022-550887035-d"));
    }

    @Test
    public void getFileAncestorIds_Success_WithRootFile() throws Exception {
        // Given - 根路径文件返回空数组
        Map<String, List<String>> expectedResult = new HashMap<>();
        expectedResult.put("file-root-001", Arrays.asList());

        when(fileService.getFileAncestorIds(anyString(), anyList()))
                .thenReturn(expectedResult);

        // When & Then
        mockMvc.perform(post("/v1/files/ancestor-ids")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"test-space\",\n" +
                        "  \"file_ids\": [\"file-root-001\"]\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['file-root-001']").isEmpty());
    }

    @Test
    public void getFileAncestorIds_Fail_MissingSpaceCode() throws Exception {
        // When & Then
        mockMvc.perform(post("/v1/files/ancestor-ids")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"file_ids\": [\"file-001\"]\n" +
                        "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getFileAncestorIds_Fail_NullFileIds() throws Exception {
        // When & Then
        mockMvc.perform(post("/v1/files/ancestor-ids")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"test-space\",\n" +
                        "  \"file_ids\": null\n" +
                        "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getFileAncestorIds_Fail_EmptyFileIds() throws Exception {
        // When & Then
        mockMvc.perform(post("/v1/files/ancestor-ids")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"test-space\",\n" +
                        "  \"file_ids\": []\n" +
                        "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getFileAncestorIds_Fail_TooManyFileIds() throws Exception {
        // Given - 超过1000个文件ID
        StringBuilder fileIdsJson = new StringBuilder("[");
        for (int i = 0; i < 1001; i++) {
            if(i > 0)
                fileIdsJson.append(",");
            fileIdsJson.append("\"file-").append(i).append("\"");
        }
        fileIdsJson.append("]");

        // When & Then
        mockMvc.perform(post("/v1/files/ancestor-ids")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"test-space\",\n" +
                        "  \"file_ids\": " + fileIdsJson + "\n" +
                        "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void getFileAncestorIds_Fail_NullRequestBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/v1/files/ancestor-ids")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isInternalServerError());
    }
}

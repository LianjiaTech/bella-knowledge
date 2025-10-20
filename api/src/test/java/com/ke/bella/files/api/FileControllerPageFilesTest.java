package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

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
import com.ke.bella.files.db.repo.Page;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;

public class FileControllerPageFilesTest {

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
    public void pageFiles_Success_WithSpaceCode() throws Exception {
        // Given
        int page = 1;
        int pageSize = 2;
        int total = 3;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("file-1").filename("a.txt").isDir(false).url("u1").build(),
                OpenAIFile.builder().id("file-2").filename("b.txt").isDir(true).build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(total).list(data));

        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 2,\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.total").value(total))
                .andExpect(jsonPath("$.limit").value(pageSize))
                .andExpect(jsonPath("$.has_more").value(true))
                .andExpect(jsonPath("$.data[0].id").value("file-1"))
                .andExpect(jsonPath("$.data[1].id").value("file-2"));
    }

    @Test
    public void pageFiles_Success_WithAncestorId() throws Exception {
        // Given
        int page = 2;
        int pageSize = 1;
        int total = 2;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("file-9").filename("c.txt").isDir(false).build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(total).list(data));

        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"ancestor_id\": \"dir-1\",\n" +
                        "  \"page\": 2,\n" +
                        "  \"page_size\": 1,\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"order\": \"ASC\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.limit").value(pageSize))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.data[0].id").value("file-9"));
    }

    @Test
    public void pageFiles_NullRequestBody() throws Exception {
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    public void pageFiles_BothSpaceCodeAndAncestorIdProvided() throws Exception {
        int page = 1;
        int pageSize = 10;
        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(0).list(Arrays.asList()));

        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"ancestor_id\": \"dir-1\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.limit").value(pageSize))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    public void pageFiles_InvalidPage() throws Exception {
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"s\",\n" +
                        "  \"page\": 0,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("page must be greater than 0"));
    }

    @Test
    public void pageFiles_InvalidPageSizeZero() throws Exception {
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"s\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 0,\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("page_size must be greater than 0"));
    }

    @Test
    public void pageFiles_InvalidPageSizeTooLarge() throws Exception {
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"s\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 101,\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("page_size cannot exceed 100"));
    }

    @Test
    public void pageFiles_InvalidOrder() throws Exception {
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"s\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"order\": \"xxx\"\n" +
                        "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("order must be 'desc' or 'asc', but got: xxx"));
    }

    @Test
    public void pageFiles_VerifyOpsParameters() throws Exception {
        // Given
        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(1, 10).total(0).list(Arrays.asList()));

        // When
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"sc\",\n" +
                        "  \"ancestor_id\": null,\n" +
                        "  \"purpose\": \"vision\",\n" +
                        "  \"tags\": [\"t1\", \"t2\"],\n" +
                        "  \"cities\": [\"c1\"],\n" +
                        "  \"filename\": \"x\",\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"cuid\": 1,\n" +
                        "  \"muid\": 2,\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk());

        // Then
        verify(fileService).pageFiles(argThat(ops -> "sc".equals(ops.getSpaceCode()) &&
                ops.getAncestorId() == null &&
                "vision".equals(ops.getPurpose()) &&
                ops.getTags() != null && ops.getTags().size() == 2 &&
                ops.getCities() != null && ops.getCities().size() == 1 &&
                "x".equals(ops.getFilename()) &&
                "file".equals(ops.getType()) &&
                ops.getCuid() == 1L &&
                ops.getMuid() == 2L &&
                ops.getPage() == 1 &&
                ops.getPageSize() == 10 &&
                "desc".equalsIgnoreCase(ops.getOrder())));
    }
}

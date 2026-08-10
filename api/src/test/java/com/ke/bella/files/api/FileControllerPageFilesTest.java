package com.ke.bella.files.api;

import static com.ke.bella.files.api.FileControllerTestFixture.stubDirectory;

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
import com.ke.bella.files.enums.NodeType;
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

        stubDirectory(fileService, "dir-1");

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

    // ========== 新增测试：type 参数可选和校验 ==========

    @Test
    public void pageFiles_MissingType_Success() throws Exception {
        // Given: 不传 type 参数，应该返回目录、文件和资源节点
        int page = 1;
        int pageSize = 10;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("dir-1").filename("folder1").isDir(true)
                        .nodeType(NodeType.DIRECTORY.getValue()).build(),
                OpenAIFile.builder().id("file-1").filename("doc.txt").isDir(false)
                        .nodeType(NodeType.FILE.getValue()).build(),
                OpenAIFile.builder().id("resource-1").filename("dataset").isDir(false)
                        .nodeType(NodeType.RESOURCE.getValue()).resourceId("dataset:1").build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(3).list(data));

        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].node_type").value(NodeType.DIRECTORY.getValue()))
                .andExpect(jsonPath("$.data[1].node_type").value(NodeType.FILE.getValue()))
                .andExpect(jsonPath("$.data[2].node_type").value(NodeType.RESOURCE.getValue()))
                .andExpect(jsonPath("$.data[2].resource_id").value("dataset:1"));

        // 验证 type 参数为 null
        verify(fileService).pageFiles(argThat(ops -> ops.getType() == null && ops.getPurpose() == null));
    }

    @Test
    public void pageFiles_EmptyStringType_BadRequest() throws Exception {
        // Given: type 为空字符串，应该报错
        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"type\": \"\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("type must be 'dir', 'file' or 'resource', but got: "));
    }

    @Test
    public void pageFiles_InvalidType_BadRequest() throws Exception {
        // Given: type 为无效值，应该报错
        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"type\": \"folder\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("type must be 'dir', 'file' or 'resource', but got: folder"));
    }

    @Test
    public void pageFiles_TypeDir_Success() throws Exception {
        // Given: type 为 dir，应该只返回目录
        int page = 1;
        int pageSize = 10;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("dir-1").filename("folder1").isDir(true)
                        .nodeType(NodeType.DIRECTORY.getValue()).build(),
                OpenAIFile.builder().id("dir-2").filename("folder2").isDir(true)
                        .nodeType(NodeType.DIRECTORY.getValue()).build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(2).list(data));

        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"type\": \"dir\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].node_type").value(NodeType.DIRECTORY.getValue()))
                .andExpect(jsonPath("$.data[1].node_type").value(NodeType.DIRECTORY.getValue()));

        // 验证 type 参数为 "dir"
        verify(fileService).pageFiles(argThat(ops -> "dir".equals(ops.getType())));
    }

    @Test
    public void pageFiles_TypeFileWithoutPurpose_Success() throws Exception {
        // Given: type 为 file 但不传 purpose，应该返回所有文件
        int page = 1;
        int pageSize = 10;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("file-1").filename("doc.txt").isDir(false)
                        .nodeType(NodeType.FILE.getValue()).purpose("assistants").build(),
                OpenAIFile.builder().id("file-2").filename("img.jpg").isDir(false)
                        .nodeType(NodeType.FILE.getValue()).purpose("vision").build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(2).list(data));

        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].node_type").value(NodeType.FILE.getValue()))
                .andExpect(jsonPath("$.data[1].node_type").value(NodeType.FILE.getValue()));

        // 验证 type 为 file 且 purpose 为 null
        verify(fileService).pageFiles(argThat(ops -> "file".equals(ops.getType()) && ops.getPurpose() == null));
    }

    @Test
    public void pageFiles_TypeResource_Success() throws Exception {
        int page = 1;
        int pageSize = 10;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("resource-1").filename("dataset-1").isDir(false)
                        .nodeType(NodeType.RESOURCE.getValue()).resourceId("dataset:1").build(),
                OpenAIFile.builder().id("resource-2").filename("workflow-2").isDir(false)
                        .nodeType(NodeType.RESOURCE.getValue()).resourceId("workflow:2").build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(2).list(data));

        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"type\": \"resource\",\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].node_type").value(NodeType.RESOURCE.getValue()))
                .andExpect(jsonPath("$.data[0].resource_id").value("dataset:1"))
                .andExpect(jsonPath("$.data[1].node_type").value(NodeType.RESOURCE.getValue()))
                .andExpect(jsonPath("$.data[1].resource_id").value("workflow:2"));

        verify(fileService).pageFiles(argThat(ops -> "resource".equals(ops.getType()) && ops.getPurpose() == null));
    }

    // ========== 新增测试：purpose 和 type 的组合场景 ==========

    @Test
    public void pageFiles_PurposeWithoutType_Success() throws Exception {
        // Given: 传 purpose 但不传 type，应该返回所有 dir + purpose 匹配的 file
        int page = 1;
        int pageSize = 10;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("dir-1").filename("folder1").isDir(true).purpose(null).build(),
                OpenAIFile.builder().id("file-1").filename("vision.jpg").isDir(false).purpose("vision").build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(2).list(data));

        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"purpose\": \"vision\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("dir-1"))
                .andExpect(jsonPath("$.data[1].id").value("file-1"))
                .andExpect(jsonPath("$.data[1].purpose").value("vision"));

        // 验证 purpose 有值但 type 为 null
        verify(fileService).pageFiles(argThat(ops -> "vision".equals(ops.getPurpose()) && ops.getType() == null));
    }

    @Test
    public void pageFiles_PurposeWithTypeFile_Success() throws Exception {
        // Given: 传 purpose 且 type 为 file，应该只返回 purpose 匹配的 file
        int page = 1;
        int pageSize = 10;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("file-1").filename("vision.jpg").isDir(false).purpose("vision").build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(1).list(data));

        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"purpose\": \"vision\",\n" +
                        "  \"type\": \"file\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].purpose").value("vision"));

        // 验证 purpose 和 type 都有值
        verify(fileService).pageFiles(argThat(ops -> "vision".equals(ops.getPurpose()) && "file".equals(ops.getType())));
    }

    @Test
    public void pageFiles_PurposeWithTypeDir_Success() throws Exception {
        // Given: 传 purpose 且 type 为 dir，应该只返回 dir（purpose 对 dir 无实际过滤效果）
        int page = 1;
        int pageSize = 10;
        List<OpenAIFile> data = Arrays.asList(
                OpenAIFile.builder().id("dir-1").filename("folder1").isDir(true).purpose(null).build());

        when(fileService.pageFiles(any(PageFileOps.class)))
                .thenReturn(Page.<OpenAIFile>from(page, pageSize).total(1).list(data));

        // When & Then
        mockMvc.perform(post("/v1/files/page")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\n" +
                        "  \"space_code\": \"space-1\",\n" +
                        "  \"purpose\": \"vision\",\n" +
                        "  \"type\": \"dir\",\n" +
                        "  \"page\": 1,\n" +
                        "  \"page_size\": 10,\n" +
                        "  \"order\": \"desc\"\n" +
                        "}"))
                .andExpect(status().isOk());

        // 验证 purpose 和 type 都有值
        verify(fileService).pageFiles(argThat(ops -> "vision".equals(ops.getPurpose()) && "dir".equals(ops.getType())));
    }
}

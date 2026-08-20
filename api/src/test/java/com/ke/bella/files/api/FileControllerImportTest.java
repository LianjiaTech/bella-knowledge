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

import org.junit.After;
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
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileControllerImportTest {
    private static final String SPACE_CODE = "sp-a";

    private MockMvc mockMvc;
    private FileService fileService;
    private FileUniquenessLock fileUniquenessLock;

    @Before
    public void setUp() {
        fileService = Mockito.mock(FileService.class);
        fileUniquenessLock = Mockito.mock(FileUniquenessLock.class);
        FileController controller = new FileController();
        ReflectionTestUtils.setField(controller, "fileService", fileService);
        ReflectionTestUtils.setField(controller, "fl", fileUniquenessLock);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FileApiResponseAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(SPACE_CODE).build());
    }

    @After
    public void tearDown() {
        BellaContext.clearAll();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder importRequest(String body) {
        return post("/v1/files/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    @Test
    public void importCreatesFileWithoutUploadingContent() throws Exception {
        String path = "import/a.txt";
        when(fileService.bucketForPurpose("assistants")).thenReturn("private-bucket");
        when(fileService.objectExists("private-bucket", path)).thenReturn(true);
        when(fileService.objectSize("private-bucket", path)).thenReturn(11L);
        when(fileService.exists(SPACE_CODE, null, "a.txt")).thenReturn(false);
        when(fileUniquenessLock.executeWithLock(eq(SPACE_CODE), eq(null), eq("a.txt"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(fileService.importObject(eq(SPACE_CODE), eq("private-bucket"), eq(path), eq(11L), eq("a.txt"), eq("assistants"),
                eq(null), eq("text/plain"), eq("text"), eq("txt"), eq(null), eq(""), eq(null), eq(null)))
                        .thenReturn(OpenAIFile.builder().id("file-1").filename("a.txt").bytes(11L).build());

        mockMvc.perform(importRequest(
                "{\"path\":\"import/a.txt\",\"filename\":\"a.txt\",\"purpose\":\"assistants\",\"mime_type\":\"text/plain\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("file-1"))
                .andExpect(jsonPath("$.bytes").value(11));

        verify(fileService).importObject(eq(SPACE_CODE), eq("private-bucket"), eq(path), eq(11L), eq("a.txt"), eq("assistants"),
                eq(null), eq("text/plain"), eq("text"), eq("txt"), eq(null), eq(""), eq(null), eq(null));
    }

    @Test
    public void importUsesSpaceCodeFromBody() throws Exception {
        String path = "import/a.txt";
        when(fileService.bucketForPurpose("assistants")).thenReturn("private-bucket");
        when(fileService.objectExists("private-bucket", path)).thenReturn(true);
        when(fileService.objectSize("private-bucket", path)).thenReturn(11L);
        when(fileService.exists("sp-b", null, "a.txt")).thenReturn(false);
        when(fileUniquenessLock.executeWithLock(eq("sp-b"), eq(null), eq("a.txt"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(fileService.importObject(eq("sp-b"), eq("private-bucket"), eq(path), eq(11L), eq("a.txt"), eq("assistants"),
                eq(null), eq(""), eq(""), eq("txt"), eq(null), eq(""), eq(null), eq(null)))
                        .thenReturn(OpenAIFile.builder().id("file-3").filename("a.txt").bytes(11L).build());

        mockMvc.perform(importRequest(
                "{\"path\":\"import/a.txt\",\"filename\":\"a.txt\",\"purpose\":\"assistants\",\"space_code\":\"sp-b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("file-3"));
    }

    @Test
    public void importRejectsMissingObjectBeforeLocking() throws Exception {
        when(fileService.bucketForPurpose("assistants")).thenReturn("private-bucket");
        when(fileService.objectExists("private-bucket", "import/missing.txt")).thenReturn(false);

        mockMvc.perform(importRequest(
                "{\"path\":\"import/missing.txt\",\"filename\":\"missing.txt\",\"purpose\":\"assistants\"}"))
                .andExpect(status().isBadRequest());

        verify(fileUniquenessLock, never()).executeWithLock(any(), any(), any(), anyLong(), any());
        verify(fileService, never()).importObject(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any());
    }

    @Test
    public void importAllowsWhitelistedExternalBucketWithoutImportPrefix() throws Exception {
        String path = "legacy/2024/a.txt";
        when(fileService.isAllowedImportSource("biz-bucket")).thenReturn(true);
        when(fileService.objectExists("biz-bucket", path)).thenReturn(true);
        when(fileService.objectSize("biz-bucket", path)).thenReturn(11L);
        when(fileService.exists(SPACE_CODE, null, "a.txt")).thenReturn(false);
        when(fileUniquenessLock.executeWithLock(eq(SPACE_CODE), eq(null), eq("a.txt"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(fileService.importObject(eq(SPACE_CODE), eq("biz-bucket"), eq(path), eq(11L), eq("a.txt"), eq("assistants"),
                eq(null), eq(""), eq(""), eq("txt"), eq(null), eq(""), eq(null), eq(null)))
                        .thenReturn(OpenAIFile.builder().id("file-2").filename("a.txt").bytes(11L).build());

        mockMvc.perform(importRequest(
                "{\"path\":\"legacy/2024/a.txt\",\"filename\":\"a.txt\",\"bucket\":\"biz-bucket\",\"purpose\":\"assistants\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("file-2"));

        verify(fileService, never()).bucketForPurpose(any());
    }

    @Test
    public void importRejectsBucketOutsideAllowlist() throws Exception {
        mockMvc.perform(importRequest(
                "{\"path\":\"legacy/a.txt\",\"filename\":\"a.txt\",\"bucket\":\"unknown-bucket\",\"purpose\":\"assistants\"}"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).objectExists(any(), any());
    }

    @Test
    public void importRejectsTraversalInExternalBucket() throws Exception {
        when(fileService.isAllowedImportSource("biz-bucket")).thenReturn(true);

        mockMvc.perform(importRequest(
                "{\"path\":\"legacy/../secret.txt\",\"filename\":\"secret.txt\",\"bucket\":\"biz-bucket\",\"purpose\":\"assistants\"}"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).objectExists(any(), any());
    }

    @Test
    public void importRejectsExistingFile() throws Exception {
        String path = "import/a.txt";
        when(fileService.bucketForPurpose("assistants")).thenReturn("private-bucket");
        when(fileService.objectExists("private-bucket", path)).thenReturn(true);
        when(fileService.objectSize("private-bucket", path)).thenReturn(11L);
        when(fileService.exists(SPACE_CODE, null, "a.txt")).thenReturn(true);
        when(fileUniquenessLock.executeWithLock(eq(SPACE_CODE), eq(null), eq("a.txt"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());

        mockMvc.perform(importRequest(
                "{\"path\":\"import/a.txt\",\"filename\":\"a.txt\",\"purpose\":\"assistants\"}"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).importObject(any(), any(), any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any());
    }

    @Test
    public void importRejectsPathOutsideImportPrefix() throws Exception {
        mockMvc.perform(importRequest(
                "{\"path\":\"assistants/file-1.txt\",\"filename\":\"file-1.txt\",\"purpose\":\"assistants\"}"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).objectExists(any(), any());
    }

    @Test
    public void importRejectsTraversalBeforeStorageAccess() throws Exception {
        mockMvc.perform(importRequest(
                "{\"path\":\"import/../outside.txt\",\"filename\":\"outside.txt\",\"purpose\":\"assistants\"}"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).objectExists(any(), any());
    }
}

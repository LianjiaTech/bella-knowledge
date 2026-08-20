package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.FileNodeCount;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileControllerResourceTest {
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
        mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new FileApiResponseAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-a").build());
    }

    @Test
    public void countNodesReturnsStableFields() throws Exception {
        FileNodeCount count = new FileNodeCount();
        count.setFileCount(2);
        count.setDirectoryCount(1);
        count.setResourceCount(3);
        when(fileService.countNodes("sp-a", null)).thenReturn(count);

        mockMvc.perform(get("/v1/files/count").param("space_code", "sp-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file_count").value(2))
                .andExpect(jsonPath("$.directory_count").value(1))
                .andExpect(jsonPath("$.resource_count").value(3));
    }

    @Test
    public void createResourceInDirectory() throws Exception {
        FileDB ancestor = new FileDB();
        ancestor.setFileId("file-parent-1-d");
        ancestor.setSpaceCode("sp-a");
        ancestor.setIsDir(1);
        ancestor.setNodeType(NodeType.DIRECTORY.getValue());
        when(fileService.getFile0("file-parent-1-d")).thenReturn(ancestor);
        when(fileUniquenessLock.executeWithLock(eq("sp-a"), eq("file-parent-1-d"), eq("Sales dataset"), anyLong(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(fileService.createResource("Sales dataset", "dataset:12345", "file-parent-1-d", "assistants"))
                .thenReturn(OpenAIFile.builder()
                        .id("file-resource-1")
                        .filename("Sales dataset")
                        .nodeType(NodeType.RESOURCE.getValue())
                        .resourceId("dataset:12345")
                        .purpose("assistants")
                        .isDir(false)
                        .bytes(0L)
                        .build());

        mockMvc.perform(post("/v1/files/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sales dataset\",\"resource_id\":\"dataset:12345\",\"ancestor_id\":\"file-parent-1-d\",\"purpose\":\"assistants\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node_type").value("resource"))
                .andExpect(jsonPath("$.resource_id").value("dataset:12345"))
                .andExpect(jsonPath("$.purpose").value("assistants"))
                .andExpect(jsonPath("$.is_dir").value(false));
    }

    @Test
    public void rejectInvalidResourceId() throws Exception {
        mockMvc.perform(post("/v1/files/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sales dataset\",\"resource_id\":\"dataset\"}"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).createResource(any(), any(), any(), any());
    }

    @Test
    public void rejectUnsupportedPurpose() throws Exception {
        mockMvc.perform(post("/v1/files/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sales dataset\",\"resource_id\":\"dataset:12345\",\"purpose\":\"unsupported\"}"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).createResource(any(), any(), any(), any());
    }

    @Test
    public void rejectResourceAsAncestor() throws Exception {
        FileDB ancestor = new FileDB();
        ancestor.setFileId("file-resource-1");
        ancestor.setSpaceCode("sp-a");
        ancestor.setIsDir(0);
        ancestor.setNodeType(NodeType.RESOURCE.getValue());
        when(fileService.getFile0("file-resource-1")).thenReturn(ancestor);

        mockMvc.perform(post("/v1/files/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Child\",\"resource_id\":\"dataset:2\",\"ancestor_id\":\"file-resource-1\"}"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).createResource(any(), any(), any(), any());
    }

    @Test
    public void contentUrlRejectsResourceBeforeStorageLookup() throws Exception {
        when(fileService.requireContentFile("file-resource-1"))
                .thenThrow(new IllegalArgumentException("node has no file content"));

        mockMvc.perform(get("/v1/files/file-resource-1/url"))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).getUrl(eq("file-resource-1"), anyLong());
    }
}

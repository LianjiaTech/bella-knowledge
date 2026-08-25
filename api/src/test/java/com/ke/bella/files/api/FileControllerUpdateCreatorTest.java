package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.ke.bella.files.api.interceptor.FileApiResponseAdvice;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;

public class FileControllerUpdateCreatorTest {
    private static final String FILE_ID = "file-test-1";
    private static final long CREATED_AT = 1704067200000L;

    private MockMvc mockMvc;
    private FileService fileService;

    @Before
    public void setup() {
        fileService = Mockito.mock(FileService.class);
        FileController fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "fileService", fileService);
        ReflectionTestUtils.setField(fileController, "fl", Mockito.mock(FileUniquenessLock.class));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new FileApiResponseAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    public void updateCreatorSuccess() throws Exception {
        OpenAIFile existing = OpenAIFile.builder().id(FILE_ID).filename("test.txt").version(3L).build();
        OpenAIFile updated = existing.toBuilder().cuid(42L).cuName("original creator").createdAt(CREATED_AT).build();
        LocalDateTime ctime = LocalDateTime.ofInstant(Instant.ofEpochMilli(CREATED_AT), ZoneId.systemDefault());
        when(fileService.getFile(FILE_ID)).thenReturn(existing);
        when(fileService.updateCreatorInfo(FILE_ID, 42L, "original creator", ctime)).thenReturn(updated);

        mockMvc.perform(put("/v1/files/{fileId}/creator", FILE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cuid\":42,\"cu_name\":\"original creator\",\"created_at\":" + CREATED_AT + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(FILE_ID))
                .andExpect(jsonPath("$.cuid").value(42L))
                .andExpect(jsonPath("$.cu_name").value("original creator"))
                .andExpect(jsonPath("$.created_at").value(CREATED_AT))
                .andExpect(jsonPath("$.version").value(3L));

        verify(fileService).updateCreatorInfo(FILE_ID, 42L, "original creator", ctime);
    }

    @Test
    public void updateCreatorFileNotFound() throws Exception {
        when(fileService.getFile(FILE_ID)).thenReturn(null);

        mockMvc.perform(put("/v1/files/{fileId}/creator", FILE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cuid\":42,\"cu_name\":\"creator\",\"created_at\":" + CREATED_AT + "}"))
                .andExpect(status().isNotFound());

        verify(fileService, never()).updateCreatorInfo(eq(FILE_ID), eq(42L), eq("creator"), Mockito.any(LocalDateTime.class));
    }

    @Test
    public void updateCreatorRejectsMissingBody() throws Exception {
        mockMvc.perform(put("/v1/files/{fileId}/creator", FILE_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(fileService, never()).getFile(FILE_ID);
    }

    @Test
    public void updateCreatorRejectsInvalidFieldsWithoutPartialUpdate() throws Exception {
        String[] invalidBodies = {
                "{\"cu_name\":\"creator\",\"created_at\":" + CREATED_AT + "}",
                "{\"cuid\":-1,\"cu_name\":\"creator\",\"created_at\":" + CREATED_AT + "}",
                "{\"cuid\":42,\"cu_name\":\" \",\"created_at\":" + CREATED_AT + "}",
                "{\"cuid\":42,\"cu_name\":\"creator\"}",
                "{\"cuid\":42,\"cu_name\":\"creator\",\"created_at\":-1}"
        };

        for (String body : invalidBodies) {
            mockMvc.perform(put("/v1/files/{fileId}/creator", FILE_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        verify(fileService, never()).getFile(FILE_ID);
        verify(fileService, never()).updateCreatorInfo(
                Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(), Mockito.any(LocalDateTime.class));
    }
}

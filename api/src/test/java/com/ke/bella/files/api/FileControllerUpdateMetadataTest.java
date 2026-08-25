package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

public class FileControllerUpdateMetadataTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @Before
    public void setup() {
        fileService = Mockito.mock(FileService.class);
        FileController fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "fileService", fileService);
        ReflectionTestUtils.setField(fileController, "fl", Mockito.mock(FileUniquenessLock.class));
        mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new FileApiResponseAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @Test
    public void updateMetadata_FileSuccess() throws Exception {
        assertSuccessfulUpdate("file-test-u", "file", false);
    }

    @Test
    public void updateMetadata_DirectorySuccess() throws Exception {
        assertSuccessfulUpdate("file-directory-u", "directory", true);
    }

    @Test
    public void updateMetadata_MissingMetadata() throws Exception {
        mockMvc.perform(put("/v1/files/{fileId}/metadata", "file-test-u")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("metadata is required"));

        verify(fileService, never()).getFile("file-test-u");
    }

    @Test
    public void updateMetadata_FileNotFound() throws Exception {
        when(fileService.getFile("file-missing-u")).thenReturn(null);

        mockMvc.perform(put("/v1/files/{fileId}/metadata", "file-missing-u")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"metadata\":\"{\\\"team\\\":\\\"search\\\"}\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").exists());

        verify(fileService).getFile("file-missing-u");
        verify(fileService, never()).updateFile(Mockito.any(FileOps.class), eq(false), eq(Scope.METADATA));
    }

    private void assertSuccessfulUpdate(String fileId, String nodeType, boolean directory) throws Exception {
        String oldMetadata = "{\"team\":\"old\"}";
        String newMetadata = "{\"team\":\"search\"}";
        OpenAIFile existingFile = OpenAIFile.builder()
                .id(fileId)
                .filename("test.txt")
                .metadata(oldMetadata)
                .nodeType(nodeType)
                .isDir(directory)
                .description("unchanged")
                .version(7L)
                .build();
        OpenAIFile updatedFile = existingFile.toBuilder().metadata(newMetadata).build();
        when(fileService.getFile(fileId)).thenReturn(existingFile);
        when(fileService.updateFile(argThat(op -> fileId.equals(op.getFileId())
                && newMetadata.equals(op.getMetadata())
                && op.getFilename() == null
                && op.getDescription() == null
                && op.getCities() == null
                && op.getTags() == null), eq(false), eq(Scope.METADATA)))
                        .thenReturn(updatedFile);

        mockMvc.perform(put("/v1/files/{fileId}/metadata", fileId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"metadata\":\"{\\\"team\\\":\\\"search\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId))
                .andExpect(jsonPath("$.metadata").value(newMetadata))
                .andExpect(jsonPath("$.filename").value("test.txt"))
                .andExpect(jsonPath("$.description").value("unchanged"))
                .andExpect(jsonPath("$.version").value(7));

        verify(fileService).getFile(fileId);
        verify(fileService).updateFile(argThat(op -> fileId.equals(op.getFileId())
                && newMetadata.equals(op.getMetadata())), eq(false), eq(Scope.METADATA));
    }
}

package com.ke.bella.files.api;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.files.api.interceptor.FileApiResponseAdvice;
import com.ke.bella.files.service.FileService;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FileControllerCountSingleTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @Before
    public void setUp() {
        fileService = Mockito.mock(FileService.class);

        FileController controller = new FileController();
        ReflectionTestUtils.setField(controller, "fileService", fileService);

        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(new FileApiResponseAdvice())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
            .build();
    }

    @Test
    public void count_withValidParams_returnsCount() throws Exception {
        String ancestorId = "dir-1";
        String type = "file";
        int expectedCount = 5;

        when(fileService.count(ancestorId, type)).thenReturn(expectedCount);

        mockMvc.perform(get("/v1/files/count")
                .param("ancestorId", ancestorId)
                .param("type", type))
            .andExpect(status().isOk())
            .andExpect(content().string("5"));

        verify(fileService, times(1)).count(ancestorId, type);
    }

    @Test
    public void count_withMissingType_returns500() throws Exception {
        String ancestorId = "dir-1";

        mockMvc.perform(get("/v1/files/count")
                .param("ancestorId", ancestorId))
            .andExpect(status().is5xxServerError());
    }
}

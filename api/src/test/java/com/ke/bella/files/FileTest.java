package com.ke.bella.files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileInputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.utils.JsonUtils;

@AutoConfigureMockMvc
public class FileTest extends AbstractTest {
    @Autowired
    private MockMvc mockMvc;

    private static final String API_KEY = "api_key";

    @Test
    public void testGetOpenAIFile() throws Exception {
        File file = new File("src/test/resources/upload_test.jpg");
        FileInputStream inputStream = new FileInputStream(file);
        MockMultipartFile mockFile = new MockMultipartFile("file", "upload_test.jpg", "image/jpeg", inputStream);

        MvcResult mvcResult = mockMvc
                .perform(multipart("/v1/files")
                        .file(mockFile)
                        .param("purpose", "vision")
                        .header("Authorization", "Bearer " + API_KEY))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpServletResponse response = mvcResult.getResponse();
        OpenAIFile fileUploaded = JsonUtils.fromJson(response.getContentAsString(), new TypeReference<OpenAIFile>() {
        });

        Assertions.assertNotNull(fileUploaded);
        Assertions.assertNotNull(fileUploaded.getId());

        String fileId = fileUploaded.getId();

        String url = String.format("/v1/files/%s", fileId);
        MvcResult fileRicherResult = mockMvc.perform(get(url)
                .header("Authorization", "Bearer " + API_KEY)
                .param("get_url", "true")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        OpenAIFile openaiFile = JsonUtils.fromJson(fileRicherResult.getResponse().getContentAsString(), new TypeReference<OpenAIFile>() {
        });

        Assertions.assertNotNull(openaiFile);
        Assertions.assertNotNull(openaiFile.getType());
        Assertions.assertNotNull(openaiFile.getMimeType());
        Assertions.assertNotNull(openaiFile.getUrl());

        String url2 = String.format("/v1/files/%s", fileId);
        MvcResult fileRicherResult2 = mockMvc.perform(get(url2)
                .header("Authorization", "Bearer " + API_KEY)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        OpenAIFile openaiFile2 = JsonUtils.fromJson(fileRicherResult2.getResponse().getContentAsString(), new TypeReference<OpenAIFile>() {
        });

        Assertions.assertNotNull(openaiFile2);
        Assertions.assertNotNull(openaiFile2.getType());
        Assertions.assertNotNull(openaiFile2.getMimeType());
        Assertions.assertNull(openaiFile2.getUrl());
    }
}

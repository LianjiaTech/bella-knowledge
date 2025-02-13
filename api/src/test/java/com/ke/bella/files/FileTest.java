package com.ke.bella.files;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

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
import com.ke.bella.files.protocol.FileUrl;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.utils.JsonUtils;

@AutoConfigureMockMvc
public class FileTest extends AbstractTest {
    @Autowired
    private MockMvc mockMvc;

    private static final String API_KEY = "api_key";

    @Test
    public void testGetOpenAIFile() throws Exception {
        OpenAIFile fileUploaded = uploadFile("upload_test.jpg", "vision", "image/jpeg");

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

    @Test
    public void getPreviewUrl() throws Exception {
        // 先从list接口获取两种purpose的文件
        OpenAIFile fileUploaded = uploadFile("doc_test.docx", "vision", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        Assertions.assertNotNull(fileUploaded);
        Assertions.assertNotNull(fileUploaded.getId());

        String fileId = fileUploaded.getId();

        FileUrl fileUrl = getPreviewUrl(fileId);

        Assertions.assertNotNull(fileUrl);
        Assertions.assertNotNull(fileUrl.getUrl());
    }

    @Test
    public void getPreviewUrlPrivate() throws Exception {
        // 先从list接口获取两种purpose的文件
        OpenAIFile fileUploaded = uploadFile("doc_test.docx", "assistants",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        Assertions.assertNotNull(fileUploaded);
        Assertions.assertNotNull(fileUploaded.getId());

        String fileId = fileUploaded.getId();

        FileUrl fileUrl = getPreviewUrl(fileId);

        Assertions.assertNotNull(fileUrl);
        Assertions.assertNotNull(fileUrl.getUrl());
    }

    @Test
    public void testUploadFileWithNoASCII() throws Exception {
        OpenAIFile fileUploaded = uploadFile("中文名用例.docx", "vision", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        Assertions.assertNotNull(fileUploaded);
        Assertions.assertNotNull(fileUploaded.getId());
    }

    @Test
    public void testListFilesFromDifferentSharding() throws Exception {
        MvcResult mvcResult = mockMvc
                .perform(post("/v1/files/list")
                        .content(
                                "{\"file_ids\":[\"file-2412161207120019000935-219272336\",\"file-2412161519030019005690-272783953\",\"file-2412161519030019005692-272783953\"]}")
                        .header("Authorization", "Bearer " + API_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpServletResponse response = mvcResult.getResponse();

        List<OpenAIFile> files = JsonUtils.fromJson(response.getContentAsString(), new TypeReference<List<OpenAIFile>>() {
        });
        Assertions.assertNotNull(files);
        Assertions.assertEquals(3, files.size());
    }

    private FileUrl getPreviewUrl(String fileId) throws Exception {
        String url = String.format("/v1/files/%s/preview_url", fileId);
        MvcResult fileRicherResult = mockMvc.perform(get(url)
                .header("Authorization", "Bearer " + API_KEY)
                .param("expires", "3600")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        return JsonUtils.fromJson(fileRicherResult.getResponse().getContentAsString(), new TypeReference<FileUrl>() {
        });
    }

    public OpenAIFile uploadFile(String filename, String purpose, String contentType) throws Exception {
        // 先从list接口获取两种purpose的文件
        File file = new File("src/test/resources/" + filename);
        FileInputStream inputStream = new FileInputStream(file);
        MockMultipartFile mockFile = new MockMultipartFile("file", filename,
                contentType, inputStream);

        MvcResult mvcResult = mockMvc
                .perform(multipart("/v1/files")
                        .file(mockFile)
                        .param("purpose", purpose)
                        .header("Authorization", "Bearer " + API_KEY))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpServletResponse response = mvcResult.getResponse();

        return JsonUtils.fromJson(response.getContentAsString(), new TypeReference<OpenAIFile>() {
        });
    }
}

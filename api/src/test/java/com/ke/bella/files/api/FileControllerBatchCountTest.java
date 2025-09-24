package com.ke.bella.files.api;

import static org.mockito.Mockito.times;
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
import com.ke.bella.files.protocol.FileCountInfo;
import com.ke.bella.files.service.FileService;

public class FileControllerBatchCountTest {

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
	public void batchCount_withValidRequest_returnsCountList() throws Exception {
		List<String> ancestorIds = Arrays.asList("dir-1", "dir-2", "dir-3");
		String type = "file";
		String spaceCode = "SPACE-001";

		List<FileCountInfo> expectedResult = Arrays.asList(
			new FileCountInfo("dir-1", 5),
			new FileCountInfo("dir-2", 3),
			new FileCountInfo("dir-3", 8)
		);

		when(fileService.batchCount(ancestorIds, type, spaceCode)).thenReturn(expectedResult);

		String requestBody = "{\n" +
			"  \"ancestorIds\": [\"dir-1\", \"dir-2\", \"dir-3\"],\n" +
			"  \"type\": \"file\",\n" +
			"  \"spaceCode\": \"SPACE-001\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/batch-count")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].ancestor_id").value("dir-1"))
			.andExpect(jsonPath("$[0].count").value(5))
			.andExpect(jsonPath("$[1].ancestor_id").value("dir-2"))
			.andExpect(jsonPath("$[1].count").value(3))
			.andExpect(jsonPath("$[2].ancestor_id").value("dir-3"))
			.andExpect(jsonPath("$[2].count").value(8));

		verify(fileService, times(1)).batchCount(ancestorIds, type, spaceCode);
	}

	@Test
	public void batchCount_withNullType_returnsCountList() throws Exception {
		List<String> ancestorIds = Arrays.asList("dir-1", "dir-2");
		String spaceCode = "SPACE-001";

		List<FileCountInfo> expectedResult = Arrays.asList(
			new FileCountInfo("dir-1", 12),
			new FileCountInfo("dir-2", 7)
		);

		when(fileService.batchCount(ancestorIds, null, spaceCode)).thenReturn(expectedResult);

		String requestBody = "{\n" +
			"  \"ancestorIds\": [\"dir-1\", \"dir-2\"],\n" +
			"  \"spaceCode\": \"SPACE-001\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/batch-count")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].ancestor_id").value("dir-1"))
			.andExpect(jsonPath("$[0].count").value(12))
			.andExpect(jsonPath("$[1].ancestor_id").value("dir-2"))
			.andExpect(jsonPath("$[1].count").value(7));

		verify(fileService, times(1)).batchCount(ancestorIds, null, spaceCode);
	}

	@Test
	public void batchCount_withNullRequest_returns400() throws Exception {
		mockMvc.perform(post("/v1/files/batch-count")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("ancestor_ids is required and cannot be empty")));
	}

	@Test
	public void batchCount_withEmptyAncestorIds_returns400() throws Exception {
		String requestBody = "{\n" +
			"  \"ancestorIds\": [],\n" +
			"  \"spaceCode\": \"SPACE-001\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/batch-count")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("ancestor_ids is required and cannot be empty")));
	}

	@Test
	public void batchCount_withTooManyAncestorIds_returns400() throws Exception {
		ObjectMapper om = new ObjectMapper();
		java.util.Map<String, Object> payload = new java.util.HashMap<>();
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 101; i++) {
			ids.add("dir-" + i);
		}
		payload.put("ancestorIds", ids);
		payload.put("spaceCode", "SPACE-001");
		String requestBody = om.writeValueAsString(payload);

		mockMvc.perform(post("/v1/files/batch-count")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("ancestor_ids size cannot exceed 100")));
	}

	@Test
	public void batchCount_withMissingSpaceCode_returns400() throws Exception {
		String requestBody = "{\n" +
			"  \"ancestorIds\": [\"dir-1\", \"dir-2\"]\n" +
			"}";

		mockMvc.perform(post("/v1/files/batch-count")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("space_code is required")));
	}

	@Test
	public void batchCount_withEmptySpaceCode_returns400() throws Exception {
		String requestBody = "{\n" +
			"  \"ancestorIds\": [\"dir-1\", \"dir-2\"],\n" +
			"  \"spaceCode\": \"\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/batch-count")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("space_code is required")));
	}
}

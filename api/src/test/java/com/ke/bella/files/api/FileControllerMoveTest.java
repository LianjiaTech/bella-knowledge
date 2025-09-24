package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ke.bella.files.api.interceptor.FileApiResponseAdvice;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 轻量单测：验证 /v1/files/move 的参数校验、spaceCode 推导、锁包装与委托调用。 使用 MockMvc standalone + Mockito，无需数据库或 Spring 上下文。
 */
public class FileControllerMoveTest {

	private MockMvc mockMvc;
	private FileService fileService;
	private FileUniquenessLock fileLock;

	@Before
	public void setUp() {
		fileService = Mockito.mock(FileService.class);
		fileLock = Mockito.mock(FileUniquenessLock.class);

		// 构造被测 Controller，并注入依赖
		FileController controller = new FileController();
		ReflectionTestUtils.setField(controller, "fileService", fileService);
		ReflectionTestUtils.setField(controller, "fl", fileLock);

		// 默认行为：锁直接执行回调
		when(fileLock.executeWithLock(Mockito.anyString(), Mockito.any(), Mockito.anyString(), Mockito.anyLong(), Mockito.any()))
			.thenAnswer(invocation -> {
				@SuppressWarnings("unchecked")
				Supplier<Object> op = (Supplier<Object>) invocation.getArgument(4);
				return op.get();
			});

		mockMvc = MockMvcBuilders
			.standaloneSetup(controller)
			.setControllerAdvice(new FileApiResponseAdvice())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
			.build();
	}

	@Test
	public void move_withAncestorId_usesAncestorSpaceCode_andLocksDirectory() throws Exception {
		String ancestorId = "dir-1";
		List<String> fileIds = Arrays.asList("f1", "f2");

		// ancestor 是目录，spaceCode 从 ancestor 取
		OpenAIFile ancestor = OpenAIFile.builder().id(ancestorId).isDir(true).spaceCode("SC-001").build();
		when(fileService.getFile(ancestorId)).thenReturn(ancestor);

		List<OpenAIFile> moved = Arrays.asList(
			OpenAIFile.builder().id("f1").build(),
			OpenAIFile.builder().id("f2").build());
		when(fileService.moveFiles(fileIds, ancestorId, "SC-001")).thenReturn(moved);

		String body = "{\n" +
			"  \"file_ids\": [\"f1\", \"f2\"],\n" +
			"  \"ancestor_id\": \"dir-1\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/move").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("f1"))
			.andExpect(jsonPath("$[1].id").value("f2"));

		verify(fileService, times(1)).getFile(ancestorId);
		verify(fileService, times(1)).moveFiles(fileIds, ancestorId, "SC-001");
		verify(fileLock, times(1)).executeWithLock(eq("SC-001"), eq(ancestorId), eq("__DIRECTORY_MOVE_LOCK__"), Mockito.anyLong(), Mockito.any());
	}

	@Test
	public void move_withSpaceCodeRoot_movesUnderRoot_andLocksRootDirectory() throws Exception {
		List<OpenAIFile> moved = Arrays.asList(OpenAIFile.builder().id("f9").build());
		when(fileService.moveFiles(Arrays.asList("f9"), null, "SC-ROOT")).thenReturn(moved);

		String body = "{\n" +
			"  \"file_ids\": [\"f9\"],\n" +
			"  \"space_code\": \"SC-ROOT\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/move").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("f9"));

		// 不会去查 ancestor
		verify(fileService, never()).getFile(Mockito.anyString());
		verify(fileService, times(1)).moveFiles(Arrays.asList("f9"), null, "SC-ROOT");
		verify(fileLock, times(1)).executeWithLock(eq("SC-ROOT"), eq(null), eq("__DIRECTORY_MOVE_LOCK__"), Mockito.anyLong(), Mockito.any());
	}

	@Test
	public void move_missingAncestorAndSpaceCode_returns400() throws Exception {
		String body = "{\n" +
			"  \"file_ids\": [\"f1\"]\n" +
			"}";

		mockMvc.perform(post("/v1/files/move").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("either ancestor_id or space_code must be provided")));

		verify(fileService, never()).moveFiles(Mockito.anyList(), Mockito.any(), Mockito.anyString());
	}

	@Test
	public void move_targetNotDirectory_returns400() throws Exception {
		String ancestorId = "file-not-dir";
		// ancestor 不是目录
		OpenAIFile ancestor = OpenAIFile.builder().id(ancestorId).isDir(false).spaceCode("SC-001").build();
		when(fileService.getFile(ancestorId)).thenReturn(ancestor);

		String body = "{\n" +
			"  \"file_ids\": [\"f1\"],\n" +
			"  \"ancestor_id\": \"file-not-dir\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/move").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Target must be a directory")));

		verify(fileService, never()).moveFiles(Mockito.anyList(), Mockito.anyString(), Mockito.anyString());
	}

	@Test
	public void move_ancestorNotFound_returns400() throws Exception {
		String ancestorId = "missing-dir";
		when(fileService.getFile(ancestorId)).thenReturn(null);

		String body = "{\n" +
			"  \"file_ids\": [\"f1\"],\n" +
			"  \"ancestor_id\": \"missing-dir\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/move").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Target directory not found: missing-dir")));

		verify(fileService, times(1)).getFile(ancestorId);
		verify(fileService, never()).moveFiles(Mockito.anyList(), Mockito.any(), Mockito.anyString());
		verify(fileLock, never()).executeWithLock(Mockito.anyString(), Mockito.any(), Mockito.anyString(), Mockito.anyLong(), Mockito.any());
	}

	@Test
	public void move_emptyFileIds_returns400() throws Exception {
		String body = "{\n" +
			"  \"file_ids\": [],\n" +
			"  \"space_code\": \"SC-ROOT\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/move").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("file_ids is required and cannot be empty")));

		verify(fileService, never()).moveFiles(Mockito.anyList(), Mockito.any(), Mockito.anyString());
	}

	@Test
	public void move_fileIdsExceedLimit_returns400() throws Exception {
		com.fasterxml.jackson.databind.ObjectMapper om = new ObjectMapper();
		java.util.Map<String, Object> payload = new java.util.HashMap<>();
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 1001; i++) {
			ids.add("f" + i);
		}
		payload.put("file_ids", ids);
		payload.put("space_code", "SC-ROOT");
		String body = om.writeValueAsString(payload);

		mockMvc.perform(post("/v1/files/move").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("file_ids size cannot exceed 1000")));

		verify(fileService, never()).moveFiles(Mockito.anyList(), Mockito.any(), Mockito.anyString());
	}

	@Test
	public void move_bothAncestorAndSpaceCode_ancestorTakesPrecedence() throws Exception {
		String ancestorId = "dir-2";
		java.util.List<String> fileIds = java.util.Arrays.asList("f3", "f4");

		OpenAIFile ancestor = OpenAIFile.builder().id(ancestorId).isDir(true).spaceCode("SC-REAL").build();
		when(fileService.getFile(ancestorId)).thenReturn(ancestor);

		java.util.List<OpenAIFile> moved = java.util.Arrays.asList(
			OpenAIFile.builder().id("f3").build(),
			OpenAIFile.builder().id("f4").build());
		when(fileService.moveFiles(fileIds, ancestorId, "SC-REAL")).thenReturn(moved);

		String body = "{\n" +
			"  \"file_ids\": [\"f3\", \"f4\"],\n" +
			"  \"ancestor_id\": \"dir-2\",\n" +
			"  \"space_code\": \"SC-PROVIDED\"\n" +
			"}";

		mockMvc.perform(post("/v1/files/move").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value("f3"))
			.andExpect(jsonPath("$[1].id").value("f4"));

		verify(fileService, times(1)).getFile(ancestorId);
		verify(fileService, times(1)).moveFiles(fileIds, ancestorId, "SC-REAL");
		verify(fileLock, times(1)).executeWithLock(eq("SC-REAL"), eq(ancestorId), eq("__DIRECTORY_MOVE_LOCK__"), Mockito.anyLong(), Mockito.any());
	}
}



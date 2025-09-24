package com.ke.bella.files.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;

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

public class FileControllerUpdateCitiesTest {

	private MockMvc mockMvc;
	private FileService fileService;
	private FileUniquenessLock fileUniquenessLock;
	private FileController fileController;

	@Before
	public void setup() {
		fileService = Mockito.mock(FileService.class);
		fileUniquenessLock = Mockito.mock(FileUniquenessLock.class);
		fileController = new FileController();

		ReflectionTestUtils.setField(fileController, "fileService", fileService);
		ReflectionTestUtils.setField(fileController, "fl", fileUniquenessLock);

		mockMvc = MockMvcBuilders
			.standaloneSetup(fileController)
			.setControllerAdvice(new FileApiResponseAdvice())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
			.build();
	}

	@Test
	public void updateCities_Success() throws Exception {
		// Given
		String fileId = "test-file-id";
		String citiesJson = "[\"北京\", \"上海\", \"深圳\"]";

		OpenAIFile existingFile = OpenAIFile.builder()
			.id(fileId)
			.filename("test.txt")
			.cities(Arrays.asList("广州"))
			.build();

		OpenAIFile updatedFile = existingFile.toBuilder()
			.cities(Arrays.asList("北京", "上海", "深圳"))
			.build();

		when(fileService.getFile(fileId)).thenReturn(existingFile);
		when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.CITIES)))
			.thenReturn(updatedFile);

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": " + citiesJson + "}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(fileId))
			.andExpect(jsonPath("$.cities[0]").value("北京"))
			.andExpect(jsonPath("$.cities[1]").value("上海"))
			.andExpect(jsonPath("$.cities[2]").value("深圳"));

		// Verify service calls
		verify(fileService).getFile(fileId);
		verify(fileService).updateFile(any(FileOps.class), eq(true), eq(Scope.CITIES));
	}

	@Test
	public void updateCities_FileNotFound() throws Exception {
		// Given
		String fileId = "non-existent-file";
		String citiesJson = "[\"北京\"]";

		when(fileService.getFile(fileId)).thenReturn(null);

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": " + citiesJson + "}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.message").exists());

		verify(fileService).getFile(fileId);
	}

	@Test
	public void updateCities_EmptyFileId() throws Exception {
		// Given
		String citiesJson = "[\"北京\"]";

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", "")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": " + citiesJson + "}"))
			.andExpect(status().isMethodNotAllowed());
	}

	@Test
	public void updateCities_NullRequestBody() throws Exception {
		// Given
		String fileId = "test-file-id";

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isInternalServerError());
	}

	@Test
	public void updateCities_NullCities() throws Exception {
		// Given
		String fileId = "test-file-id";

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").exists());
	}

	@Test
	public void updateCities_EmptyCitiesList() throws Exception {
		// Given
		String fileId = "test-file-id";

		OpenAIFile existingFile = OpenAIFile.builder()
			.id(fileId)
			.filename("test.txt")
			.build();

		OpenAIFile updatedFile = existingFile.toBuilder()
			.cities(Collections.emptyList())
			.build();

		when(fileService.getFile(fileId)).thenReturn(existingFile);
		when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.CITIES)))
			.thenReturn(updatedFile);

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": []}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cities").isEmpty());
	}

	@Test
	public void updateCities_CitiesTooLong() throws Exception {
		// Given
		String fileId = "test-file-id";
		// 创建总长度超过512字符的城市列表
		String longCity = new String(new char[300]).replace('\0', 'x');
		String citiesJson = "[\"" + longCity + "\", \"" + longCity + "\"]";

		OpenAIFile existingFile = OpenAIFile.builder()
			.id(fileId)
			.filename("test.txt")
			.build();

		when(fileService.getFile(fileId)).thenReturn(existingFile);

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": " + citiesJson + "}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.message").value("cities total length cannot exceed 512 characters"));
	}

	@Test
	public void updateCities_MaxLengthCities() throws Exception {
		// Given
		String fileId = "test-file-id";
		// 创建总长度刚好512字符的城市列表
		String city1 = new String(new char[256]).replace('\0', 'a');
		String city2 = new String(new char[256]).replace('\0', 'b');
		String citiesJson = "[\"" + city1 + "\", \"" + city2 + "\"]";

		OpenAIFile existingFile = OpenAIFile.builder()
			.id(fileId)
			.filename("test.txt")
			.build();

		OpenAIFile updatedFile = existingFile.toBuilder()
			.cities(Arrays.asList(city1, city2))
			.build();

		when(fileService.getFile(fileId)).thenReturn(existingFile);
		when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.CITIES)))
			.thenReturn(updatedFile);

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": " + citiesJson + "}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cities[0]").value(city1))
			.andExpect(jsonPath("$.cities[1]").value(city2));
	}

	@Test
	public void updateCities_WithSpecialCharacters() throws Exception {
		// Given
		String fileId = "test-file-id";
		String citiesJson = "[\"北京市\", \"São Paulo\", \"New York City\", \"東京\"]";

		OpenAIFile existingFile = OpenAIFile.builder()
			.id(fileId)
			.filename("test.txt")
			.build();

		OpenAIFile updatedFile = existingFile.toBuilder()
			.cities(Arrays.asList("北京市", "São Paulo", "New York City", "東京"))
			.build();

		when(fileService.getFile(fileId)).thenReturn(existingFile);
		when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.CITIES)))
			.thenReturn(updatedFile);

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": " + citiesJson + "}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cities[0]").value("北京市"))
			.andExpect(jsonPath("$.cities[1]").value("São Paulo"))
			.andExpect(jsonPath("$.cities[2]").value("New York City"))
			.andExpect(jsonPath("$.cities[3]").value("東京"));
	}

	@Test
	public void updateCities_ServiceThrowsException() throws Exception {
		// Given
		String fileId = "test-file-id";
		String citiesJson = "[\"北京\"]";

		OpenAIFile existingFile = OpenAIFile.builder()
			.id(fileId)
			.filename("test.txt")
			.build();

		when(fileService.getFile(fileId)).thenReturn(existingFile);
		when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.CITIES)))
			.thenThrow(new RuntimeException("Database error"));

		// When & Then
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": " + citiesJson + "}"))
			.andExpect(status().isInternalServerError());
	}

	@Test
	public void updateCities_VerifyFileOpsParameters() throws Exception {
		// Given
		String fileId = "test-file-id";
		String citiesJson = "[\"测试城市1\", \"测试城市2\"]";

		OpenAIFile existingFile = OpenAIFile.builder()
			.id(fileId)
			.filename("test.txt")
			.build();

		OpenAIFile updatedFile = existingFile.toBuilder()
			.cities(Arrays.asList("测试城市1", "测试城市2"))
			.build();

		when(fileService.getFile(fileId)).thenReturn(existingFile);
		when(fileService.updateFile(any(FileOps.class), eq(true), eq(Scope.CITIES)))
			.thenReturn(updatedFile);

		// When
		mockMvc.perform(put("/v1/files/{fileId}/cities", fileId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cities\": " + citiesJson + "}"))
			.andExpect(status().isOk());

		// Then - 验证传递给service的FileOps参数
		verify(fileService).updateFile(argThat(ops ->
			ops.getFileId().equals(fileId) &&
				ops.getCities() != null &&
				ops.getCities().size() == 2 &&
				ops.getCities().contains("测试城市1") &&
				ops.getCities().contains("测试城市2")
		), eq(true), eq(Scope.CITIES));
	}
}

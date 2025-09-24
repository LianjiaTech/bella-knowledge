package com.ke.bella.files.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class MoveOps {

	@JsonProperty("file_ids")
	private List<String> fileIds;

	@JsonProperty("ancestor_id")
	private String ancestorId;

	@JsonProperty("space_code")
	private String spaceCode;
}

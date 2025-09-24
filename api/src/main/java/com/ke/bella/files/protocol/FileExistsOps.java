package com.ke.bella.files.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class FileExistsOps {

	@JsonProperty("space_code")
	private String spaceCode;

	@JsonProperty("ancestor_id")
	private String ancestorId;

	private String filename;
}

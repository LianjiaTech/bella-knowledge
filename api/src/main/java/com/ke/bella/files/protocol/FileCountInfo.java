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
public class FileCountInfo {

	@JsonProperty("ancestor_id")
	private String ancestorId;

	private Integer count;

}

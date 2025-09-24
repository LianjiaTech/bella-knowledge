package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Page<T> {

	private long pageNo;

	private long pageSize;

	private long total;

	private long pages;

	private List<T> records;
}

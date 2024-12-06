package com.ke.bella.files.protocol;

import lombok.Data;

@Data
public class FileOps
{
	private String fileId;
	private FileStatus status;
	private BroadcastStatus broadcastStatus;
}

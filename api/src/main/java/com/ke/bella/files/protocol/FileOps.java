package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class FileOps {
    private String fileId;
    private FileStatus status;
    private BroadcastStatus broadcastStatus;
    private String domTreeFileId;
}

package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Progress {
    private Long id;
    private String fileId;
    private String name;
    private String status;
    private String message;
    private Integer percent;
    private Long cuid;
    private String cuName;
    private Long ctime;
    private Long muid;
    private String muName;
    private Long mtime;
}

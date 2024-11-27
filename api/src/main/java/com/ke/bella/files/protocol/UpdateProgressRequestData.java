package com.ke.bella.files.protocol;

import lombok.Data;

@Data
public class UpdateProgressRequestData {
    private String status;
    private String message;
    private int percent;
}

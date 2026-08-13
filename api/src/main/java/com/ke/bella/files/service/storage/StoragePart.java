package com.ke.bella.files.service.storage;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class StoragePart {
    int partNumber;
    long size;
    String etag;
}

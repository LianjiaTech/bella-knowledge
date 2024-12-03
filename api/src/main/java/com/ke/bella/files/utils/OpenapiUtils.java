package com.ke.bella.files.utils;

import static com.ke.bella.files.configuration.Configs.OPEN_API_BASE;

import com.ke.bella.openapi.client.OpenapiClient;

public class OpenapiUtils {
    public static OpenapiClient openapiClient;
    static {
        openapiClient = new OpenapiClient(OPEN_API_BASE);
    }
}

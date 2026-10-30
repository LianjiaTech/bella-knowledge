package com.ke.bella.files.service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.files.configuration.Configs;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.BellaResponse;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class ServiceClientAuthService {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    public ApikeyInfo authenticate(String authorization, String serviceClientCode, String effectiveAkCode) throws IOException {
        Request request = new Request.Builder()
                .url(Configs.OPEN_API_BASE + "/v1/apikey/whoami")
                .header("Authorization", authorization)
                .header(BellaContext.SERVICE_CLIENT_HEADER, serviceClientCode)
                .header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER, effectiveAkCode)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful() || response.body() == null) {
                return null;
            }
            BellaResponse<ApikeyInfo> result = JacksonUtils.MAPPER.readValue(response.body().string(),
                    new TypeReference<BellaResponse<ApikeyInfo>>() { });
            return result != null && result.getCode() == 200 ? result.getData() : null;
        }
    }
}

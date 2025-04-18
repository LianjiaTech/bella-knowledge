package com.ke.bella.files.api.interceptor;

import static com.ke.bella.files.configuration.Configs.MAX_FILE_SIZE;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.AccessDeniedException;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.ke.bella.files.annotations.FileAPI;
import com.ke.bella.files.protocol.CustomOpenAiError;
import com.ke.bella.files.protocol.FileException.AuthorizationException;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileException.ProgressNotFoundException;
import com.ke.bella.files.utils.JsonUtils;
import com.theokanning.openai.OpenAiError.OpenAiErrorDetails;

import lombok.extern.slf4j.Slf4j;

@FileAPI
@RestControllerAdvice(annotations = FileAPI.class)
@Slf4j
public class FileApiResponseAdvice implements ResponseBodyAdvice<Object> {
    private static String stacktrace(Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        response.getHeaders().add("Cache-Control", "no-cache");
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        if(body instanceof CustomOpenAiError) {
            response.setStatusCode(HttpStatus.valueOf(((CustomOpenAiError) body).getCode()));
            return body;
        }

        if(body instanceof String) {
            return JsonUtils.toJson(body);
        }
        return body;
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public CustomOpenAiError handleException(Exception e) {
        int code = 500;
        String errorType;
        String msg = e.getMessage();
        if(e instanceof AuthorizationException) {
            code = 401;
        } else if(e instanceof FileNotFoundException || e instanceof ProgressNotFoundException) {
            code = 404;
        } else if(e instanceof MaxUploadSizeExceededException) {
            code = 413;
            msg = String.format("File size exceeds the maximum limit of %s.", MAX_FILE_SIZE);
        } else if(e instanceof IllegalArgumentException) {
            code = 400;
        } else if(e instanceof NotImplementedException) {
            code = 405;
        } else if (e instanceof AccessDeniedException) {
            code = 403;
        }
        if(code == 500) {
            errorType = "internal_server_error";
            LOGGER.warn(e.getLocalizedMessage(), e);
        } else {
            errorType = "invalid_request_error";
            LOGGER.info(e.getMessage());
        }
        OpenAiErrorDetails openAiErrorDetails = new OpenAiErrorDetails(msg, errorType, null, null);
        CustomOpenAiError customOpenAiError = new CustomOpenAiError();
        customOpenAiError.setCode(code);
        customOpenAiError.setError(openAiErrorDetails);
        return customOpenAiError;
    }
}

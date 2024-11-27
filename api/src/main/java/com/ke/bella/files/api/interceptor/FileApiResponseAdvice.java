package com.ke.bella.files.api.interceptor;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.naming.AuthenticationException;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.ke.bella.files.annotations.FileAPI;
import com.ke.bella.files.protocol.BellaResponse;
import com.ke.bella.files.utils.JsonUtils;

import lombok.extern.slf4j.Slf4j;

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
        if(body instanceof BellaResponse) {
            response.setStatusCode(HttpStatus.valueOf(((BellaResponse) body).getCode()));
            return body;
        }
        BellaResponse<Object> resp = new BellaResponse<>();
        resp.setCode(200);
        resp.setTimestamp(System.currentTimeMillis());
        resp.setData(body);
        if(body instanceof String) {
            return JsonUtils.toJson(resp);
        }
        return resp;
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public BellaResponse<?> handleException(Exception e) {
        int code = 500;
        String msg = e.getLocalizedMessage();
        if(e instanceof AuthenticationException) {
            code = 401;
        }
        if(code == 500) {
            LOGGER.warn(e.getLocalizedMessage(), e);
        } else {
            LOGGER.info(e.getMessage());
        }

        BellaResponse<?> resp = new BellaResponse<>();
        resp.setCode(code);
        resp.setTimestamp(System.currentTimeMillis());

        if(code == 500) {
            resp.setStacktrace(stacktrace(e));
        }
        return resp;
    }
}

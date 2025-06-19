package com.ke.bella.files.api.interceptor;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.http.auth.AuthenticationException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.ke.bella.files.api.DatasetController;
import com.ke.bella.files.utils.JsonUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.BellaResponse;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice(assignableTypes = { BellaApiResponseAdvice.class, DatasetController.class })
@Slf4j
public class BellaApiResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass,
            ServerHttpRequest request, ServerHttpResponse response) {
        try {
            response.getHeaders().add("Cache-Control", "no-cache");
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
        } finally {
            BellaContext.clearAll();
        }
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public BellaResponse<?> exceptionHandler(Exception e) {
        int code = 500;
        String msg = e.getLocalizedMessage();
        if(e instanceof IllegalArgumentException
                || e instanceof DataIntegrityViolationException
                || e instanceof MethodArgumentNotValidException
                || e instanceof MaxUploadSizeExceededException) {
            code = 400;
        }

        if(e instanceof DataIntegrityViolationException) {
            msg = "invalid request data. error : " + e.getMessage();
        }

        if(e instanceof MaxUploadSizeExceededException) {
            msg = e.getMessage();
        }

        if(e instanceof AuthenticationException) {
            code = 401;
        }

        if(code == 500) {
            LOGGER.warn(e.getMessage(), e);
        } else {
            LOGGER.info(e.getMessage());
        }

        BellaResponse<?> er = new BellaResponse<>();
        er.setCode(code);
        er.setTimestamp(System.currentTimeMillis());
        er.setMessage(msg);
        if(code == 500) {
            er.setStacktrace(stacktrace(e));
        }

        return er;
    }

    private static String stacktrace(Throwable e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}

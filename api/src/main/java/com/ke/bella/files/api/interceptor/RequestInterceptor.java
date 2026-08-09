package com.ke.bella.files.api.interceptor;

import static com.ke.bella.files.utils.OpenapiUtils.openapiClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.ke.bella.files.protocol.CustomOpenAiError;
import com.ke.bella.files.protocol.FileException.AuthorizationException;
import com.ke.bella.files.utils.JsonUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.theokanning.openai.OpenAiError.OpenAiErrorDetails;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RequestInterceptor extends HandlerInterceptorAdapter {
    @Value("${bella.dev-auth.enabled:false}")
    private boolean devAuthEnabled;

    @Value("${bella.dev-auth.token:local-dev}")
    private String devAuthToken;

    @Value("${bella.dev-auth.user-id:1}")
    private Long devUserId;

    @Value("${bella.dev-auth.user-name:Local Dev}")
    private String devUserName;

    @Value("${bella.dev-auth.space-code:local-dev}")
    private String devSpaceCode;

    @Value("${bella.dev-auth.manager-ak:local-dev-ak}")
    private String devManagerAk;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if(path.startsWith("/actuator/") || "/error".equals(path)) {
            return true;
        }
        if(BellaContext.getOperatorIgnoreNull() != null) {
            LOGGER.info("operator already set in BellaContext, skipping api key interceptor logic.");
            return true;
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if(auth == null || !auth.startsWith("Bearer ")) {
            writeAuthorizationError(response, auth);
            return false;
        }

        String apikey = auth.substring("Bearer ".length());
        if(devAuthEnabled && StringUtils.equals(apikey, devAuthToken)) {
            BellaContext.setOperator(Operator.builder()
                    .userId(devUserId)
                    .userName(devUserName)
                    .spaceCode(devSpaceCode)
                    .managerAk(devManagerAk)
                    .build());
            BellaContext.setApikey(ApikeyInfo.builder()
                    .apikey(devAuthToken)
                    .code(devManagerAk)
                    .ownerName(devUserName)
                    .userId(devUserId)
                    .build());
            return true;
        }

        ApikeyInfo apikeyInfo = openapiClient.whoami(apikey);
        if(apikeyInfo == null) {
            writeAuthorizationError(response, auth);
            return false;
        }
        Long akOwnerId = apikeyInfo.getUserId();
        String aKOwnerName = apikeyInfo.getOwnerName();

        BellaContext.setOperator(
                Operator.builder()
                        .userId(akOwnerId)
                        .userName(aKOwnerName)
                        .build());
        BellaContext.setApikey(apikeyInfo);
        return true;
    }

    private void writeAuthorizationError(HttpServletResponse response, String auth) throws Exception {
        AuthorizationException error = new AuthorizationException(auth);
        OpenAiErrorDetails details = new OpenAiErrorDetails(error.getMessage(), "invalid_request_error", null, null);
        CustomOpenAiError body = new CustomOpenAiError();
        body.setCode(HttpStatus.UNAUTHORIZED.value());
        body.setError(details);

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJson(body));
    }
}

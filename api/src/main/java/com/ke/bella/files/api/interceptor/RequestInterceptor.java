package com.ke.bella.files.api.interceptor;

import static com.ke.bella.files.utils.OpenapiUtils.openapiClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.ke.bella.files.protocol.FileException.AuthorizationException;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RequestInterceptor extends HandlerInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(BellaContext.getOperatorIgnoreNull() != null) {
            LOGGER.info("operator already set in BellaContext, skipping api key interceptor logic.");
            return true;
        }
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if(auth == null || !auth.startsWith("Bearer ")) {
            throw new AuthorizationException(auth);
        }

        String bellaOperatorId = request.getHeader("X-BELLA-OPERATOR-ID");
        String bellaOperatorName = request.getHeader("X-BELLA-OPERATOR-NAME");
        String bellaSpaceCode = request.getHeader("X-BELLA-SPACE-CODE");

        String apikey = auth.substring("Bearer ".length());
        ApikeyInfo apikeyInfo = openapiClient.whoami(apikey);
        if(apikeyInfo == null) {
            throw new AuthorizationException(auth);
        }
        Long akOwnerId = apikeyInfo.getUserId();
        String aKOwnerName = apikeyInfo.getOwnerName();

        Long userId = (bellaOperatorId == null || bellaOperatorId.isEmpty()) ? akOwnerId : Long.valueOf(bellaOperatorId);
        String userName = (bellaOperatorName == null || bellaOperatorName.isEmpty()) ? aKOwnerName : bellaOperatorName;

        BellaContext.setOperator(com.ke.bella.openapi.Operator.builder().userId(userId).userName(userName).spaceCode(bellaSpaceCode).build());
        BellaContext.setApikey(apikeyInfo);
        return true;
    }
}

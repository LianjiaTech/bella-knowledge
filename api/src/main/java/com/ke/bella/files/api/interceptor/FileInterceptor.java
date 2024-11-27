package com.ke.bella.files.api.interceptor;

import static com.ke.bella.files.configuration.Configs.OPEN_API_BASE;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.ke.bella.files.BellaContext;
import com.ke.bella.files.api.Operator;
import com.ke.bella.files.protocol.FileException.AuthorizationException;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.client.OpenapiClient;

@Component
public class FileInterceptor extends HandlerInterceptorAdapter {

    private static final OpenapiClient openapiClient = new OpenapiClient(OPEN_API_BASE);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
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

        BellaContext.setOperator(Operator.builder().userId(userId).userName(userName).spaceCode(bellaSpaceCode).build());
        BellaContext.setApikey(apikeyInfo);
        return true;
    }
}

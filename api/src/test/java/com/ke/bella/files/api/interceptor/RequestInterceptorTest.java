package com.ke.bella.files.api.interceptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.service.ServiceClientAuthService;
import com.ke.bella.files.utils.BellaContextHelper;
import com.ke.bella.files.utils.OpenapiUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.client.OpenapiClient;

public class RequestInterceptorTest {
    private RequestInterceptor interceptor;
    private ServiceClientAuthService service;
    private OpenapiClient originalClient;

    @Before
    public void setup() {
        BellaContext.clearAll();
        interceptor = new RequestInterceptor();
        service = mock(ServiceClientAuthService.class);
        ReflectionTestUtils.setField(interceptor, "serviceClientAuthService", service);
        originalClient = OpenapiUtils.openapiClient;
        OpenapiUtils.openapiClient = mock(OpenapiClient.class);
    }

    @After
    public void cleanup() {
        BellaContext.clearAll();
        OpenapiUtils.openapiClient = originalClient;
    }

    @Test
    public void ordinaryApiKeyKeepsExistingIdentity() throws Exception {
        ApikeyInfo user = identity("ordinary-ak");
        when(OpenapiUtils.openapiClient.whoami("secret")).thenReturn(user);
        MockHttpServletRequest request = request("Bearer secret", null, null);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertSame(user, BellaContext.getApikey());
        assertEquals("ordinary-ak", BellaContextHelper.getOperatorAkCode());
        verifyNoInteractions(service);
    }

    @Test
    public void ordinaryApiKeyFailureReturnsUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("Bearer invalid", null, null), response, new Object()));
        assertEquals(401, response.getStatus());
        verifyNoInteractions(service);
    }

    @Test
    public void serviceClientUsesOnlyEffectiveIdentityEvenWithExistingOperator() throws Exception {
        BellaContext.setOperator(Operator.builder().userId(999L).managerAk("system-ak").build());
        when(service.authenticate("Bearer sc-secret", "parser", "user-ak")).thenReturn(identity("user-ak"));
        assertTrue(interceptor.preHandle(request("Bearer sc-secret", "parser", "user-ak"),
                new MockHttpServletResponse(), new Object()));
        assertEquals("user-ak", BellaContextHelper.getOperatorAkCode());
        assertEquals(Long.valueOf(7L), BellaContext.getOperator().getUserId());
        assertEquals("user", BellaContext.getOperator().getUserName());
        assertNull(BellaContext.getOperator().getManagerAk());
        verifyNoInteractions(OpenapiUtils.openapiClient);
    }

    @Test
    public void incompleteOrMalformedHeadersNeverReachAuthentication() throws Exception {
        String[][] headers = {
                {null, "parser", "user-ak"}, {"Bearer sc-secret", null, "user-ak"},
                {"Bearer sc-secret", "parser", null}, {"Basic sc-secret", "parser", "user-ak"},
                {"Bearer ", "parser", "user-ak"}, {"Bearer sc-secret", "", "user-ak"},
                {"Bearer sc-secret", "parser", " "}, {"Bearer sc-secret", "parser,other", "user-ak"},
                {"Bearer sc-secret", "parser", " user-ak"}, {"Bearer sc-secret", null, null}
        };
        for(String[] values : headers) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request(values[0], values[1], values[2]), response, new Object()));
            assertEquals(401, response.getStatus());
            assertFalse(response.getContentAsString().contains("sc-secret"));
        }
        verifyNoInteractions(service, OpenapiUtils.openapiClient);
    }

    @Test
    public void duplicateAndLegacyIdentityHeadersAreRejected() throws Exception {
        for(String name : new String[] {"Authorization", BellaContext.SERVICE_CLIENT_HEADER,
                BellaContext.EFFECTIVE_API_KEY_CODE_HEADER, BellaContext.BELLA_USER_AK_HEADER}) {
            MockHttpServletRequest request = request("Bearer sc-secret", "parser", "user-ak");
            request.addHeader(name, "other");
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(401, response.getStatus());
        }
        verifyNoInteractions(service, OpenapiUtils.openapiClient);
    }

    @Test
    public void invalidEffectiveIdentityCannotFallBackToSystem() throws Exception {
        for(ApikeyInfo invalid : new ApikeyInfo[] {null, identity("system-ak"), ApikeyInfo.builder().code("user-ak").build()}) {
            BellaContext.setApikey(identity("system-ak"));
            when(service.authenticate("Bearer sc-secret", "parser", "user-ak")).thenReturn(invalid);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request("Bearer sc-secret", "parser", "user-ak"), response, new Object()));
            assertEquals(401, response.getStatus());
            assertNull(BellaContext.getApikeyIgnoreNull());
        }
        verifyNoInteractions(OpenapiUtils.openapiClient);
    }

    @Test
    public void upstreamAuthenticationExceptionReturnsUnauthorized() throws Exception {
        when(service.authenticate(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("secret"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("Bearer sc-secret", "parser", "user-ak"), response, new Object()));
        assertEquals(401, response.getStatus());
        assertFalse(response.getContentAsString().contains("sc-secret"));
        assertNull(BellaContext.getApikeyIgnoreNull());
    }

    private ApikeyInfo identity(String code) {
        return ApikeyInfo.builder().code(code).userId(7L).ownerName("user").build();
    }

    private MockHttpServletRequest request(String auth, String clientCode, String effectiveCode) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/files");
        if(auth != null) {
            request.addHeader("Authorization", auth);
        }
        if(clientCode != null) {
            request.addHeader(BellaContext.SERVICE_CLIENT_HEADER, clientCode);
        }
        if(effectiveCode != null) {
            request.addHeader(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER, effectiveCode);
        }
        return request;
    }
}

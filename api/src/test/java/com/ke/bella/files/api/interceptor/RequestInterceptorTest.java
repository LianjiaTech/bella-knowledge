package com.ke.bella.files.api.interceptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.request.BellaInterceptor;

import okhttp3.Request;

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
        AuthResult result = BellaContext.getAuthResultIgnoreNull();
        assertNotNull(result);
        assertEquals(CredentialType.SERVICE_CLIENT, result.getCredentialType());
        assertEquals("parser", result.getServiceClientCode());
        assertSame(BellaContext.getApikey(), result.getApikeyInfo());
        verifyNoInteractions(OpenapiUtils.openapiClient);
    }

    @Test
    public void serviceClientAuthResultSurvivesAsyncSnapshotAndSdkForwarding() throws Exception {
        ApikeyInfo effective = identity("user-ak");
        when(service.authenticate("Bearer sc-secret", "parser", "user-ak")).thenReturn(effective);
        assertTrue(interceptor.preHandle(request("Bearer sc-secret", "parser", "user-ak"),
                new MockHttpServletResponse(), new Object()));
        Map<String, Object> snapshot = BellaContext.snapshot();
        Map<String, Object> restored = CompletableFuture.supplyAsync(() -> {
            try {
                BellaContext.replace(snapshot);
                return BellaContext.snapshot();
            } finally {
                BellaContext.clearAll();
            }
        }).get(5, TimeUnit.SECONDS);
        AuthResult result = (AuthResult) restored.get("authResult");
        assertNotNull(result);
        assertEquals(CredentialType.SERVICE_CLIENT, result.getCredentialType());
        assertEquals("parser", result.getServiceClientCode());
        assertSame(effective, result.getApikeyInfo());
        assertSame(effective, restored.get("ak"));

        String previousCode = (String) ReflectionTestUtils.getField(BellaInterceptor.class, "configuredServiceClientCode");
        String previousToken = (String) ReflectionTestUtils.getField(BellaInterceptor.class, "configuredServiceClientToken");
        try {
            BellaInterceptor.configureServiceClient("file-api", "sc-outbound");
            Request.Builder outgoing = new Request.Builder().url("http://openapi.test/v1/files");
            BellaInterceptor.addServiceClientHeaders(outgoing, restored);
            Request forwarded = outgoing.build();
            assertEquals("Bearer sc-outbound", forwarded.header("Authorization"));
            assertEquals("file-api", forwarded.header(BellaContext.SERVICE_CLIENT_HEADER));
            assertEquals("user-ak", forwarded.header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER));
        } finally {
            BellaInterceptor.configureServiceClient(previousCode, previousToken);
        }
    }

    @Test
    public void failedServiceClientClearsPreviousAuthResult() throws Exception {
        BellaContext.setAuthResult(AuthResult.builder()
                .credentialType(CredentialType.SERVICE_CLIENT)
                .apikeyInfo(identity("system-ak"))
                .serviceClientCode("previous-client")
                .build());
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("Bearer sc-secret", "parser", "user-ak"), response, new Object()));
        assertEquals(401, response.getStatus());
        assertNull(BellaContext.getAuthResultIgnoreNull());
        assertNull(BellaContext.getApikeyIgnoreNull());
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

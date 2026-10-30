package com.ke.bella.files.service;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ke.bella.files.configuration.Configs;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

public class ServiceClientAuthServiceTest {
    private HttpServer server;
    private String originalBase;
    private final AtomicReference<Headers> headers = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> body = new AtomicReference<>(
            "{\"code\":200,\"data\":{\"code\":\"user-ak\",\"userId\":7,\"ownerName\":\"user\"}}");

    @Before
    public void setup() throws Exception {
        originalBase = Configs.OPEN_API_BASE;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/apikey/whoami", exchange -> {
            headers.set(exchange.getRequestHeaders());
            requests.incrementAndGet();
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", "/redirected");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        Configs.OPEN_API_BASE = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void cleanup() {
        server.stop(0);
        Configs.OPEN_API_BASE = originalBase;
        BellaContext.clearAll();
    }

    @Test
    public void forwardsThreeHeadersUnmodifiedWithoutContextIdentity() throws Exception {
        BellaContext.setOperator(Operator.builder().userId(999L).managerAk("system-ak").build());
        BellaContext.setApikey(ApikeyInfo.builder().code("system-ak").apikey("system-secret").build());
        ApikeyInfo effective = new ServiceClientAuthService().authenticate("Bearer sc-secret", "parser", "user-ak");
        assertEquals("user-ak", effective.getCode());
        assertEquals(Long.valueOf(7L), effective.getUserId());
        assertEquals("Bearer sc-secret", headers.get().getFirst("Authorization"));
        assertEquals("parser", headers.get().getFirst(BellaContext.SERVICE_CLIENT_HEADER));
        assertEquals("user-ak", headers.get().getFirst(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER));
    }

    @Test
    public void neverCachesEffectiveIdentitiesByServiceCredential() throws Exception {
        ServiceClientAuthService service = new ServiceClientAuthService();
        assertEquals("user-ak", service.authenticate("Bearer sc-secret", "parser", "user-ak").getCode());
        body.set("{\"code\":200,\"data\":{\"code\":\"other-ak\",\"userId\":8}}");
        assertEquals("other-ak", service.authenticate("Bearer sc-secret", "parser", "other-ak").getCode());
        assertEquals(2, requests.get());
        assertEquals("other-ak", headers.get().getFirst(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER));
    }

    @Test
    public void rejectsHttpErrorsAndRedirects() throws Exception {
        ServiceClientAuthService service = new ServiceClientAuthService();
        for(int code : new int[] {401, 403, 500, 302}) {
            status.set(code);
            assertNull(service.authenticate("Bearer sc-secret", "parser", "user-ak"));
        }
        assertEquals(4, requests.get());
    }

    @Test
    public void rejectsFailedAndEmptyUpstreamEnvelopes() throws Exception {
        ServiceClientAuthService service = new ServiceClientAuthService();
        for(String json : new String[] {"null", "{}", "{\"code\":401}", "{\"code\":200,\"data\":null}"}) {
            body.set(json);
            assertNull(service.authenticate("Bearer sc-secret", "parser", "user-ak"));
        }
    }
}

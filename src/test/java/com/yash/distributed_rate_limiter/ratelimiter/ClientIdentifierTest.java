package com.yash.distributed_rate_limiter.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIdentifierTest {

    private final ClientIdentifier identifier = new ClientIdentifier();

    @Test
    void whenApiKeyPresent_usesApiKeyRegardlessOfOtherHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn("key123");
        when(request.getHeader("X-Client-Id")).thenReturn("user1"); // present, but should be ignored

        String result = identifier.resolve(request);

        assertEquals("apikey:key123", result);
    }

    @Test
    void whenNoApiKey_fallsBackToClientId() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("X-Client-Id")).thenReturn("user1");

        String result = identifier.resolve(request);

        assertEquals("client:user1", result);
    }

    @Test
    void whenNeitherHeaderPresent_fallsBackToIpAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("X-Client-Id")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.1.5");

        String result = identifier.resolve(request);

        assertEquals("ip:192.168.1.5", result);
    }

    @Test
    void whenClientIdIsBlankString_treatedAsAbsent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("X-Client-Id")).thenReturn("   "); // blank, not null
        when(request.getRemoteAddr()).thenReturn("192.168.1.5");

        String result = identifier.resolve(request);

        assertEquals("ip:192.168.1.5", result); // should fall through, not use the blank string
    }
}
package com.yash.distributed_rate_limiter.ratelimiter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIdentifier {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String CLIENT_ID_HEADER = "X-Client-Id";

    /**
     * Resolves a single identity string to rate-limit by, checking sources
     * in priority order: API key (most specific/trusted) -> client ID header
     * -> IP address (last resort, for fully anonymous traffic).
     *
     * The returned value is prefixed with its source, so identically-named
     * values from different sources (e.g. an API key that happens to match
     * an IP address string) never collide in Redis.
     */
    public String resolve(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey;
        }

        String clientId = request.getHeader(CLIENT_ID_HEADER);
        if (clientId != null && !clientId.isBlank()) {
            return "client:" + clientId;
        }

        return "ip:" + request.getRemoteAddr();
    }
}
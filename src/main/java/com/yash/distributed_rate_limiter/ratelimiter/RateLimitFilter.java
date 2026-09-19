package com.yash.distributed_rate_limiter.ratelimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final TokenBucketRateLimiter rateLimiter;
    private final ClientIdentifier clientIdentifier;

    @Autowired
    public RateLimitFilter(TokenBucketRateLimiter rateLimiter, ClientIdentifier clientIdentifier) {
        this.rateLimiter = rateLimiter;
        this.clientIdentifier = clientIdentifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Debug/inspection/actuator endpoints are exempt from rate limiting.
        // Actuator also needs CORS headers so the local dashboard.html (loaded via
        // file://) can read metrics directly from the browser.
        if (path.startsWith("/api/debug") || path.startsWith("/actuator")) {
            if (path.startsWith("/actuator")) {
                response.setHeader("Access-Control-Allow-Origin", "*");
                response.setHeader("Access-Control-Allow-Methods", "GET");
            }
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = clientIdentifier.resolve(request);

        RateLimitResult result = rateLimiter.checkLimit(clientId);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.secondsUntilFullRefill()));

        if (result.isAllowed()) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.secondsUntilFullRefill()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Rate limit exceeded\"}");
        }
    }
}
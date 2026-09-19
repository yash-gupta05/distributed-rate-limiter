package com.yash.distributed_rate_limiter.ratelimiter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class TierResolver {

    // Comma-separated list of identities (as resolved by ClientIdentifier, e.g. "apikey:key123")
    // that should be treated as premium. Everyone else defaults to FREE.
    private final Set<String> premiumClients;

    public TierResolver(@Value("${ratelimiter.premium-clients:}") String premiumClientsCsv) {
        if (premiumClientsCsv == null || premiumClientsCsv.isBlank()) {
            this.premiumClients = new HashSet<>();
        } else {
            this.premiumClients = new HashSet<>(Arrays.asList(premiumClientsCsv.split(",")));
        }
    }

    public Tier resolveTier(String clientId) {
        return premiumClients.contains(clientId) ? Tier.PREMIUM : Tier.FREE;
    }
}
package com.soheil.lifeos;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Mandatory guard for future SOHEIL networking.
 * Current baseline has no INTERNET permission; when networking is introduced,
 * endpoints must be explicitly allowlisted here/configured through trusted policy.
 */
public final class SecureNetworkPolicy {
    private final Set<String> allowedHosts;

    public SecureNetworkPolicy(Set<String> allowedHosts) {
        Set<String> normalized = new HashSet<>();
        if (allowedHosts != null) {
            for (String host : allowedHosts) {
                if (host != null && !host.trim().isEmpty()) normalized.add(host.trim().toLowerCase());
            }
        }
        this.allowedHosts = Collections.unmodifiableSet(normalized);
    }

    public URI requireAllowed(String endpoint) {
        try {
            URI u = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(u.getScheme())) throw new SecurityException("HTTPS required");
            if (u.getUserInfo() != null) throw new SecurityException("Credentials in URL are forbidden");
            if (u.getHost() == null) throw new SecurityException("Endpoint host missing");
            String host = u.getHost().toLowerCase();
            if (!allowedHosts.contains(host)) throw new SecurityException("Endpoint is not allowlisted: " + host);
            int port = u.getPort();
            if (port != -1 && port != 443) throw new SecurityException("Production endpoint must use TLS port 443");
            return u;
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Invalid endpoint", e);
        }
    }

    public Set<String> allowedHosts() { return allowedHosts; }
}

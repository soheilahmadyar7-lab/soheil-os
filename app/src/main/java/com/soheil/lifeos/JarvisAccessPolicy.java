package com.soheil.lifeos;

import java.util.EnumSet;

/**
 * Security boundary for future connected Jarvis.
 * Remote access is DEFAULT DENY and must be explicitly enabled per domain.
 */
public final class JarvisAccessPolicy {
    public enum Domain {
        TASKS, INBOX, MEMORY, DAILY_STATE, JOURNAL, HEALTH,
        FINANCIAL, PEOPLE, CALENDAR, LOCATION, FILES
    }

    private final SecurePrefs prefs;
    private final SoheilDb db;
    private static final EnumSet<Domain> ALWAYS_EXPLICIT = EnumSet.of(
            Domain.JOURNAL, Domain.HEALTH, Domain.FINANCIAL,
            Domain.PEOPLE, Domain.LOCATION, Domain.FILES);

    public JarvisAccessPolicy(SecurePrefs prefs, SoheilDb db) {
        this.prefs = prefs;
        this.db = db;
    }

    /** Local Jarvis may use only data already inside SOHEIL. */
    public boolean localAllowed(Domain d) {
        return d == Domain.TASKS || d == Domain.INBOX || d == Domain.MEMORY ||
                d == Domain.DAILY_STATE || d == Domain.JOURNAL;
    }

    /** Remote Jarvis is disabled per domain until the user explicitly opts in. */
    public boolean remoteEnabled(Domain d) {
        return "1".equals(prefs.getString("remote_domain_" + d.name(), "0"));
    }

    public void setRemoteEnabled(Domain d, boolean enabled) {
        prefs.putString("remote_domain_" + d.name(), enabled ? "1" : "0");
        db.auditAccess("SECURITY_POLICY", "REMOTE_" + d.name() + "=" + enabled);
    }

    /** Sensitive classes must still obtain per-request consent even if integration is enabled. */
    public boolean requiresPerRequestConsent(Domain d) {
        return ALWAYS_EXPLICIT.contains(d);
    }

    public void requireRemoteAccess(Domain d, boolean perRequestConsent) {
        if (!remoteEnabled(d)) throw new SecurityException("Remote Jarvis access denied for " + d.name());
        if (requiresPerRequestConsent(d) && !perRequestConsent) {
            throw new SecurityException("Explicit per-request consent required for " + d.name());
        }
        db.auditAccess(d.name(), "REMOTE_JARVIS_CONTEXT");
    }
}

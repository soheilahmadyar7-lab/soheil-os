package com.soheil.lifeos;

import android.content.Context;
import android.content.SharedPreferences;

/** Small encrypted preference layer for sensitive scalar values. */
public final class SecurePrefs {
    private static final String NAME = "soheil_secure_prefs";
    private static final String PREFIX = "enc_";
    private final SharedPreferences prefs;
    private final SharedPreferences legacy;
    private final SoheilCrypto crypto;

    public SecurePrefs(Context context, SoheilCrypto crypto) {
        this.prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        this.legacy = context.getSharedPreferences("soheil", Context.MODE_PRIVATE);
        this.crypto = crypto;
    }

    public String getString(String key, String def) {
        String enc = prefs.getString(PREFIX + key, null);
        if (enc != null) return crypto.decrypt(enc);

        // One-time migration from old alpha SharedPreferences.
        if (legacy.contains(key)) {
            String old = legacy.getString(key, def);
            putString(key, old == null ? "" : old);
            legacy.edit().remove(key).apply();
            return old == null ? def : old;
        }
        return def;
    }

    public void putString(String key, String value) {
        String enc = crypto.encrypt(value == null ? "" : value);
        if (!prefs.edit().putString(PREFIX + key, enc).commit()) {
            throw new SecurityException("Secure preferences write failed");
        }
    }

    public void remove(String key) {
        prefs.edit().remove(PREFIX + key).apply();
        legacy.edit().remove(key).apply();
    }
}

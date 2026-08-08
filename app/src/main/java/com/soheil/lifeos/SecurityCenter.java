package com.soheil.lifeos;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;

/** Central platform-hardening policy. Future screens/features must use this class. */
public final class SecurityCenter {
    private SecurityCenter() {}

    public static final long BACKGROUND_LOCK_MS = 30_000L;
    public static final long INACTIVITY_LOCK_MS = 5 * 60_000L;

    public static void hardenWindow(Activity activity) {
        Window w = activity.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (Build.VERSION.SDK_INT >= 31) w.setHideOverlayWindows(true);
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(false);
        View decor = w.getDecorView();
        decor.setFilterTouchesWhenObscured(true);
        if (Build.VERSION.SDK_INT >= 26) {
            decor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
    }

    public static boolean isDebuggable(Context c) {
        return (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /** Advisory only. Root checks are bypassable and must never be used as the sole trust boundary. */
    public static boolean hasRootIndicators() {
        String tags = Build.TAGS;
        if (tags != null && tags.contains("test-keys")) return true;
        String[] paths = {
                "/system/app/Superuser.apk", "/system/xbin/su", "/system/bin/su",
                "/sbin/su", "/su/bin/su", "/data/local/xbin/su", "/data/local/bin/su"
        };
        for (String p : paths) if (new File(p).exists()) return true;
        return false;
    }

    public static void requireHttps(String url) {
        if (url == null || !url.startsWith("https://")) {
            throw new SecurityException("SOHEIL blocks non-HTTPS network endpoints");
        }
    }

    public static String runtimeSummary(Context c, SoheilCrypto crypto) {
        return "Vault: " + (crypto.isUnlocked() ? "UNLOCKED" : "LOCKED") +
                "\nCrypto: " + crypto.securitySummary() +
                "\nScreen shield: ON" +
                "\nOverlay shield: " + (Build.VERSION.SDK_INT >= 31 ? "ON" : "legacy mitigation") +
                "\nAuto-backup: OFF" +
                "\nCleartext network: BLOCKED" +
                "\nBuild: " + (isDebuggable(c) ? "TEST/DEBUG" : "HARDENED") +
                "\nRoot indicators: " + (hasRootIndicators() ? "WARNING" : "none detected");
    }
}

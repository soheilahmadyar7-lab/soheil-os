#!/usr/bin/env bash
set -euo pipefail

MANIFEST="app/src/main/AndroidManifest.xml"
ALLOW="security/permissions.allowlist"

fail(){ echo "SECURITY GATE FAILED: $1" >&2; exit 1; }

# Manifest invariants.
grep -q 'android:allowBackup="false"' "$MANIFEST" || fail "automatic backup must remain disabled"
grep -q 'android:usesCleartextTraffic="false"' "$MANIFEST" || fail "cleartext traffic must remain disabled"
grep -q 'android:networkSecurityConfig="@xml/network_security_config"' "$MANIFEST" || fail "network security config missing"
grep -q 'android:exported="false"' "$MANIFEST" || fail "internal receiver must remain non-exported"

# Permission allowlist. Any new permission requires deliberate review/update of allowlist.
actual=$(grep -o 'android.permission.[A-Z0-9_]*' "$MANIFEST" | sort -u || true)
allowed=$(grep -v '^#' "$ALLOW" | sed '/^[[:space:]]*$/d' | sort -u || true)
if [[ "$actual" != "$allowed" ]]; then
  echo "Actual permissions:"; printf '%s\n' "$actual"
  echo "Allowed permissions:"; printf '%s\n' "$allowed"
  fail "Android permission set changed without security review"
fi

# No cleartext endpoints in application source.
if grep -RInE 'http://[^/]' app/src/main/java 2>/dev/null; then
  fail "cleartext URL found in application source"
fi

# No application logging of potentially sensitive values.
if grep -RInE 'android\.util\.Log\.|\bLog\.(d|v|i|w|e)\(' app/src/main/java 2>/dev/null; then
  fail "runtime logging is forbidden in the Life Vault code"
fi

# Obvious secret/private-key signatures must never enter Git history.
if grep -RInE --exclude='security_gate.sh' \
  '(sk-[A-Za-z0-9_-]{20,}|OPENAI_API_KEY[[:space:]]*=|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16})' \
  . --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle 2>/dev/null; then
  fail "possible hard-coded secret/private key detected"
fi

# Signing/private material must not be tracked.
if git ls-files | grep -E '\.(jks|keystore|p12|pem|key)$' >/dev/null; then
  fail "signing/private-key file is tracked by Git"
fi

# Core runtime privacy controls must remain present.
grep -q 'FLAG_SECURE' app/src/main/java/com/soheil/lifeos/SecurityCenter.java || fail "screen capture shield missing"
grep -q 'AES/GCM/NoPadding' app/src/main/java/com/soheil/lifeos/SoheilCrypto.java || fail "vault authenticated encryption missing"
grep -q 'AndroidKeyStore' app/src/main/java/com/soheil/lifeos/SoheilCrypto.java || fail "Keystore boundary missing"

echo "SOHEIL security gate: PASS"

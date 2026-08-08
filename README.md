# SOHEIL — Life OS
### v0.3.1-security · Private Life Vault foundation

**SOHEIL** is a local-first personal Life Operating System. **Jarvis** is its intelligence layer.

The current security release establishes a durable security boundary before connected AI, Health, Calendar, voice or cloud sync are introduced.

## Current product

- Today / Focus dashboard
- Capture + encrypted Inbox
- encrypted Tasks and Memories
- private reminders
- encrypted Daily Check-in
- persistent encrypted Jarvis Local conversation
- local context-aware Jarvis
- Security Center

## Security baseline

SOHEIL follows a defense-in-depth design aligned to OWASP MASVS categories.

- **Vault encryption:** AES-256-GCM authenticated encryption with fresh nonce per record.
- **Context binding:** GCM AAD binds ciphertext to record type, opaque record key and timestamp.
- **Key hierarchy:** random 256-bit Vault DEK wrapped by a user-authenticated Android Keystore KEK.
- **Hardware protection:** StrongBox requested where available; Android Keystore/TEE fallback otherwise.
- **Authentication:** AndroidX BiometricPrompt with strong biometric or device credential.
- **Session:** manual lock, background auto-lock, inactivity auto-lock, in-memory key zeroization.
- **Metadata minimization:** security-relevant task/state fields live inside encrypted authenticated payloads; exact private lookups use keyed blind indexes.
- **Screen privacy:** FLAG_SECURE, recents screenshot protection, overlay/tapjacking mitigations, autofill and IME personalized-learning restrictions.
- **Backup:** automatic cloud/device-transfer extraction of the Vault is disabled.
- **Notifications:** no private task content is placed on the lock screen.
- **Network:** current app has no INTERNET permission; cleartext HTTP is blocked by policy for future networking.
- **Jarvis:** remote domain access is default-deny; high-sensitivity domains require explicit per-request consent when connected AI is later introduced.
- **Secrets:** signing keys/API keys/private keys are prohibited from source control.
- **CI:** permission allowlist, security invariant gate, CodeQL, Dependabot, APK signature verification.

See [`docs/SECURITY_ARCHITECTURE.md`](docs/SECURITY_ARCHITECTURE.md) for the threat model and long-term invariants.

## Important security reality

No application can be made permanently immune to future vulnerabilities. Android, dependencies, cryptographic recommendations and attack techniques evolve. SOHEIL's architecture is designed so security controls remain centralized and inherited by future features, while vulnerability patches, dependency maintenance and key/certificate rotation remain mandatory security maintenance.

A fully compromised/rooted device while the Vault is actively unlocked is outside the guarantee boundary. Root indicators are advisory, not treated as an unbreakable defense.

SOHEIL is **not an EMR/EHR** and should not be used for identifiable patient records until a separate jurisdiction-specific regulatory/compliance architecture exists.

## Data recovery

Automatic portable backup is intentionally disabled because the local Keystore key is device-bound. Until a separate user-controlled encrypted export/recovery feature is implemented, uninstalling SOHEIL, clearing app data, factory-resetting or losing the device can make local Vault data unrecoverable.

## Builds

### Security-test build

GitHub Actions builds a non-debuggable **test-signed** APK. It is appropriate for functional/security testing on a real device, but the test signing certificate is not the permanent production identity.

### Production release

The release pipeline requires a dedicated SOHEIL signing keystore supplied only through CI secrets. Release builds are non-debuggable, R8-minified/shrunk and signature-verified. The private signing key must never be committed to Git.

Required CI secrets:

- `SOHEIL_KEYSTORE_B64`
- `SOHEIL_KEYSTORE_PASSWORD`
- `SOHEIL_KEY_ALIAS`
- `SOHEIL_KEY_PASSWORD`

## Future connected architecture

```text
User authentication
        ↓
Encrypted local SOHEIL Vault
        ↓
Permission / Jarvis Access policy
        ↓
Minimum Necessary Context selector
        ↓
Secure allowlisted HTTPS network layer
        ↓
SOHEIL backend (provider secrets server-side only)
        ↓
External AI/provider
```

Cloud sync and remote AI are separate security domains. Future sync should prefer client-side encrypted blobs; remote AI must never receive the entire Vault by default.

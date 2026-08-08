# Security Policy

SOHEIL treats security/privacy defects as release-blocking issues.

## Supported branch

`main` is the active security baseline. Production releases must pass the repository Security Gate and signature verification workflow.

## Reporting a vulnerability

Do **not** publish secrets, private user data, signing material, working exploits against real users, or sensitive vulnerability details in a public issue.

Preferred process:
1. privately notify the repository owner through an available private GitHub/security channel;
2. include affected version/commit, reproducible steps and impact;
3. provide proof-of-concept only with synthetic/non-user data;
4. allow remediation and validation before public disclosure.

## Security response

A credible vulnerability affecting confidentiality, integrity, authentication, key handling, remote access, backup/recovery, signing or supply chain blocks new feature releases until triaged.

Response priorities:
- revoke/rotate exposed credentials or keys where applicable;
- contain remote access if affected;
- patch centrally at the security boundary rather than feature-by-feature;
- add a regression check to `security/security_gate.sh` or automated analysis when practical;
- rebuild and verify a newly signed release;
- document any data-impact/notification obligations that apply to the deployment.

## Security invariants

See `docs/SECURITY_ARCHITECTURE.md`. Future features may extend security controls, but must not silently weaken the established default-deny, encrypted-vault, authenticated-session, least-privilege and minimum-context principles.

## No "forever secure" claim

Cryptography, Android, dependencies and the threat environment evolve. Dependency/CVE patches, signing/certificate rotation and security-response releases remain part of responsible maintenance even when the architecture itself does not need redesign.

# Security Policy

## Supported Versions

ErikrafT Drop™ for Android follows a rolling security-support policy. The **latest released Android version** is the primary supported version for security fixes.

| Version | Support Status |
| --- | --- |
| **10.1.x** | ✅ Supported |
| 10.0.x and older | ⚠️ Upgrade recommended / not guaranteed |

> **Current Android release:** `v10.1.5` (versionCode `28`)
>
> Security fixes may require users to update to the latest Android release. Older releases are not guaranteed to receive backported fixes.

## Reporting a Vulnerability

If you discover a security vulnerability in **ErikrafT Drop™ for Android**, please report it privately and responsibly. Do not disclose the vulnerability publicly before the maintainers have had a reasonable opportunity to investigate and release a fix.

### Security contact

- **Email:** [contact+security@erikraft.com](mailto:contact+security@erikraft.com)
- **Discord:** use the `#ticket` channel at [discord.erikraft.com](https://discord.erikraft.com/)

Use email whenever the report contains sensitive technical details, exploit information, credentials, private data, or other information that should not be posted publicly.

### Information to include

When possible, include:

- A clear description of the vulnerability.
- The affected ErikrafT Drop™ for Android version, Android version, device model, and relevant configuration.
- Steps to reproduce the issue.
- The expected and actual behavior.
- The potential security impact and attack scenario.
- A minimal proof of concept (PoC), if available and safe to provide.
- Relevant logs, screenshots, URLs, APK/AAB details, or files that help reproduce the issue. Remove secrets and personal information before sending them.
- Whether the vulnerability is reproducible in the latest release.

### Response and disclosure

We aim to acknowledge security reports within **5 business days** and will investigate reports as promptly as reasonably possible.

Please allow reasonable time for investigation, remediation, testing, and deployment before making vulnerability details public. We may request additional information during the investigation.

## Scope and Security Considerations

Security reports involving the Android client are especially valuable in areas such as:

- WebView and JavaScript/native bridge interactions.
- File selection, receiving, saving, sharing, and storage handling.
- Intents, deep links, exported components, and URI/FileProvider handling.
- P2P/WebRTC and WebSocket transfer handling.
- QR/Animated QR parsing and transfer integrity.
- FTP/FTPS server startup, authentication, authorization, TLS configuration, passive ports, and file access.
- Tor/.onion networking, SOCKS5 handling, and Lyrebird integration.
- Background services and notifications.
- Notification content or actions that could expose sensitive information or trigger unauthorized behavior.
- Native libraries and JNI boundaries, including security-sensitive interactions involving Tor/Lyrebird.
- Cryptographic or integrity checks used for transferred data.
- Any bypass that permits unauthorized access to files, transfers, settings, or privileged application behavior.

### Out of scope

Reports that do not demonstrate a security impact may be treated as general bugs rather than security vulnerabilities. Automated scans without a reproducible security impact, dependency notices without an exploitable path, and purely cosmetic issues are generally not considered security vulnerabilities.

## Responsible Disclosure

Please test only with devices, accounts, files, servers, and networks that you own or are authorized to use.

Do not access, modify, delete, or expose another person's data. Do not perform denial-of-service attacks, social engineering, spam, credential attacks, or actions that could disrupt users or infrastructure.

If testing requires interaction with another device or user, use a controlled test environment whenever possible.

Thank you for helping keep **ErikrafT Drop™ for Android** secure, private, and reliable.

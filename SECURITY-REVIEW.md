# Security Review

## Audit result

| # | Severity | File | Lines | Vulnerability | Confidence |
|---|----------|------|-------|---------------|------------|
| 1 | 🟡 MEDIUM | app/src/main/res/xml/network_security_config.xml | 3-6 | Globally trusted user-installed CAs and broad cleartext policy | 10/10 |
| 2 | 🟡 MEDIUM | app/src/main/java/com/erikraft/drop/JavaScriptInterface.java | 51, 82-89, 145-166 | Native WebView transfer could exceed the declared size or grow without a global bound | 9/10 |
| 3 | 🟡 MEDIUM | app/src/main/java/com/erikraft/drop/MainActivity.java | 273, 292 | WebView file access and third-party cookie acceptance expanded the local attack surface | 8/10 |
| 4 | ⚪ LOW | app/src/main/java/com/erikraft/drop/TftpServerService.java | 36, 137-150 | TFTP exposes a selected folder through an unauthenticated read-only server | 9/10 |
| 5 | ⚪ LOW | app/src/main/java/com/erikraft/drop/FtpServerService.java | 105-107, 136-165 | Plain FTP remains selectable in addition to FTPS; encryption status depends on persisted settings | 8/10 |
| 6 | ⚪ LOW | app/src/main/java/com/erikraft/drop/FtpServerService.java | 59, 157-158, 244-250 | The runtime FTPS keystore password is source-hardcoded | 7/10 |

## Reconnaissance

- Stack: Android app, Java 8 source compatibility, Gradle/AGP 8.13.2, Android SDK 36, WebView, NanoHTTPD, Apache FTP/FTPS, Apache SSHD/SFTP, TFTP, Tor, NFC and QR/Fountain transfer.
- No Supabase, Firebase, Stripe, Mercado Pago, AI API, SMS, email, WhatsApp, server actions or payment webhook implementation was found.
- Public/local surfaces include the exported `MainActivity`, NFC and Quick Settings components, custom HTTP/Tor/NanoHTTPD endpoints, FTP/FTPS, SFTP and TFTP services, and local FileProvider URIs.
- Authentication: the app is primarily local/device-based. There is no application account system. FTP/FTPS and SFTP authenticate with locally configured credentials; SFTP validates against those preferences at `SftpServerService.java:127-130`. TFTP and its read server have no authentication.
- Authorization: native bridge calls are scoped to a random per-launch token and the configured origin. See `MainActivity.java:349-359` and `OriginScopedJavaScriptInterface.java:35-38`.
- Sensitive data: personal files and text pass through the WebView/native bridge and local transfer servers. Credentials are stored in Android SharedPreferences. Signing credentials are supplied by environment or Gradle properties and are not hardcoded.
- Cost: no hosted AI, messaging or payment metered APIs were found. The TFTP implementation caps concurrent sessions at 4. WebView transfer handling now caps declared transfers at 10 GiB. The 4 CPU-hour limit of a specific hosting plan could not be confirmed from repository code.
- Secrets: no versioned keystore or private key was found, and the tracked history scan did not match common API-key/private-key patterns. GitHub Actions signing credentials are references to secrets. No secret values are reproduced here.

## Confirmed findings and corrections

### 1. User CA trust and broad cleartext trust

Evidence before:

```xml
<base-config cleartextTrafficPermitted="true">
    <trust-anchors tools:ignore="AcceptsUserCertificates">
        <certificates src="system" />
        <certificates src="user" />
    </trust-anchors>
</base-config>
```

This is proved by `app/src/main/res/xml/network_security_config.xml:3-6`. A network attacker or compromised user CA could intercept connections to HTTP endpoints or replace trust chains for supported HTTPS endpoints.

Correction:

```xml
<base-config cleartextTrafficPermitted="true">
    <trust-anchors>
        <certificates src="system" />
    </trust-anchors>
</base-config>
```

The application no longer trusts user-installed certificate authorities. Cleartext remains available for explicitly configured local/Tor transport paths, while public Snapdrop/PairDrop domains retain `cleartextTrafficPermitted="false"` in the existing domain block.

Commit: `9b30573 Harden WebView and network trust defaults`.

### 2. Native WebView transfer resource abuse

Evidence before:

`app/src/main/java/com/erikraft/drop/JavaScriptInterface.java:82-89` parsed the declared size but did not reject oversized declarations consistently. `app/src/main/java/com/erikraft/drop/JavaScriptInterface.java:145-155` decoded and wrote every Base64 chunk without a global or exact declared-size check.

Correction:

```java
private static final long MAX_TRANSFER_BYTES = 10L * 1024L * 1024L * 1024L;

static boolean isTransferChunkAllowed(final int chunkBytes, final long totalBytes,
                                      final long expectedBytes) {
    if (chunkBytes < 0 || totalBytes < 0) return false;
    if ((long) chunkBytes > MAX_TRANSFER_BYTES) return false;
    if (totalBytes > MAX_TRANSFER_BYTES - chunkBytes) return false;
    return expectedBytes < 0 || (long) chunkBytes <= expectedBytes - totalBytes;
}
```

This prevents a chunk or cumulative transfer from exceeding 10 GiB and prevents chunks from exceeding the declared file size.

Commit: `cde072b Bound native WebView file transfers`.

### 3. Expanded WebView file/cookie surface

Evidence before:

- `app/src/main/java/com/erikraft/drop/MainActivity.java:273`: `setAllowFileAccess(true)`
- `app/src/main/java/com/erikraft/drop/MainActivity.java:292`: `setAcceptThirdPartyCookies(binding.webview, true)`

Correction: file access and third-party cookies are now disabled.

Commit: `9b30573 Harden WebView and network trust defaults`.

## Probable findings

- **FTP plaintext mode:** `FtpServerService.java:105-107` reads `pref_ftp_ftps` and `pref_ftp_ftps_implicit`; `FtpServerService.java:154-165` starts TLS only when enabled. This is probable if the setting is disabled or has not migrated. Confirm by checking the persisted preference and the active server log/address.
- **SharedPreferences credentials:** FTP and SFTP passwords are read from and written to Android SharedPreferences. Device compromise or a malicious app with backup access is outside the repository's confirmed threat model; check for backup restrictions and any device-management exception in production builds.
- **FTP/SFTP brute force:** FTP/SFTP credentials are strong by default, but no application-level IP/user rate limiter exists. Confirm whether Apache FTP/SSHD connection or login throttling is applied by device policy, network controls or upstream Android firewall configuration.
- **Supply chain:** dependency lock resolution could not be fully audited because the local build runner has JDK 27 while this project's Gradle build rejects major class version 71. The repository uses Gradle and Android dependencies but no known vulnerable version is reported here without `npm audit`-equivalent evidence.

## Not found or not verifiable

- No IDOR/BOLA route or server-side resource-ID authorization model exists because this is a local Android app, not a user-owned backend.
- No mass-assignment endpoint, role/plan/credit field or backend ORM update was found.
- No payment webhook implementation or payment signature verification is present.
- No Supabase/Firebase RLS or Firebase Rules are present in the repository. Panel rules could not be verified because they are external and no provider files were found.
- No AI, SMS, email or WhatsApp metered endpoint was found. Hosted serverless Active CPU cannot be measured solely from this repository.
- No API, package or private signing secret was recovered from tracked files or Git history. The source-hardcoded FTPS keystore password is reported but is only an exposed implementation default, not a release signing secret.

## Five mandatory checks

1. **Rate limit/resource consumption:** FTP/SFTP rely on credentials and TFTP has a four-session cap. No login or connection throttle is implemented in the TFTP/FTP settings. WebView file transfer now has a global bound.
2. **IDOR/BOLA:** no backend resource-ID routes were found.
3. **Mass assignment:** no backend request-body-to-database update mechanism was found.
4. **Webhooks:** no payment provider webhook implementation was found.
5. **Database open access:** no Supabase/Firebase database configuration or rules exist in the repository.

## Analysis coverage

Analyzed:

- Gradle/build and GitHub Actions configuration
- Android manifest, permissions, exported components and FileProvider
- Network security policy
- MainActivity/WebView/native bridge origin controls
- FTP/FTPS, SFTP and TFTP servers
- Tor/NanoHTTPD transfer surface
- SharedPreferences credentials and tracked secret patterns
- Git history secret-pattern scan
- Unit and instrumentation test locations

Corrected:

- Removed user-installed CA trust
- Disabled WebView third-party cookies
- Disabled WebView file access
- Added transfer size and declared-size enforcement
- Added focused transfer-limit unit assertions

Not corrected:

- TFTP is intentionally unauthenticated and must be used only on trusted networks
- Plain FTP can still be explicitly selected
- The source-hardcoded FTPS keystore password is a runtime implementation default, not a signing secret
- External Supabase/Firebase RLS and hosted service configuration require panel access
- Dependency CVE status requires a runnable Gradle/JDK environment and a dependency audit

## Validation

Both baseline and post-fix commands were:

```text
./gradlew testMobileDebugUnitTest assembleMobileDebug --no-daemon
```

Both stopped before project compilation or tests with Gradle's `Unsupported class file major version 71`, because the environment uses JDK 27 and the project CI uses JDK 17. No project dependency or JDK was installed without authorization. The focused test was added at `app/src/test/java/com/erikraft/drop/FileIntegrityTest.java:15-19`, but it was not executable in this environment.

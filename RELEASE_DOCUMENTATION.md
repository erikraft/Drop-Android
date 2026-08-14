# ErikrafT Drop™ for Android — Release & Signing Documentation

This document explains how to build signed release artifacts (`.apk` and `.aab`) locally and in GitHub Actions.

## 1. Google Play App Signing and key usage

This project should sign release builds with the existing production **upload key** (not a newly generated key).

- **Upload key**: used by local builds and CI to sign the AAB/APK before upload.
- **App signing key**: managed by Google Play App Signing and used by Google to sign distributed APKs.

The CI-generated `ErikrafT-Drop-release.aab` is an upload artifact for Google Play Console.

> Never commit `.jks`, `.keystore`, `.p12`, or password files to this repository.

## 2. Required GitHub Actions secrets

Configure repository or environment secrets at:
**Settings → Secrets and variables → Actions → New repository secret**

Required secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded content of the release/upload keystore file (`.jks`). |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_ALIAS` | Alias of the release/upload key inside the keystore. |
| `ANDROID_KEY_PASSWORD` | Password of the key alias. |

The workflow decodes `ANDROID_KEYSTORE_BASE64` into a temporary file under `${{ runner.temp }}` and deletes it at the end of the job.

### Encode the keystore as Base64

- Linux/macOS:
  ```bash
  base64 -w 0 my-release-key.jks
  ```
- PowerShell:
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("my-release-key.jks"))
  ```

## 3. Local signed release build

Release builds require signing credentials. Use either environment variables or `keystore.properties` (already ignored by Git).

### Option A: environment variables

```bash
export KEYSTORE_PATH="/path/to/my-release-key.jks"
export KEYSTORE_PASSWORD="***"
export KEY_ALIAS="***"
export KEY_PASSWORD="***"

./gradlew clean assembleMobileRelease bundleMobileRelease
```

### Option B: `keystore.properties`

Create `/home/runner/work/Drop-Android/Drop-Android/keystore.properties`:

```properties
storeFile=/path/to/my-release-key.jks
storePassword=***
keyAlias=***
keyPassword=***
```

Then run:

```bash
./gradlew clean assembleMobileRelease bundleMobileRelease
```

## 4. Versioning before release

Before creating a Play Store release, update both values in `/home/runner/work/Drop-Android/Drop-Android/app/build.gradle`:

- `versionCode` must always increase for every Play update.
- `versionName` should match the intended release tag (for example `v9.0.8`).

Never publish two Play releases with the same `versionCode`.

## 5. CI release behavior

Workflow file: `/home/runner/work/Drop-Android/Drop-Android/.github/workflows/release.yml`

Triggers:
- push tags matching `v*`
- manual `workflow_dispatch`

Pipeline steps:
1. run `./gradlew test`
2. decode temporary keystore from `ANDROID_KEYSTORE_BASE64`
3. run `./gradlew clean assembleMobileRelease bundleMobileRelease`
4. verify APK/AAB existence and non-zero size
5. verify APK signing with `apksigner`
6. verify AAB signing with `jarsigner`
7. upload:
   - `erikraft-drop-apk` → `ErikrafT-Drop-release.apk`
   - `erikraft-drop-aab` → `ErikrafT-Drop-release.aab`
8. when triggered by `v*` tag, create a GitHub Release and attach:
   - `ErikrafT-Drop-vX.Y.Z.apk`
   - `ErikrafT-Drop-vX.Y.Z.aab`

## 6. Target API check

Current Android configuration in `app/build.gradle` uses:
- `compileSdk 35`
- `targetSdkVersion 35`

Keep this aligned with Google Play requirements before each release window.

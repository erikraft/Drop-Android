# ErikrafT Drop Public API Integration (Android)

This document defines the Android-side boundary for the public ErikrafT Drop API initiative. It is a compatibility contract and implementation plan, not a claim that a native Android SDK already exists.

## Current architecture

The Android application loads the Drop web client in a WebView and uses the app-owned `JavaScriptInterface` for specific native operations such as saving received files. That bridge is an internal implementation detail of the official application. Third-party applications must not be granted direct access to the bridge or its methods.

The shared public API is hosted by the existing Drop service:

- Capabilities: `GET https://drop.erikraft.com/api/v1/capabilities`
- Existing CLI metadata: `GET https://drop.erikraft.com/api/cli`
- Existing signaling: `wss://drop.erikraft.com/server` (protocol details remain defined by the Web/Desktop repository)

The capabilities endpoint is read-only discovery metadata. It is not a REST endpoint for sending, accepting, or downloading files.

## Android integration rules

1. Keep the WebView bridge private to the official application and expose only the minimum native methods required by the web client.
2. Do not expose arbitrary Java method invocation, filesystem paths, shell commands, received code execution, or raw credentials to web content.
3. Any future public native SDK must use a separate, explicitly designed API surface rather than reusing `JavaScriptInterface` as a third-party interface.
4. Require explicit user approval before accepting incoming transfers or granting filesystem access.
5. Preserve Android storage permissions, scoped storage behavior, FileProvider rules, and existing download destination behavior.
6. Keep the public protocol compatible with Web/Desktop; do not implement a second transfer protocol just for Android.
7. Treat server capability flags as declarations, not proof that a given Android/WebView version supports every feature.

## Planned implementation order

1. Stabilize the public API v1 metadata and documented error/compatibility rules in the Web/Desktop repository.
2. Add shared contract tests for capability discovery and WebSocket protocol compatibility.
3. Design a public Android SDK facade only after the SDK method names, lifecycle, cancellation, progress callbacks, and consent model are specified.
4. Validate the implementation on supported Android versions and mobile, TV, and XR flavors before declaring SDK support.

## Validation requirements for a future SDK

- No public SDK method should bypass user consent for incoming files.
- Cancellation and error paths must release streams and listeners.
- Progress callbacks must be monotonic and completion must be emitted at most once.
- API keys, pairing secrets, and file contents must never be written to logs.
- Tests must cover WebView/native bridge boundaries and the current transfer flow.
- New user-facing strings must use the existing localization system. Add translations in Portuguese, English, and all other app-supported languages when UI strings are introduced.

## Status

The existing app bridge remains unchanged. This document records the intended boundary while the Web/Desktop public API contract is being established.

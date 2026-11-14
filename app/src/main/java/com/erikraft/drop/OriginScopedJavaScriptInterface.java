package com.erikraft.drop;

import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Exposes the native bridge through a per-WebView capability token.
 * The raw JavaScript interface is injected into all frames by WebView, so
 * every call must carry a token only provided to the configured origin.
 */
public final class OriginScopedJavaScriptInterface {
    private static final String TAG = "DropAndroidBridge";
    private static final int MAX_ARGUMENT_JSON_CHARS = 1024 * 1024;

    private final JavaScriptInterface delegate;
    private final byte[] expectedToken;

    OriginScopedJavaScriptInterface(final JavaScriptInterface delegate, final String token) {
        if (delegate == null || token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Bridge delegate and token are required");
        }
        this.delegate = delegate;
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @JavascriptInterface
    public String invoke(final String token, final String method, final String argsJson) {
        if (!isAuthorized(token) || method == null || argsJson == null
                || argsJson.length() > MAX_ARGUMENT_JSON_CHARS) {
            return "null";
        }

        try {
            final JSONArray args = new JSONArray(argsJson);
            final Object result;
            switch (method) {
                case "newFile":
                    requireArgs(args, 3);
                    delegate.newFile(stringArg(args, 0), stringArg(args, 1), stringArg(args, 2));
                    result = null;
                    break;
                case "downloadBase64File":
                    requireArgs(args, 3);
                    delegate.downloadBase64File(stringArg(args, 0), stringArg(args, 1), stringArg(args, 2));
                    result = null;
                    break;
                case "onBytes":
                    requireArgs(args, 1);
                    delegate.onBytes(requiredStringArg(args, 0));
                    result = null;
                    break;
                case "saveDownloadFileName":
                    requireArgs(args, 2);
                    delegate.saveDownloadFileName(stringArg(args, 0), stringArg(args, 1));
                    result = null;
                    break;
                case "notifyMention":
                    requireArgs(args, 2);
                    delegate.notifyMention(stringArg(args, 0), stringArg(args, 1));
                    result = null;
                    break;
                case "copyToClipboard":
                    requireArgs(args, 1);
                    delegate.copyToClipboard(stringArg(args, 0));
                    result = null;
                    break;
                case "getClipboardText":
                    requireArgs(args, 0);
                    result = delegate.getClipboardText();
                    break;
                case "requestExternalShare":
                    requireArgs(args, 3);
                    delegate.requestExternalShare(stringArg(args, 0), stringArg(args, 1), stringArg(args, 2));
                    result = null;
                    break;
                case "openOnionTransfer":
                    requireArgs(args, 0);
                    delegate.openOnionTransfer();
                    result = null;
                    break;
                case "openFtpSettings":
                    requireArgs(args, 0);
                    delegate.openFtpSettings();
                    result = null;
                    break;
                case "openSftpSettings":
                    requireArgs(args, 0);
                    delegate.openSftpSettings();
                    result = null;
                    break;
                case "setKeepScreenOn":
                    requireArgs(args, 1);
                    delegate.setKeepScreenOn(booleanArg(args, 0));
                    result = null;
                    break;
                case "isKeepScreenOnEnabled":
                    requireArgs(args, 0);
                    result = delegate.isKeepScreenOnEnabled();
                    break;
                case "getPreferredWebsiteLocale":
                    requireArgs(args, 0);
                    result = delegate.getPreferredWebsiteLocale();
                    break;
                case "setDialogVisible":
                    requireArgs(args, 1);
                    delegate.setDialogVisible(booleanArg(args, 0));
                    result = null;
                    break;
                case "getYouAreKnownAsTranslationString":
                    requireArgs(args, 1);
                    result = delegate.getYouAreKnownAsTranslationString(requiredStringArg(args, 0));
                    break;
                case "getVersionId":
                    requireArgs(args, 0);
                    result = delegate.getVersionId();
                    break;
                case "getTextFromUploadIntent":
                    requireArgs(args, 0);
                    result = delegate.getTextFromUploadIntent();
                    break;
                case "shouldOpenSendTextDialog":
                    requireArgs(args, 0);
                    result = delegate.shouldOpenSendTextDialog();
                    break;
                case "dialogShown":
                    requireArgs(args, 0);
                    delegate.dialogShown();
                    result = null;
                    break;
                case "dialogHidden":
                    requireArgs(args, 0);
                    delegate.dialogHidden();
                    result = null;
                    break;
                case "ignoreClickedListener":
                    requireArgs(args, 0);
                    delegate.ignoreClickedListener();
                    result = null;
                    break;
                case "setProgress":
                    requireArgs(args, 1);
                    delegate.setProgress(floatArg(args, 0));
                    result = null;
                    break;
                case "vibrate":
                    requireArgs(args, 0);
                    delegate.vibrate();
                    result = null;
                    break;
                case "resetUploadIntent":
                    requireArgs(args, 0);
                    delegate.resetUploadIntent();
                    result = null;
                    break;
                default:
                    return "null";
            }
            return encodeResult(result);
        } catch (JSONException | IOException | IllegalArgumentException e) {
            Log.w(TAG, "Rejected invalid native bridge call: " + method, e);
            return encodeError();
        } catch (RuntimeException e) {
            Log.w(TAG, "Native bridge call failed: " + method, e);
            return encodeError();
        }
    }

    private boolean isAuthorized(final String token) {
        return token != null && MessageDigest.isEqual(
                expectedToken,
                token.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void requireArgs(final JSONArray args, final int expected) throws JSONException {
        if (args.length() != expected) {
            throw new JSONException("Invalid argument count");
        }
    }

    private static String stringArg(final JSONArray args, final int index) throws JSONException {
        final Object value = args.get(index);
        if (value == JSONObject.NULL) return null;
        if (value instanceof String) return (String) value;
        // Preserve the legacy JavascriptInterface coercion used by file-size values.
        if (value instanceof Number) return value.toString();
        throw new JSONException("Expected string-compatible argument");
    }

    private static String requiredStringArg(final JSONArray args, final int index) throws JSONException {
        final String value = stringArg(args, index);
        if (value == null) throw new JSONException("Expected non-null string argument");
        return value;
    }

    private static boolean booleanArg(final JSONArray args, final int index) throws JSONException {
        final Object value = args.get(index);
        if (!(value instanceof Boolean)) throw new JSONException("Expected boolean argument");
        return (Boolean) value;
    }

    private static float floatArg(final JSONArray args, final int index) throws JSONException {
        final Object value = args.get(index);
        if (!(value instanceof Number)) throw new JSONException("Expected numeric argument");
        final float number = ((Number) value).floatValue();
        if (Float.isNaN(number) || Float.isInfinite(number)) {
            throw new JSONException("Expected finite numeric argument");
        }
        return number;
    }

    private static String encodeError() {
        return "{\"__bridgeError\":true}";
    }

    private static String encodeResult(final Object result) {
        if (result == null) return "null";
        if (result instanceof String) return JSONObject.quote((String) result);
        if (result instanceof Boolean || result instanceof Number) return result.toString();
        return "null";
    }
}

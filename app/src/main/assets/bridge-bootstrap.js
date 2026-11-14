(function installOriginScopedDropBridge() {
    'use strict';

    const token = __ERIKRAFT_BRIDGE_TOKEN__;
    const nativeBridge = window.__ErikrafTdropAndroidNative;
    if (!nativeBridge || typeof nativeBridge.invoke !== 'function') return;

    const methods = new Set([
        'newFile',
        'downloadBase64File',
        'onBytes',
        'saveDownloadFileName',
        'notifyMention',
        'copyToClipboard',
        'getClipboardText',
        'requestExternalShare',
        'openOnionTransfer',
        'openFtpSettings',
        'openSftpSettings',
        'setKeepScreenOn',
        'isKeepScreenOnEnabled',
        'getPreferredWebsiteLocale',
        'setDialogVisible',
        'getYouAreKnownAsTranslationString',
        'getVersionId',
        'getTextFromUploadIntent',
        'shouldOpenSendTextDialog',
        'dialogShown',
        'dialogHidden',
        'ignoreClickedListener',
        'setProgress',
        'vibrate',
        'resetUploadIntent'
    ]);

    const bridge = new Proxy(Object.create(null), {
        get: function (_target, property) {
            if (property === 'then') return undefined;
            if (typeof property !== 'string' || !methods.has(property)) return undefined;
            return function () {
                const args = Array.prototype.slice.call(arguments);
                const result = nativeBridge.invoke(token, property, JSON.stringify(args));
                try {
                    return JSON.parse(result);
                } catch (_error) {
                    return null;
                }
            };
        },
        has: function (_target, property) {
            return typeof property === 'string' && methods.has(property);
        }
    });

    Object.defineProperty(window, 'ErikrafTdropAndroid', {
        configurable: false,
        enumerable: false,
        writable: false,
        value: bridge
    });
})();

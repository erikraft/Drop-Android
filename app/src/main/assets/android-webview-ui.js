(function applyAndroidWebViewUi() {
    'use strict';

    const styleId = 'erikraft-android-webview-ui';
    if (document.getElementById(styleId)) return;

    function initialize() {
        if (document.getElementById(styleId)) return;
        const style = document.createElement('style');
        style.id = styleId;
        style.textContent = [
            'a.icon-button[href="#about"]',
            '#language-selector',
            '#theme-auto'
        ].join(',\n') + ' { display: none !important; }';
        (document.head || document.documentElement).appendChild(style);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize, { once: true });
    } else {
        initialize();
    }
})();

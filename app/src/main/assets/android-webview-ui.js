(function applyAndroidWebViewUi() {
    'use strict';

    const selectors = [
        'a.icon-button[href="#about"]',
        '#language-selector',
        '#theme-auto'
    ];

    function hide() {
        selectors.forEach(selector => {
            document.querySelectorAll(selector).forEach(element => {
                element.style.setProperty('display', 'none', 'important');
                element.setAttribute('aria-hidden', 'true');
                element.setAttribute('data-erikraft-android-hidden', 'true');
            });
        });
    }

    function initialize() {
        hide();
        const observer = new MutationObserver(hide);
        observer.observe(document.documentElement, { childList: true, subtree: true });
        window.setTimeout(() => observer.disconnect(), 10000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize, { once: true });
    } else {
        initialize();
    }
})();

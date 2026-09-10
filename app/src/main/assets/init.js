/*
  Initialises the website as needed for this app (GUI and JS changes)
  Good practice:
    - We should always try to touch the js as little as possible (add code instead of overriding) not to break anything should there be minor changes to the website
    - There are many Snapdrop forks with minor tweaks, so put every independent code part into a try-catch
*/

//change ReceiveTextDialog._onCopy to connect to JavaScriptInterface (don't call super method as it will throw an NotAllowedError)
try {
    ReceiveTextDialog.prototype._onCopy = function () {
        ErikrafTdropAndroid.copyToClipboard(this.$text.textContent);
        Events.fire('notify-user', 'Copied to clipboard');
    };
} catch (e) {
    console.error(e);
}

//change PeerUI.setProgress(progress) to connect to JavaScriptInterface
try {
    PeerUI.prototype.sP = PeerUI.prototype.setProgress;
    PeerUI.prototype.setProgress = function (progress) {
        ErikrafTdropAndroid.setProgress(progress);
        this.sP(progress);
    };
} catch (e) {
    console.error(e);
}

//change tweet link
try {
    document.querySelector('.icon-button[title~="Tweet"]').href = 'https://x.com/ErikrafTbr';
} catch (e) {
    console.error(e);
}

//add footer to about page
try {
    let websiteLinkDiv = document.createElement('div');
    websiteLinkDiv.style.cssText = 'height:10%; position:absolute; bottom:0px;';

    let websiteLink = document.createElement('a');
    websiteLink.href = '/';
    websiteLink.target = '_blank';

    let websiteLinkText = document.createElement('h4');
    websiteLinkText.style.cssText = 'font-size: 16px; font-weight: 400; letter-spacing: .5em; margin: 16px 0;';
    websiteLinkText.innerText = window.location.host.toUpperCase();

    let aboutScreen = document.querySelector('#about>section');
    websiteLinkDiv.appendChild(websiteLink);
    websiteLink.appendChild(websiteLinkText);
    aboutScreen.appendChild(websiteLinkDiv);
} catch (e) {
    console.error(e);
}

//retarget donation button (play guidelines)
try {
    document.querySelector('.icon-button[href*="paypal"]').href = 'https://ko-fi.com/erikraft/';
} catch (e) {
    console.error(e);
}

//remove "safari hack"
try {
    document.body.onclick = null;
} catch (e) {
    console.error(e);
}

//avoid text overflow (receive dialog) - might not be necessary anymore (see #393)
try {
    document.getElementById('fileName').style.textOverflow = 'ellipsis';
    document.getElementById('fileName').style.overflow = 'hidden';
} catch (e) {
    console.error(e);
}

//move about background to the left side (for better opening animation)
try {
    let aboutBackground = document.querySelector('#about>x-background');
    aboutBackground.style.top = 'calc(-32px - 250px)';
    aboutBackground.style.left = 'calc(32px - 250px)';
    aboutBackground.style.right = null;
} catch (e) {
    console.error(e);
}

//change PeerUI._onTouchEnd(e) to connect to JavaScriptInterface
try {
    PeerUI.prototype._oTE = PeerUI.prototype._onTouchEnd;
    PeerUI.prototype._onTouchEnd = function (e) {
        this._oTE(e);
        if ((Date.now() - this._touchStart < 500) && SnapdropAndroid.shouldOpenSendTextDialog()) {
            if (document.querySelector('meta[name="application-name"]')?.content == "PairDrop") {
                // no fix yet, cause they have changed the data format
            } else {
                Events.fire('text-recipient', this._peer.id);
            }
        }
    };
} catch (e) {
    console.error(e);
}

//catch chunks
try {
    Peer.prototype._oFH = Peer.prototype._onFileHeader;
    Peer.prototype._onFileHeader = function (header) {
        SnapdropAndroid.newFile(header.name, header.mime, header.size);
        this._oFH(header);
    };

    Peer.prototype._oCR = Peer.prototype._onChunkReceived;
    Peer.prototype._onChunkReceived = function (chunk) {
        let bytes = new Uint8Array(chunk);
        let binary = '';
        let len = bytes.byteLength;
        for (let i = 0; i < len; i += 8192) {
            let sub = bytes.subarray(i, Math.min(i + 8192, len));
            binary += String.fromCharCode.apply(null, sub);
        }
        let base64 = btoa(binary);
        SnapdropAndroid.onBytes(base64);
        this._oCR(chunk);
    };
} catch (e) {
    console.error(e);
}

// ensure Android JS bridge is available under a common name when possible
try {
    if (typeof ErikrafTdropAndroid !== 'undefined' && typeof SnapdropAndroid === 'undefined') {
        window.SnapdropAndroid = ErikrafTdropAndroid;
    }
} catch (e) {
    console.error(e);
}

//detect dialogs
try {
    const _androidBridge = (typeof ErikrafTdropAndroid !== 'undefined') ? ErikrafTdropAndroid : (typeof SnapdropAndroid !== 'undefined' ? SnapdropAndroid : null);
    if (_androidBridge) {
        if (!Dialog.prototype._shw) Dialog.prototype._shw = Dialog.prototype.show;
        Dialog.prototype.show = function () {
            try { _androidBridge.dialogShown(); } catch (e) { /* ignore bridge errors */ }
            this._shw();
        };

        if (!Dialog.prototype._hde) Dialog.prototype._hde = Dialog.prototype.hide;
        Dialog.prototype.hide = function () {
            try { _androidBridge.dialogHidden(); } catch (e) { /* ignore bridge errors */ }
            this._hde();
        };
    }
} catch (e) {
    console.error(e);
}

//register ignoreClickedListener
try {
    let downloadCancel = document.querySelector("#receiveDialog>x-background>x-paper>div.row-reverse>button");
    downloadCancel.addEventListener("click", function () { SnapdropAndroid.ignoreClickedListener(); });
} catch (e) {
    console.error(e);
}

//show localized display name
try {
    const localizationBuiltIn = !!document.getElementById('language-selector');

    if (!localizationBuiltIn) {
        let localizeDisplayName = function (str) {
            const displayNameNode = document.getElementById('displayName');
            if (displayNameNode.textContent.substring(0, 17) === "You are known as ") {
                displayNameNode.textContent = SnapdropAndroid.getYouAreKnownAsTranslationString(str);
            }
        };

        window.addEventListener('display-name', e => window.setTimeout(_ => localizeDisplayName(e.detail.message.displayName), 100), false);

        let currentText = document.getElementById('displayName').textContent;
        if (currentText.startsWith("You are known as ")) {
            localizeDisplayName(currentText.split("You are known as ")[1]);
        }
    }
} catch (e) {
    console.error(e);
}

window.addEventListener('file-received', e => {
    SnapdropAndroid.saveDownloadFileName(e.detail.name, e.detail.size);
}, false);

window.addEventListener('files-received', e => {
    SnapdropAndroid.vibrate();
}, false);

window.addEventListener('files-sent', e => {
    SnapdropAndroid.vibrate();
}, false);

window.addEventListener('share-mode-changed', e => {
    if (!e.detail.active) {
        SnapdropAndroid.resetUploadIntent();
    }
}, false);

// Android WebView does not reliably handle blob:/data: downloads by itself.
// Route download anchors through the native bridge so WebChat images, text files,
// compressed images, metadata exports, and other client-generated downloads can be saved.
try {
    const androidDownloadBridge = (typeof ErikrafTdropAndroid !== 'undefined')
        ? ErikrafTdropAndroid
        : (typeof SnapdropAndroid !== 'undefined' ? SnapdropAndroid : null);

    if (androidDownloadBridge && !window.__erikraftAndroidDownloadBridgeInstalled) {
        window.__erikraftAndroidDownloadBridgeInstalled = true;

        const downloadAnchor = async (anchor, event) => {
            const href = anchor && (anchor.href || anchor.getAttribute('href'));
            if (!anchor || !anchor.hasAttribute('download') || !href) return false;
            if (!href.startsWith('blob:') && !href.startsWith('data:') && !href.startsWith('http:') && !href.startsWith('https:')) return false;

            if (event) {
                event.preventDefault();
                event.stopImmediatePropagation();
            }

            try {
                const response = await fetch(href, { credentials: 'include' });
                if (!response.ok) throw new Error('HTTP ' + response.status);
                const blob = await response.blob();
                // Keep each bridge call bounded. Passing an entire Blob as one Base64 string can
                // exceed a WebView binder/heap limit and makes large WebTorrent downloads fail.
                const name = anchor.getAttribute('download') || 'download';
                const mime = blob.type || 'application/octet-stream';
                const chunkSize = 192 * 1024; // Base64 is about 256 KiB per bridge invocation.
                androidDownloadBridge.newFile(name, mime, String(blob.size));
                for (let offset = 0; offset < blob.size; offset += chunkSize) {
                    const buffer = await blob.slice(offset, offset + chunkSize).arrayBuffer();
                    const bytes = new Uint8Array(buffer);
                    let binary = '';
                    for (let index = 0; index < bytes.length; index += 0x8000) {
                        binary += String.fromCharCode.apply(null, bytes.subarray(index, index + 0x8000));
                    }
                    androidDownloadBridge.onBytes(btoa(binary));
                }
                androidDownloadBridge.saveDownloadFileName(name, String(blob.size));
                return true;
            } catch (error) {
                // Discard a partially written file if a chunk conversion or native write fails.
                if (typeof androidDownloadBridge.ignoreClickedListener === 'function') {
                    androidDownloadBridge.ignoreClickedListener();
                }
                console.error('Android WebView download bridge failed', error);
                return false;
            }
        };

        document.addEventListener('click', event => {
            const target = event.target instanceof Element ? event.target.closest('a[download]') : null;
            if (target) {
                const href = target.href || target.getAttribute('href') || '';
                if (href.startsWith('blob:') || href.startsWith('data:') || href.startsWith('http:') || href.startsWith('https:')) {
                    downloadAnchor(target, event);
                }
            }
        }, true);

        const originalAnchorClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function () {
            const href = this.href || this.getAttribute('href') || '';
            if (this.hasAttribute('download') && (href.startsWith('blob:') || href.startsWith('data:') || href.startsWith('http:') || href.startsWith('https:'))) {
                downloadAnchor(this, null);
                return;
            }
            return originalAnchorClick.apply(this, arguments);
        };
    }
} catch (e) {
    console.error('Unable to install Android WebView download bridge', e);
}

// Route WebView clipboard writes through Android when available. This covers
// SHA-256 copy buttons and other client dialogs that use navigator.clipboard.writeText.
try {
    const androidClipboardBridge = (typeof ErikrafTdropAndroid !== 'undefined')
        ? ErikrafTdropAndroid
        : (typeof SnapdropAndroid !== 'undefined' ? SnapdropAndroid : null);
    if (androidClipboardBridge && navigator.clipboard && typeof navigator.clipboard.writeText === 'function'
            && !navigator.clipboard.__erikraftAndroidBridge) {
        const originalWriteText = navigator.clipboard.writeText.bind(navigator.clipboard);
        const androidWriteText = text => {
            try {
                androidClipboardBridge.copyToClipboard(String(text ?? ''));
                return Promise.resolve();
            } catch (error) {
                return originalWriteText(text);
            }
        };
        androidWriteText.__erikraftAndroidBridge = true;
        navigator.clipboard.writeText = androidWriteText;
    }
} catch (e) {
    console.error('Unable to install Android clipboard bridge', e);
}

//hide unnecessary web toolbar buttons
try {
    document.querySelector('#theme').style.display = "none";
} catch (e) {
    console.error(e);
}
try {
    document.querySelector('#theme-wrapper').style.display = "none";
    localStorage.removeItem('theme');
    document.body.classList.remove('dark-theme');
    document.body.classList.remove('light-theme');
} catch (e) {
    console.error(e);
}
try {
    document.getElementById('language-selector').style.display = "none";
} catch (e) {
    console.error(e);
}
try {
    document.getElementById('expand').style.display = "none";
} catch (e) {
    console.error(e);
}

// Android WebView: About is exposed by the native action bar, so keep only one entry point.
try {
    if (!window.__erikraftHideNativeAbout) {
        window.__erikraftHideNativeAbout = true;
        const aboutButton = document.querySelector('body > header a[href="#about"]');
        if (aboutButton) aboutButton.style.display = 'none';
    }
} catch (e) { console.error(e); }

// QR controls: ensure a user gesture reaches getUserMedia so the native WebView camera permission dialog is shown.
try {
    const askCamera = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
            stream.getTracks().forEach(track => track.stop());
        } catch (e) { console.warn('Camera permission/request was not granted', e); }
    };
    ['#openQRScanner', '#animated-qr-btn'].forEach(selector => {
        const node = document.querySelector(selector);
        if (node && !node.dataset.erikraftCameraPermissionHooked) {
            node.dataset.erikraftCameraPermissionHooked = 'true';
            node.addEventListener('click', () => { askCamera(); }, { capture: true });
        }
    });
} catch (e) { console.error(e); }

// Replace the QR scanner icon with the requested Material icon path while preserving the existing button behavior.
try {
    const scanner = document.querySelector('#openQRScanner svg');
    if (scanner) {
        scanner.setAttribute('viewBox', '0 -960 960 960');
        scanner.setAttribute('width', '24');
        scanner.setAttribute('height', '24');
        scanner.innerHTML = '<path d="M120-680q-17 0-28.5-11.5T80-720v-120q0-17 11.5-28.5T120-880h120q17 0 28.5 11.5T280-840q0 17-11.5 28.5T240-800h-80v80q0 17-11.5 28.5T120-680Zm0 600q-17 0-28.5-11.5T80-120v-120q0-17 11.5-28.5T120-280q17 0 28.5 11.5T160-240v80h80q17 0 28.5 11.5T280-120q0 17-11.5 28.5T240-80H120Zm600 0q-17 0-28.5-11.5T680-120q0-17 11.5-28.5T720-160h80v-80q0-17 11.5-28.5T840-280q17 0 28.5 11.5T880-240v120q0 17-11.5 28.5T840-80H720Zm91.5-611.5Q800-703 800-720v-80h-80q-17 0-28.5-11.5T680-840q0-17 11.5-28.5T720-880h120q17 0 28.5 11.5T880-840v120q0 17-11.5 28.5T840-680q-17 0-28.5-11.5ZM700-200v-60h60v60h-60Zm0-120v-60h60v60h-60Zm-60 60v-60h60v60h-60Zm-60 60v-60h60v60h-60Zm-60-60v-60h60v60h-60Zm120-120v-60h60v60h-60Zm-60 60v-60h60v60h-60Zm-60-60v-60h60v60h-60Zm40-140q-17 0-28.5-11.5T520-560v-160q0-17 11.5-28.5T560-760h160q17 0 28.5 11.5T760-720v160q0 17-11.5 28.5T720-520H560ZM240-200q-17 0-28.5-11.5T200-240v-160q0-17 11.5-28.5T240-440h160q17 0 28.5 11.5T440-400v160q0 17-11.5 28.5T400-200H240Zm0-320q-17 0-28.5-11.5T200-560v-160q0-17 11.5-28.5T240-760h160q17 0 28.5 11.5T440-720v160q0 17-11.5 28.5T400-520H240Zm20 260h120v-120H260v120Zm0-320h120v-120H260v120Zm320 0h120v-120H580v120Z" fill="currentColor"/>';
    }
} catch (e) { console.error(e); }

// Do not wipe the native light/dark selection. MainActivity controls WebView force-dark explicitly.
try {
    localStorage.removeItem('theme');
} catch (e) { console.error(e); }

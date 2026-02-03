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
    Peer.prototype._onFileHeader = function (header) {
        this._isToAndroidBase64 = header.mime.startsWith("text/") || header.name.toLowerCase().endsWith(".txt");
        let mimeInfo = this._isToAndroidBase64 ? "base64:" + header.mime : header.mime;
        SnapdropAndroid.newFile(header.name, mimeInfo, header.size);
        this._oFH(header);
    };

    Peer.prototype._onChunkReceived = function (chunk) {
        let decoder = new TextDecoder('iso-8859-1');
        let rawString = decoder.decode(chunk);

        if (this._isToAndroidBase64) {
            SnapdropAndroid.onBytes(btoa(rawString));
        } else {
            SnapdropAndroid.onBytes(rawString);
        }
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
    // Only override dialog methods when an Android bridge exists. Preserve originals if present.
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
            // don't change it e.g. for pairdrop.net
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
    // vibrates after receiving all files (supported only on PairDrop)
    SnapdropAndroid.vibrate();
}, false);

window.addEventListener('files-sent', e => {
    // vibrates after sending all files (supported only on PairDrop)
    SnapdropAndroid.vibrate();
}, false);

window.addEventListener('share-mode-changed', e => {
    // remove upload intent on canceling share mode (supported only on PairDrop)
    if (!e.detail.active) {
        SnapdropAndroid.resetUploadIntent();
    }
}, false);

//hide unnecessary web toolbar buttons
try {
    // snapdrop.net - theme
    document.querySelector('#theme').style.display = "none";
} catch (e) {
    console.error(e);
}
try {
    // pairdrop.net - theme
    document.querySelector('#theme-wrapper').style.display = "none";
    localStorage.removeItem('theme');
    document.body.classList.remove('dark-theme');
    document.body.classList.remove('light-theme');
} catch (e) {
    console.error(e);
}
try {
    // remove pairdrop language selector
    document.getElementById('language-selector').style.display = "none";
} catch (e) {
    console.error(e);
}
try {
    // remove pairdrop overflow menu
    document.getElementById('expand').style.display = "none";
} catch (e) {
    console.error(e);
}
try {
    // other items
    document.querySelector('.icon-button[href="#about"]').style.display = "none";
    document.querySelector('.icon-button[href="#"]').style.display = "none";
} catch (e) {
    console.error(e);
}

// Android WebView fallback for chat room select (custom picker overlay)
try {
    const chatRoomSelect = document.getElementById('chat-room-select');
    if (chatRoomSelect && (typeof SnapdropAndroid !== 'undefined' || typeof ErikrafTdropAndroid !== 'undefined')) {
        const overlayId = 'android-chat-room-picker';
        let overlay = document.getElementById(overlayId);

        const buildOverlay = () => {
            if (overlay) return overlay;
            overlay = document.createElement('div');
            overlay.id = overlayId;
            overlay.style.cssText = [
                'position:fixed',
                'inset:0',
                'z-index:9999',
                'background:rgba(0,0,0,0.45)',
                'display:none',
                'align-items:flex-end',
                'justify-content:center'
            ].join(';');

            const sheet = document.createElement('div');
            // Derive colors from existing CSS variables when possible
            const rootStyle = getComputedStyle(document.documentElement);
            const dialogBg = rootStyle.getPropertyValue('--dialog-bg-color')?.trim();
            const textColor = rootStyle.getPropertyValue('--text-color')?.trim();
            const shadowRgb = rootStyle.getPropertyValue('--shadow-color-dialog-rgb')?.trim();
            const surface = dialogBg || (document.body.classList.contains('dark-theme') ? '#1f1f1f' : '#ffffff');
            const text = textColor ? `rgb(${textColor})` : (document.body.classList.contains('dark-theme') ? '#ffffff' : '#111111');
            const shadow = shadowRgb ? `rgba(${shadowRgb}, 0.25)` : 'rgba(0,0,0,0.25)';

            sheet.style.cssText = [
                'width:min(520px, 92vw)',
                'margin:16px',
                `background:${surface}`,
                `color:${text}`,
                'border-radius:16px',
                `box-shadow:0 12px 24px ${shadow}`,
                'padding:12px',
                'max-height:70vh',
                'overflow:auto'
            ].join(';');

            const title = document.createElement('div');
            title.style.cssText = 'font-weight:600;font-size:14px;margin:4px 8px 8px;';
            title.textContent = (typeof Localization !== 'undefined' && Localization.getTranslation)
                ? Localization.getTranslation('chat.room_select_title') || 'Select room'
                : 'Select room';

            const list = document.createElement('div');
            list.style.cssText = 'display:flex;flex-direction:column;gap:6px;';
            list.id = 'android-chat-room-picker-list';

            sheet.appendChild(title);
            sheet.appendChild(list);
            overlay.appendChild(sheet);
            overlay.addEventListener('click', e => {
                if (e.target === overlay) {
                    overlay.style.display = 'none';
                }
            });
            document.body.appendChild(overlay);
            return overlay;
        };

        const buildList = () => {
            const list = document.getElementById('android-chat-room-picker-list');
            if (!list) return;
            list.innerHTML = '';
            const options = Array.from(chatRoomSelect.options || []);
            options.forEach(opt => {
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.textContent = opt.textContent || opt.label || opt.value;
                btn.style.cssText = [
                    'text-align:left',
                    'padding:10px 12px',
                    'border-radius:10px',
                    'border:1px solid rgba(0,0,0,0.15)',
                    'background:rgba(255,255,255,0.06)',
                    'color:inherit'
                ].join(';');
                if (opt.value === chatRoomSelect.value) {
                    btn.style.border = '1px solid rgba(var(--accent-color, 255,255,255), 0.6)';
                }
                btn.addEventListener('click', () => {
                    chatRoomSelect.value = opt.value;
                    chatRoomSelect.dispatchEvent(new Event('change', { bubbles: true }));
                    overlay.style.display = 'none';
                });
                list.appendChild(btn);
            });
        };

        const openPicker = e => {
            if (chatRoomSelect.disabled) return;
            if (e) {
                e.preventDefault();
                e.stopPropagation();
            }
            buildOverlay();
            buildList();
            overlay.style.display = 'flex';
        };

        chatRoomSelect.addEventListener('click', openPicker);
        chatRoomSelect.addEventListener('mousedown', openPicker);
        chatRoomSelect.addEventListener('touchstart', openPicker, { passive: false });
    }
} catch (e) {
    console.error(e);
}

// Android chat notifications via bridge (local notification)
try {
    const bridge = (typeof SnapdropAndroid !== 'undefined') ? SnapdropAndroid : (typeof ErikrafTdropAndroid !== 'undefined' ? ErikrafTdropAndroid : null);
    if (bridge && typeof Events !== 'undefined') {
        Events.on('chat-message-received', e => {
            try {
                const message = e?.detail?.message;
                if (!message || !message.senderId) return;
                // Only notify when app isn't visible to avoid duplicate signals
                if (document.visibilityState === 'visible') return;

                const peerDisplayName = message.senderName || message.senderId;
                const title = (typeof Localization !== 'undefined' && Localization.getTranslation)
                    ? Localization.getTranslation("notifications.message-received", null, { name: peerDisplayName })
                    : `Message from ${peerDisplayName}`;

                let body = message.text || '';
                if (!body && message.attachment) {
                    if (typeof Localization !== 'undefined' && Localization.getTranslation) {
                        body = message.attachment.name
                            || (message.attachment.kind === 'video'
                                ? Localization.getTranslation("dialogs.title-file")
                                : Localization.getTranslation("dialogs.title-image"));
                    } else {
                        body = message.attachment.name || 'Attachment';
                    }
                }

                if (typeof bridge.showChatNotification === 'function') {
                    bridge.showChatNotification(title, body);
                }
            } catch (err) {
                console.error(err);
            }
        });
    }
} catch (e) {
    console.error(e);
}

package com.erikraft.drop;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.google.android.material.appbar.MaterialToolbar;

import java.nio.charset.StandardCharsets;

/**
 * Optional Android Beam compatibility and NFC text-transfer tools.
 * NFC tag reading/writing is intentionally not part of this file-transfer-focused app.
 */
public final class LegacyTransferActivity extends DropPipActivity {
    private NfcAdapter nfcAdapter;
    private EditText textInput;
    private TextView status;
    private Button beamButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SnapdropApplication.setAppTheme(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.legacy_transfer_screen_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationContentDescription(R.string.legacy_transfer_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));
        status = new TextView(this);
        status.setText(R.string.legacy_transfer_ready);
        root.addView(status);
        textInput = new EditText(this);
        textInput.setHint(R.string.legacy_transfer_text_hint);
        textInput.setMinLines(2);
        textInput.setGravity(android.view.Gravity.TOP);
        root.addView(textInput);
        beamButton = new Button(this);
        beamButton.setText(R.string.legacy_beam_send_text);
        root.addView(beamButton);
        setContentView(root);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        final boolean beamAvailable = hasOfficialBeamApi(nfcAdapter);
        beamButton.setEnabled(beamAvailable);
        beamButton.setAlpha(beamAvailable ? 1f : 0.55f);
        if (!beamAvailable) {
            beamButton.setText(nfcAdapter == null
                    ? R.string.legacy_nfc_no_hardware
                    : R.string.transfer_feature_unavailable);
            status.setText(nfcAdapter == null
                    ? R.string.legacy_nfc_no_hardware
                    : R.string.transfer_feature_unavailable);
        }
        beamButton.setOnClickListener(v -> shareWithBeam());
        handleNfcIntent(getIntent());
    }

    private void shareWithBeam() {
        if (nfcAdapter == null) {
            status.setText(R.string.legacy_nfc_no_hardware);
            return;
        }
        if (!hasOfficialBeamApi(nfcAdapter)) {
            status.setText(R.string.transfer_feature_unavailable);
            beamButton.setEnabled(false);
            beamButton.setAlpha(0.55f);
            return;
        }
        final String text = textInput.getText().toString();
        if (text.trim().isEmpty()) {
            textInput.setError(getString(R.string.legacy_transfer_text_required));
            textInput.requestFocus();
            return;
        }
        try {
            final NdefMessage message = new NdefMessage(
                    new NdefRecord[]{createTextRecord(text)});
            NfcAdapter.class.getMethod("setNdefPushMessage",
                    NdefMessage.class, Activity.class, Activity[].class)
                    .invoke(nfcAdapter, message, this, new Activity[0]);
            // invokeBeam launches the platform's official Beam handoff UI on Android versions
            // that still expose it. Do not replace it with the Android Sharesheet on newer OSes.
            NfcAdapter.class.getMethod("invokeBeam", Activity.class).invoke(nfcAdapter, this);
            status.setText(R.string.legacy_beam_ready_to_tap);
        } catch (ReflectiveOperationException | SecurityException exception) {
            status.setText(R.string.legacy_beam_api_unavailable);
            beamButton.setEnabled(false);
            beamButton.setAlpha(0.55f);
        }
    }

    private boolean hasOfficialBeamApi(NfcAdapter adapter) {
        if (adapter == null) return false;
        final PackageManager packageManager = getPackageManager();
        try {
            NfcAdapter.class.getMethod("setNdefPushMessage",
                    NdefMessage.class, Activity.class, Activity[].class);
            NfcAdapter.class.getMethod("invokeBeam", Activity.class);
        } catch (NoSuchMethodException | SecurityException exception) {
            return false;
        }

        try {
            // This legacy method is not present in current public SDK stubs.
            final java.lang.reflect.Method beamEnabled =
                    NfcAdapter.class.getMethod("isNdefPushEnabled");
            return Boolean.TRUE.equals(beamEnabled.invoke(adapter));
        } catch (NoSuchMethodException exception) {
            // Fall back to Android's explicit Beam feature declaration, never NFC alone.
            return packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_BEAM);
        } catch (ReflectiveOperationException | SecurityException | UnsupportedOperationException exception) {
            return packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_BEAM);
        }
    }

    private NdefRecord createTextRecord(String text) {
        byte[] language = "en".getBytes(StandardCharsets.US_ASCII);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + language.length + textBytes.length];
        payload[0] = (byte) language.length;
        System.arraycopy(language, 0, payload, 1, language.length);
        System.arraycopy(textBytes, 0, payload, 1 + language.length, textBytes.length);
        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);
    }

    private String readTextRecord(NdefMessage message) {
        if (message == null || message.getRecords().length == 0) return null;
        NdefRecord record = message.getRecords()[0];
        byte[] payload = record.getPayload();
        if (payload == null || payload.length == 0) return null;
        int languageLength = payload[0] & 0x3f;
        if (payload.length <= languageLength + 1) return "";
        return new String(payload, languageLength + 1, payload.length - languageLength - 1, StandardCharsets.UTF_8);
    }

    private void handleNfcIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) return;
        android.os.Parcelable[] incoming = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (incoming != null && incoming.length > 0 && incoming[0] instanceof NdefMessage) {
            String received = readTextRecord((NdefMessage) incoming[0]);
            if (received != null) {
                textInput.setText(received);
                status.setText(R.string.legacy_beam_text_received);
            }
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNfcIntent(intent);
    }


}

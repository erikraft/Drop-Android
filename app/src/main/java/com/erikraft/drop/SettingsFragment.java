package com.erikraft.drop;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.core.os.LocaleListCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.anggrayudi.storage.SimpleStorageHelper;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.erikraft.drop.utils.Link;
import com.erikraft.drop.utils.ShareUtils;
import com.erikraft.drop.utils.ViewUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final List<String> APP_LOCALE_TAGS = Arrays.asList(
            "af-ZA", "ar-SA", "bg-BG", "ca-ES", "cs-CZ", "da-DK", "de-DE",
            "el-GR", "en-US", "es-ES", "fi-FI", "fr-FR", "hi-IN", "hr-HR",
            "hu-HU", "id-ID", "it-IT", "he-IL", "ja-JP", "ko-KR", "mk-MK",
            "ne-NP", "nl-NL", "no-NO", "pl-PL", "pt-BR", "pt-PT", "ro-RO",
            "ru-RU", "sk-SK", "sl-SI", "sr", "sv-SE", "tr-TR", "uk-UA",
            "vi-VN", "zh-CN", "zh-TW");
    private static final String BITCOIN_PRIMARY = "bc1qn8pvw3fvl5dt0eq9fe4js6l3k3j2kekqwxdah2";
    private static final String BITCOIN_EMAIL = "bc1q0mtp0lcyfr7c29xa6ngf8nyv4j0dts4hwynq6d";

    private final SimpleStorageHelper storageHelper = new SimpleStorageHelper(this);
    private SharedPreferences prefs;

    private final ActivityResultLauncher<String> storagePpermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
        final SwitchPreferenceCompat retainLocationMetadataPref = findPreference(getString(R.string.pref_retain_location_metadata));
        retainLocationMetadataPref.setChecked(result);
        if (result) {
            retainLocationMetadataPref.setEnabled(false);
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            Snackbar.make(requireView(), R.string.permission_not_granted, Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(requireView(), R.string.permission_not_granted_fallback, Snackbar.LENGTH_LONG)
                    .setAction(R.string.open_settings, v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", requireContext().getPackageName(), null))))
                    .show();
        }
    });

    private final ActivityResultLauncher<String> notificationsPpermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
        final SwitchPreferenceCompat notificationsPref = findPreference(getString(R.string.pref_notifications));
        if (result) {
            notificationsPref.setChecked(true);
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.POST_NOTIFICATIONS)) {
            Snackbar.make(requireView(), R.string.permission_not_granted, Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(requireView(), R.string.permission_not_granted_fallback, Snackbar.LENGTH_LONG)
                    .setAction(R.string.open_settings, v -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())))
                    .show();
        }
    });

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        updateLegacyTransferCompatibility();
        final Preference legacyNfcPreference = findPreference("legacy_nfc_beam");
        if (legacyNfcPreference != null) {
            legacyNfcPreference.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), LegacyTransferActivity.class));
                return true;
            });
        }
        final Preference legacyWifiPreference = findPreference("legacy_wifi_direct");
        if (legacyWifiPreference != null) {
            legacyWifiPreference.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), LegacyTransferActivity.class));
                return true;
            });
        }

        final Preference diagnosticsEnabled = findPreference(getString(R.string.pref_diagnostics_enabled));
        final Preference diagnosticsOpen = findPreference(getString(R.string.pref_diagnostics_open));
        if (diagnosticsEnabled != null && diagnosticsOpen != null) {
            diagnosticsOpen.setEnabled(prefs.getBoolean(diagnosticsEnabled.getKey(), false));
            diagnosticsEnabled.setOnPreferenceChangeListener((pref, value) -> { diagnosticsOpen.setEnabled((Boolean) value); return true; });
            diagnosticsOpen.setOnPreferenceClickListener(pref -> {
                if (!prefs.getBoolean(diagnosticsEnabled.getKey(), false)) { Snackbar.make(requireView(), R.string.diagnostics_disabled, Snackbar.LENGTH_LONG).show(); return true; }
                startActivity(new Intent(requireContext(), DiagnosticsActivity.class)); return true;
            });
        }

        if (savedInstanceState != null) storageHelper.onRestoreInstanceState(savedInstanceState);

        initUrlPreference(R.string.pref_support, "https://biodrop.erikraft.com/donation.html");

        final Preference bitcoinPreference = findPreference(getString(R.string.pref_bitcoin_donation));
        if (bitcoinPreference != null) {
            bitcoinPreference.setOnPreferenceClickListener(pref -> {
                showBitcoinDonationDialog();
                return true;
            });
        }

        final Preference onionPreference = findPreference(getString(R.string.pref_onion_transfer));
        if (onionPreference != null) {
            onionPreference.setOnPreferenceClickListener(pref -> {
                startActivity(new Intent(requireContext(), OnionTransferActivity.class));
                return true;
            });
        }

        final Preference ftpPreference = findPreference("ftp_settings");
        if (ftpPreference != null) {
            ftpPreference.setOnPreferenceClickListener(pref -> {
                startActivity(new Intent(requireContext(), FtpSettingsActivity.class));
                return true;
            });
        }
        final Preference sftpPreference = findPreference("sftp_settings");
        if (sftpPreference != null) {
            sftpPreference.setOnPreferenceClickListener(pref -> {
                startActivity(new Intent(requireContext(), SftpSettingsActivity.class));
                return true;
            });
        }

        final Preference openSourceComponents = findPreference(getString(R.string.pref_about));
        if (openSourceComponents != null) {
            openSourceComponents.setOnPreferenceClickListener(pref -> {
                startActivity(new Intent(requireContext(), AboutActivity.class));
                return true;
            });
        }

        final Preference floatingTextSelectionPref = findPreference(getString(R.string.pref_floating_text_selection));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            floatingTextSelectionPref.setVisible(true);
            floatingTextSelectionPref.setOnPreferenceChangeListener((pref, newValue) -> {
                getContext().getPackageManager().setComponentEnabledSetting(
                        new ComponentName(getContext(), FloatingTextActivity.class),
                        (Boolean) newValue ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                return true;
            });
        }

        final Preference notificationsPref = findPreference(getString(R.string.pref_notifications));
        notificationsPref.setOnPreferenceChangeListener((pref, newValue) -> {
            if ((boolean) newValue) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    if (isNotificationsCorrectlyEnabled()) return true;
                    Intent settingsIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName())
                            : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setData(Uri.fromParts("package", getContext().getPackageName(), null));
                    startActivity(settingsIntent);
                    return true;
                }
                notificationsPpermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return false;
            }
            return true;
        });

        final Preference deviceNamePref = findPreference(getString(R.string.pref_device_name));
        deviceNamePref.setOnPreferenceClickListener(pref -> showEditTextPreferenceWithResetPossibility(pref, "Android ", "", null, newValue -> updateDeviceNameSummary(deviceNamePref)));
        updateDeviceNameSummary(deviceNamePref);

        final Preference baseUrlPref = findPreference(getString(R.string.pref_baseurl));
        baseUrlPref.setOnPreferenceClickListener(pref -> {
            startActivity(OnboardingActivity.getServerSelectionIntent(requireActivity()));
            return true;
        });

        final Preference saveLocationPref = findPreference(getString(R.string.pref_save_location));
        saveLocationPref.setOnPreferenceClickListener(preference -> {
            storageHelper.openFolderPicker();
            return true;
        });
        final String downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        saveLocationPref.setSummary(prefs.getString(saveLocationPref.getKey(), downloadsFolder));
        storageHelper.setOnFolderSelected((requestCode, folder) -> {
            final String path = DocumentFileUtils.getAbsolutePath(folder, requireContext());
            setPreferenceValue(saveLocationPref.getKey(), path, null);
            saveLocationPref.setSummary(path);
            return null;
        });

        final Preference themePref = findPreference(getString(R.string.pref_theme_setting));
        themePref.setOnPreferenceChangeListener((preference, newValue) -> {
            final DarkModeSetting darkTheme = DarkModeSetting.valueOf((String) newValue);
            SnapdropApplication.setAppTheme(darkTheme);
            requireActivity().setResult(Activity.RESULT_OK);
            requireActivity().recreate();
            return true;
        });

        final Preference appLanguagePreference = findPreference(getString(R.string.pref_app_language));
        if (appLanguagePreference != null) {
            updateAppLanguageSummary(appLanguagePreference);
            appLanguagePreference.setOnPreferenceClickListener(preference -> {
                showAppLanguageDialog();
                return true;
            });
        }

        final SwitchPreferenceCompat pictureInPicturePref = findPreference(getString(R.string.pref_picture_in_picture));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            pictureInPicturePref.setVisible(false);
        }

        final SwitchPreferenceCompat locationMetadataPref = findPreference(getString(R.string.pref_retain_location_metadata));
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            locationMetadataPref.setVisible(true);
            final boolean granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED;
            locationMetadataPref.setChecked(granted);
            if (!granted) {
                locationMetadataPref.setOnPreferenceChangeListener((pref, newValue) -> {
                    if ((Boolean) newValue) storagePpermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION);
                    return false;
                });
            } else {
                locationMetadataPref.setEnabled(false);
            }
        }
    }

    private void showBitcoinDonationDialog() {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, dp(4), padding, 0);

        TextView primaryLabel = new TextView(requireContext());
        primaryLabel.setText(R.string.bitcoin_primary_account);
        primaryLabel.setTextSize(14);
        root.addView(primaryLabel);
        TextView primaryAddress = addressText(BITCOIN_PRIMARY);
        root.addView(primaryAddress);
        MaterialButton copyPrimary = new MaterialButton(requireContext());
        copyPrimary.setText(R.string.bitcoin_copy_primary);
        root.addView(copyPrimary);

        TextView emailLabel = new TextView(requireContext());
        emailLabel.setText(R.string.bitcoin_email_account);
        emailLabel.setTextSize(14);
        emailLabel.setPadding(0, dp(18), 0, 0);
        root.addView(emailLabel);
        TextView emailAddress = addressText(BITCOIN_EMAIL);
        root.addView(emailAddress);
        MaterialButton copyEmail = new MaterialButton(requireContext());
        copyEmail.setText(R.string.bitcoin_copy_email);
        root.addView(copyEmail);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.bitcoin_donation_dialog_title)
                .setIcon(R.drawable.ic_bitcoin)
                .setView(root)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        copyPrimary.setOnClickListener(v -> copyBitcoinAddress(BITCOIN_PRIMARY));
        copyEmail.setOnClickListener(v -> copyBitcoinAddress(BITCOIN_EMAIL));
        dialog.show();
    }

    private TextView addressText(String address) {
        TextView text = new TextView(requireContext());
        text.setText(address);
        text.setTextIsSelectable(true);
        text.setTextSize(13);
        text.setPadding(0, dp(4), 0, dp(4));
        return text;
    }

    private void copyBitcoinAddress(String address) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin", address));
        Snackbar.make(requireView(), R.string.bitcoin_copied, Snackbar.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LanguageOption {
        final String localeTag;
        final String label;
        final String searchable;

        LanguageOption(final String localeTag, final String label, final String searchTerms) {
            this.localeTag = localeTag;
            this.label = label;
            this.searchable = normalizeLanguageSearch(label + " " + searchTerms);
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Makes language search accent-insensitive and tolerant of one-character typos.
     * Keep this local to language search; it does not change displayed names or locale tags.
     */
    private static String normalizeLanguageSearch(final String value) {
        final String source = value == null ? "" : value;
        final StringBuilder normalizedFlags = new StringBuilder(source.length());
        for (int offset = 0; offset < source.length();) {
            final int codePoint = source.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF && offset < source.length()) {
                final int next = source.codePointAt(offset);
                if (next >= 0x1F1E6 && next <= 0x1F1FF) {
                    normalizedFlags.append((char) ('A' + codePoint - 0x1F1E6));
                    normalizedFlags.append((char) ('A' + next - 0x1F1E6));
                    offset += Character.charCount(next);
                    continue;
                }
            }
            normalizedFlags.appendCodePoint(codePoint);
        }
        return Normalizer.normalize(normalizedFlags.toString(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    private static boolean languageSearchMatches(final String searchable, final String query) {
        final String normalizedQuery = normalizeLanguageSearch(query);
        if (normalizedQuery.isEmpty() || searchable.contains(normalizedQuery)) return true;

        final String[] queryTokens = normalizedQuery.split("\\s+");
        final String[] candidateTokens = searchable.split("\\s+");
        for (String queryToken : queryTokens) {
            boolean tokenMatched = false;
            for (String candidateToken : candidateTokens) {
                if (candidateToken.startsWith(queryToken)
                        || (queryToken.length() >= 4 && isWithinOneEdit(queryToken, candidateToken))) {
                    tokenMatched = true;
                    break;
                }
            }
            if (!tokenMatched) return false;
        }
        return true;
    }

    private static boolean isWithinOneEdit(final String first, final String second) {
        if (Math.abs(first.length() - second.length()) > 1) return false;
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < first.length() && j < second.length()) {
            if (first.charAt(i) == second.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) return false;
            if (first.length() > second.length()) i++;
            else if (first.length() < second.length()) j++;
            else {
                i++;
                j++;
            }
        }
        if (i < first.length() || j < second.length()) edits++;
        return edits <= 1;
    }

    /**
     * Returns a regional-indicator flag as an invisible search alias (it is never rendered).
     * Locale tags without a region, such as Serbian "sr", intentionally have no flag alias.
     */
    private static String getFlagSearchAlias(final String localeTag) {
        final String region = Locale.forLanguageTag(localeTag).getCountry().toUpperCase(Locale.ROOT);
        if (region.length() != 2 || !region.matches("[A-Z]{2}")) return "";
        final int first = 0x1F1E6 + region.charAt(0) - 'A';
        final int second = 0x1F1E6 + region.charAt(1) - 'A';
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    private static String getLanguageSearchAliases(final String localeTag) {
        final String language = Locale.forLanguageTag(localeTag).getLanguage();
        switch (language) {
            case "af": return "afrikaans africano";
            case "ar": return "arabic arabe árabe";
            case "bg": return "bulgarian bulgaro búlgaro";
            case "ca": return "catalan catalão catala catalan";
            case "cs": return "czech checo tcheco";
            case "da": return "danish dinamarques dinamarquês";
            case "de": return "german alemao alemão alema alemã";
            case "el": return "greek grego grega";
            case "en": return "english ingles inglês inges ingless inglish";
            case "es": return "spanish espanhol español espanhol";
            case "fi": return "finnish finlandes finlandês";
            case "fr": return "french frances francês fraces";
            case "hi": return "hindi indiano";
            case "hr": return "croatian croata";
            case "hu": return "hungarian hungaro húngaro";
            case "id": return "indonesian indonesio indonésio";
            case "it": return "italian italiano";
            case "he": return "hebrew hebraico";
            case "ja": return "japanese japan japao japão 日本 日本語 にほん にっぽん japonês japones japonese";
            case "ko": return "korean coreano";
            case "mk": return "macedonian macedonio macedônio";
            case "ne": return "nepali nepalês nepales";
            case "nl": return "dutch holandes holandês neerlandes neerlandês";
            case "no": return "norwegian noruegues norueguês";
            case "pl": return "polish polones polonês";
            case "pt": return "portuguese portugues português portugeus";
            case "ro": return "romanian romeno romena";
            case "ru": return "russian russo russa";
            case "sk": return "slovak eslovaco";
            case "sl": return "slovenian esloveno";
            case "sr": return "serbian servio sérvio";
            case "sv": return "swedish sueco";
            case "tr": return "turkish turco";
            case "uk": return "ukrainian ucraniano";
            case "vi": return "vietnamese vietnamita";
            case "zh": return "chinese china 中国 中文 汉语 漢語 chines chinês mandarim";
            default: return "";
        }
    }

    private void showAppLanguageDialog() {
        final List<String> locales = new ArrayList<>(APP_LOCALE_TAGS);
        locales.sort((first, second) ->
                getLanguageDisplayName(first).compareToIgnoreCase(getLanguageDisplayName(second)));

        final List<LanguageOption> allOptions = new ArrayList<>();
        final String systemDefaultLabel = getString(R.string.app_language_system_default);
        allOptions.add(new LanguageOption(null, systemDefaultLabel, "device default system padrão padrao"));
        for (String localeTag : locales) {
            final Locale locale = Locale.forLanguageTag(localeTag);
            final String displayName = getLanguageDisplayName(localeTag);
            final String englishName = locale.getDisplayName(Locale.ENGLISH);
            final String localizedName = locale.getDisplayName(Locale.forLanguageTag("pt-BR"));
            final String countryEnglish = locale.getDisplayCountry(Locale.ENGLISH);
            final String countryPortuguese = locale.getDisplayCountry(Locale.forLanguageTag("pt-BR"));
            final String countryNative = locale.getDisplayCountry(locale);
            allOptions.add(new LanguageOption(localeTag, displayName,
                    localeTag + " " + locale.getLanguage() + " " + englishName + " "
                            + localizedName + " " + locale.getDisplayName(Locale.getDefault()) + " "
                            + countryEnglish + " " + countryPortuguese + " " + countryNative + " "
                            + getFlagSearchAlias(localeTag) + " " + getLanguageSearchAliases(localeTag)));
        }

        final String selectedLocaleTags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        final String selectedLocale = selectedLocaleTags.isEmpty() ? null : selectedLocaleTags.split(",")[0];

        final LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), 0);

        final EditText searchInput = new EditText(requireContext());
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchInput.setHint(R.string.app_language_search);
        searchInput.setContentDescription(getString(R.string.app_language_search));
        content.addView(searchInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final ListView languageList = new ListView(requireContext());
        languageList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        final ArrayAdapter<LanguageOption> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_single_choice, new ArrayList<>(allOptions));
        languageList.setAdapter(adapter);
        final int maxListHeight = Math.min(dp(360),
                Math.max(dp(180), getResources().getDisplayMetrics().heightPixels / 2));
        final FrameLayout listContainer = new FrameLayout(requireContext());
        final TextView emptyView = new TextView(requireContext());
        emptyView.setText(R.string.app_language_search_no_results);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(16), dp(12), dp(16), dp(12));
        listContainer.addView(languageList, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        listContainer.addView(emptyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        languageList.setEmptyView(emptyView);
        content.addView(listContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxListHeight));

        final TextView contributionMessage = new TextView(requireContext());
        contributionMessage.setText(R.string.app_language_contribute);
        contributionMessage.setPadding(0, dp(16), 0, dp(8));
        content.addView(contributionMessage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final MaterialButton webTranslationsButton = new MaterialButton(requireContext());
        webTranslationsButton.setText(R.string.app_language_contribute_web);
        content.addView(webTranslationsButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        webTranslationsButton.setOnClickListener(v ->
                ShareUtils.openUrl(this, "https://crowdin.com/project/erikraft-drop"));

        final MaterialButton androidTranslationsButton = new MaterialButton(requireContext());
        androidTranslationsButton.setText(R.string.app_language_contribute_android);
        content.addView(androidTranslationsButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        androidTranslationsButton.setOnClickListener(v ->
                ShareUtils.openUrl(this, "https://crowdin.com/project/erikraft-drop-android"));

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_language_dialog_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        final Runnable markSelectedOption = () -> {
            languageList.clearChoices();
            for (int i = 0; i < adapter.getCount(); i++) {
                final LanguageOption option = adapter.getItem(i);
                if (option == null) continue;
                final boolean selected = selectedLocale == null
                        ? option.localeTag == null
                        : option.localeTag != null && localeTagsMatch(option.localeTag, selectedLocale);
                if (selected) {
                    languageList.setItemChecked(i, true);
                    break;
                }
            }
        };

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence text, final int start, final int count, final int after) { }

            @Override
            public void onTextChanged(final CharSequence text, final int start, final int before, final int count) { }

            @Override
            public void afterTextChanged(final Editable editable) {
                final String query = editable == null ? "" : editable.toString();
                final List<LanguageOption> matches = new ArrayList<>();
                for (LanguageOption option : allOptions) {
                    if (query.trim().isEmpty() || languageSearchMatches(option.searchable, query)) {
                        matches.add(option);
                    }
                }
                adapter.clear();
                adapter.addAll(matches);
                markSelectedOption.run();
            }
        });

        languageList.setOnItemClickListener((parent, view, position, id) -> {
            final LanguageOption option = adapter.getItem(position);
            if (option == null) return;
            final LocaleListCompat applicationLocales = option.localeTag == null
                    ? LocaleListCompat.getEmptyLocaleList()
                    : LocaleListCompat.forLanguageTags(option.localeTag);
            dialog.dismiss();
            AppCompatDelegate.setApplicationLocales(applicationLocales);
        });

        dialog.show();
        markSelectedOption.run();
    }


    /**
     * Detects whether the platform can expose the original Android Beam API.
     * NFC hardware alone does not prove that Android Beam is implemented.
     * Newer Android versions are marked incompatible without dimming the icon.
     */
    private void updateLegacyTransferCompatibility() {
        final CompatibilityPreference nfcPreference = findPreference("legacy_nfc_beam");
        final CompatibilityPreference wifiPreference = findPreference("legacy_wifi_direct");
        if (nfcPreference != null) {
            final NfcAdapter adapter = NfcAdapter.getDefaultAdapter(requireContext());
            final boolean hasNfc = adapter != null;
            final boolean officialBeamApi = hasOfficialBeamApi(adapter);
            nfcPreference.setCompatible(officialBeamApi);
            nfcPreference.setSummary(!hasNfc
                    ? R.string.legacy_nfc_no_hardware
                    : officialBeamApi
                        ? R.string.legacy_beam_legacy_compatible
                        : R.string.transfer_feature_unavailable);
        }
        if (wifiPreference != null) {
            final boolean hasWifiDirect = requireContext().getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
            wifiPreference.setCompatible(hasWifiDirect);
            wifiPreference.setSummary(hasWifiDirect
                    ? R.string.legacy_transfer_tap_to_open
                    : R.string.legacy_wifi_no_hardware);
        }

        final CompatibilityPreference ftpPreference = findPreference("ftp_settings");
        if (ftpPreference != null) {
            final boolean supported = isClassAvailable("org.apache.ftpserver.FtpServerFactory");
            ftpPreference.setCompatible(supported);
            ftpPreference.setSummary(supported
                    ? R.string.ftp_settings_summary
                    : R.string.transfer_feature_unavailable);
        }
        final CompatibilityPreference sftpPreference = findPreference("sftp_settings");
        if (sftpPreference != null) {
            final boolean supported = isClassAvailable("org.apache.sshd.server.SshServer")
                    && isClassAvailable("org.apache.sshd.sftp.server.SftpSubsystemFactory");
            sftpPreference.setCompatible(supported);
            sftpPreference.setSummary(supported
                    ? R.string.sftp_settings_summary
                    : R.string.transfer_feature_unavailable);
        }
        final CompatibilityPreference onionPreference =
                findPreference(getString(R.string.pref_onion_transfer));
        if (onionPreference != null) {
            final boolean supported = TorController.isRuntimeSupported(requireContext());
            onionPreference.setCompatible(supported);
            onionPreference.setSummary(supported
                    ? R.string.onion_transfer_settings_summary
                    : R.string.transfer_feature_unavailable);
        }
    }

    private boolean isClassAvailable(@NonNull final String className) {
        try {
            Class.forName(className, false, requireContext().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    /**
     * Requires the original Beam methods and platform-declared Beam support.
     * isNdefPushEnabled() is hidden in current SDK stubs, so query it reflectively
     * when an older compatible Android release exposes it.
     */
    private boolean hasOfficialBeamApi(final NfcAdapter adapter) {
        if (adapter == null) return false;
        final PackageManager packageManager = requireContext().getPackageManager();
        try {
            NfcAdapter.class.getMethod("setNdefPushMessage",
                    android.nfc.NdefMessage.class, Activity.class, Activity[].class);
            NfcAdapter.class.getMethod("invokeBeam", Activity.class);
        } catch (NoSuchMethodException | SecurityException exception) {
            return false;
        }

        try {
            final java.lang.reflect.Method beamEnabled =
                    NfcAdapter.class.getMethod("isNdefPushEnabled");
            return Boolean.TRUE.equals(beamEnabled.invoke(adapter));
        } catch (NoSuchMethodException exception) {
            // This method is hidden or absent in newer SDK/runtime combinations.
            // The platform feature is stronger evidence than NFC hardware alone.
            return packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_BEAM);
        } catch (ReflectiveOperationException | SecurityException | UnsupportedOperationException exception) {
            return packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_BEAM);
        }
    }

    private void updateAppLanguageSummary(final Preference preference) {
        final String selectedLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        final String displayName = selectedLocale.isEmpty()
                ? getString(R.string.app_language_system_default)
                : getLanguageDisplayName(selectedLocale.split(",")[0]);
        preference.setSummary(getString(R.string.app_language_summary, displayName));
    }

    private String getLanguageDisplayName(final String languageTag) {
        final Locale locale = Locale.forLanguageTag(languageTag);
        return locale.getDisplayName(locale);
    }

    private boolean localeTagsMatch(final String first, final String second) {
        return Locale.forLanguageTag(first).toLanguageTag()
                .equalsIgnoreCase(Locale.forLanguageTag(second.split(",")[0]).toLanguageTag());
    }

    private boolean isNotificationsCorrectlyEnabled() {
        final NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || notificationManager.areNotificationsEnabled()) &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || notificationManager.getNotificationChannel("MYCHANNEL") == null || notificationManager.getNotificationChannel("MYCHANNEL").getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    private void setPreferenceValue(final String preferenceKey, final String value, final Consumer<String> onPreferenceChangeCallback) {
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putString(preferenceKey, value).apply();
        if (onPreferenceChangeCallback != null) onPreferenceChangeCallback.accept(value);
    }

    private void updateDeviceNameSummary(final Preference pref) {
        if (prefs.contains(getString(R.string.pref_device_name))) {
            pref.setSummary("Android " + prefs.getString(getString(R.string.pref_device_name), getString(R.string.app_name)));
        } else {
            pref.setSummary(R.string.pref_device_name_summary);
        }
    }

    private Preference initUrlPreference(final @StringRes int pref, final String url) {
        final Preference preference = findPreference(getString(pref));
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                ShareUtils.openUrl(this, url);
                return true;
            });
        }
        return preference;
    }

    private boolean showEditTextPreferenceWithResetPossibility(final Preference pref, final String prefix, final @NonNull String defaultValue, final Link link, final Consumer<String> onPreferenceChangeCallback) {
        ViewUtils.showEditTextWithResetPossibility(this, pref.getTitle(), prefix, PreferenceManager.getDefaultSharedPreferences(requireContext()).getString(pref.getKey(), defaultValue), link, newValue -> setPreferenceValue(pref.getKey(), newValue, onPreferenceChangeCallback));
        return true;
    }

    @Override
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        storageHelper.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        final boolean enabled = isNotificationsCorrectlyEnabled();
        final SwitchPreferenceCompat notificationsPref = findPreference(getString(R.string.pref_notifications));
        if (!enabled && notificationsPref.isChecked()) notificationsPref.setChecked(false);

        final Preference baseUrlPref = findPreference(getString(R.string.pref_baseurl));
        baseUrlPref.setSummary(prefs.getString(baseUrlPref.getKey(), getString(R.string.baseurl_not_set)));
    }
}

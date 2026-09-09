package com.erikraft.drop;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.anggrayudi.storage.SimpleStorageHelper;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.erikraft.drop.utils.ClipboardUtils;
import com.erikraft.drop.utils.Link;
import com.erikraft.drop.utils.LogUtils;
import com.erikraft.drop.utils.ShareUtils;
import com.erikraft.drop.utils.ViewUtils;
import com.google.android.material.snackbar.Snackbar;

public class SettingsFragment extends PreferenceFragmentCompat {
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

        if (savedInstanceState != null) storageHelper.onRestoreInstanceState(savedInstanceState);

        initUrlPreference(R.string.pref_support, "https://biodrop.erikraft.com/donation.html");

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

package com.erikraft.drop;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.preference.PreferenceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PreferenceLifecycleContractTest {
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = PreferenceManager.getDefaultSharedPreferences(
                ApplicationProvider.getApplicationContext());
        prefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        prefs.edit().clear().commit();
    }

    @Test
    public void pipDefaultsToEnabledAndPersistsUserChoice() {
        assertTrue(DropPipActivity.isPictureInPictureEnabled(prefs, "picture_in_picture"));

        prefs.edit().putBoolean("picture_in_picture", false).commit();
        assertFalse(DropPipActivity.isPictureInPictureEnabled(prefs, "picture_in_picture"));

        prefs.edit().putBoolean("picture_in_picture", true).commit();
        assertTrue(DropPipActivity.isPictureInPictureEnabled(prefs, "picture_in_picture"));
    }

    @Test
    public void screenOnPreferenceDefaultsToEnabledAndIsIndependentFromPip() {
        assertTrue(MainActivity.shouldKeepScreenOn(prefs, "keep_screen_on"));

        prefs.edit()
                .putBoolean("keep_screen_on", false)
                .putBoolean("picture_in_picture", true)
                .commit();
        assertFalse(MainActivity.shouldKeepScreenOn(prefs, "keep_screen_on"));
        assertTrue(DropPipActivity.isPictureInPictureEnabled(prefs, "picture_in_picture"));

        prefs.edit()
                .putBoolean("keep_screen_on", true)
                .putBoolean("picture_in_picture", false)
                .commit();
        assertTrue(MainActivity.shouldKeepScreenOn(prefs, "keep_screen_on"));
        assertFalse(DropPipActivity.isPictureInPictureEnabled(prefs, "picture_in_picture"));
    }

    @Test
    public void persistedPreferencesSurviveNewSharedPreferencesInstance() {
        prefs.edit()
                .putBoolean("keep_screen_on", false)
                .putBoolean("picture_in_picture", false)
                .commit();

        final SharedPreferences reopened = PreferenceManager.getDefaultSharedPreferences(
                ApplicationProvider.getApplicationContext());

        assertFalse(MainActivity.shouldKeepScreenOn(reopened, "keep_screen_on"));
        assertFalse(DropPipActivity.isPictureInPictureEnabled(reopened, "picture_in_picture"));
    }
}

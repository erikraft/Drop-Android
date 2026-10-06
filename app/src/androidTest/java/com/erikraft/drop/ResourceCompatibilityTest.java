package com.erikraft.drop;

import static org.junit.Assert.assertNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ResourceCompatibilityTest {

    @Test
    public void criticalDrawablesInflateOnDevice() {
        final Context context = ApplicationProvider.getApplicationContext();

        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_snapdrop));
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_ftp));
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.pref_savelocation));
    }

    @Test
    public void quickSettingsServiceExposesAnInflatableIcon() throws Exception {
        final Context context = ApplicationProvider.getApplicationContext();
        final ComponentName component = new ComponentName(context, QuickTileService.class);
        final ServiceInfo info = context.getPackageManager().getServiceInfo(component, 0);
        final Drawable icon = info.loadIcon(context.getPackageManager());

        assertNotNull(icon);
    }
    @Test
    public void minasshdNistp384ParametersResolveWithBundledProvider() throws Exception {
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }

        org.apache.sshd.common.cipher.ECCurves.nistp384.getParameters();
    }

}

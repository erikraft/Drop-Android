package com.erikraft.drop;

import static org.junit.Assert.assertNotNull;

import java.security.AlgorithmParameters;
import java.security.Provider;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;

import org.apache.sshd.common.util.OsUtils;
import org.apache.sshd.common.util.io.PathUtils;
import org.apache.sshd.server.SshServer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ApplicationProvider;

import android.content.Context;

import java.security.KeyStore;

@RunWith(AndroidJUnit4.class)
public class CryptoProviderCompatibilityTest {

    @Test
    public void bundledBouncyCastleSupportsFtpsRsaSignature() throws Exception {
        Provider provider = new BouncyCastleProvider();
        assertNotNull(Signature.getInstance("SHA256withRSA", provider));
    }

    @Test
    public void androidPlatformSupportsSshNistP384Parameters() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp384r1"));
        assertNotNull(parameters.getParameterSpec(ECParameterSpec.class));
    }

    @Test
    public void androidProvidesPkcs12Keystore() throws Exception {
        assertNotNull(KeyStore.getInstance("PKCS12"));
    }

    @Test
    public void sshdInitializesWithAndroidHomeAndWithoutItsBcRegistrar() {
        String providerProperty = "org.apache.sshd.security.provider.BC.enabled";
        String previousProviderProperty = System.getProperty(providerProperty);
        Context context = ApplicationProvider.getApplicationContext();
        java.nio.file.Path appFiles = context.getFilesDir().toPath();

        try {
            System.setProperty("user.home", appFiles.toString());
            System.setProperty("user.dir", appFiles.toString());
            PathUtils.setUserHomeFolderResolver(() -> appFiles);
            OsUtils.setCurrentWorkingDirectoryResolver(() -> appFiles);
            System.setProperty(providerProperty, "false");
            SshServer server = SshServer.setUpDefaultServer();
            assertNotNull(server);
            server.close(true);
        } finally {
            if (previousProviderProperty == null) {
                System.clearProperty(providerProperty);
            } else {
                System.setProperty(providerProperty, previousProviderProperty);
            }
        }
    }
}

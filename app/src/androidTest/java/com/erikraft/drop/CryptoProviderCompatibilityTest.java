package com.erikraft.drop;

import static org.junit.Assert.assertNotNull;

import java.security.AlgorithmParameters;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;

import org.apache.sshd.server.SshServer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
    public void sshdInitializesWithoutItsBouncyCastleRegistrar() {
        String property = "org.apache.sshd.security.provider.BC.enabled";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "false");
            SshServer server = SshServer.setUpDefaultServer();
            assertNotNull(server);
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }
}

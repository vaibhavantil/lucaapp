package de.culture4life.luca.crypto;

import android.util.Base64;

import com.nexenio.rxkeystore.util.RxBase64;

import de.culture4life.luca.LucaApplication;

import net.lachlanmckee.timberjunit.TimberTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import io.reactivex.rxjava3.core.Single;

import static de.culture4life.luca.crypto.AsymmetricCipherProviderTest.decodePrivateKey;
import static de.culture4life.luca.crypto.AsymmetricCipherProviderTest.decodePublicKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class CryptoManagerTest {

    private static final UUID USER_ID = UUID.fromString("02fb635c-f6a5-48eb-8379-a83d611618f2");
    private static final long ROUNDED_UNIX_TIMESTAMP = 1601481600;
    private static final String ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY = "BAIDQ7/zTOcV+XXX5io9XZn1t4MUOAswVfZKd6Fpup/MwlNssx4mCEPcO34AIiV0TbL2ywOP3QoHs41cfvv7uTo=";
    private static final String ENCODED_TRACE_SECRET = "dZrDSp83PCcVL5ZvsJypwA==";
    private static final String ENCODED_DATA_SECRET = "OUR2Tnpohdf6ZQukgqPZ";
    private static final String ENCODED_USER_VERIFICATION_SECRET = "mLV+R8H+YXTTQYonfu6dHw==";
    private static final String ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY = "JwlHQ8w3GjM6T94PwgltA7PNvCk1xokk8HcqXG0CXBI=";
    private static final String ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY = "BIMFVAOglk1B4PIlpaVspeWeFwO5eUusqxFAUUDFNJYGpbp9iu0jRHQAipDTVgFSudcm9tF5kh4+wILrAm3vHWg=";
    private static final String ENCODED_SHARED_DH_SECRET = "cSPbpq56ygtUX0TayiRw0KJpaeoNS/3dcNljtndAXaE=";
    private static final String ENCODED_ENCODING_SECRET = "Nj6/y5L55LYJfobFIsg9+NP1lHrTdaR2hQUUo6nVtC4=";
    private static final String ENCODED_TRACE_ID = "Z0aw+vjwazzQHj21PxmWTQ==";
    private static final String ENCODED_USER_EPHEMERAL_PRIVATE_KEY = "uOjZciQBD49t5ashVVjFunrHlPcIWKth7d6//ygT2yI=";
    private static final String ENCODED_USER_EPHEMERAL_PUBLIC_KEY = "BAr5nL4a/X/XQL/z9TlbxkOhegauxGQcX+szjh5nR8r2Yj0m6qKygRgN4mJqr0lVxNNrDcRw+reHzH/ZD8ezj7U=";

    @Rule
    public TimberTestRule logAllAlwaysRule = TimberTestRule.logAllAlways();

    private CryptoManager cryptoManager;

    @Before
    public void setup() {
        LucaApplication application = (LucaApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();

        cryptoManager = spy(application.getCryptoManager());
        cryptoManager.initialize(application).blockingAwait();

        doReturn(Single.just(new DailyKeyPairPublicKeyWrapper(0, decodePublicKey(ENCODED_DAILY_KEY_PAIR_PUBLIC_KEY))))
                .when(cryptoManager).getDailyKeyPairPublicKeyWrapper();

        doReturn(Single.just(decodeSecret(ENCODED_TRACE_SECRET)))
                .when(cryptoManager).getCurrentTracingSecret();

        doReturn(Single.just(decodeSecret(ENCODED_DATA_SECRET)))
                .when(cryptoManager).getDataSecret();

        KeyPair userMasterKeyPair = new KeyPair(
                decodePublicKey(ENCODED_GUEST_KEY_PAIR_PUBLIC_KEY),
                decodePrivateKey(ENCODED_GUEST_KEY_PAIR_PRIVATE_KEY)
        );
        doReturn(Single.just(userMasterKeyPair))
                .when(cryptoManager).getGuestKeyPair();

        KeyPair userEphemeralKeyPair = new KeyPair(
                decodePublicKey(ENCODED_USER_EPHEMERAL_PUBLIC_KEY),
                decodePrivateKey(ENCODED_USER_EPHEMERAL_PRIVATE_KEY)
        );
        doReturn(Single.just(userEphemeralKeyPair))
                .when(cryptoManager).getUserEphemeralKeyPair(any());

        doReturn(Single.just(decodeSecret(ENCODED_SHARED_DH_SECRET)))
                .when(cryptoManager).getSharedDiffieHellmanSecret();
    }

    @Test
    public void generateTraceId() {
        cryptoManager.generateTraceId(USER_ID, 1601481600L)
                .map(CryptoManagerTest::encodeSecret)
                .test()
                .assertValue(ENCODED_TRACE_ID);
    }

    @Test
    public void generateSharedDiffieHellmanSecret() {
        cryptoManager.generateSharedDiffieHellmanSecret()
                .map(CryptoManagerTest::encodeSecret)
                .test()
                .assertValue(ENCODED_SHARED_DH_SECRET);
    }

    @Test
    public void encodeToString() {
        CryptoManager.encodeToString("AOU\nÄÖÜ".getBytes(StandardCharsets.UTF_8))
                .test()
                .assertResult("QU9VCsOEw5bDnA==");
    }

    @Test
    public void decodeFromString() {
        CryptoManager.decodeFromString("QU9VCsOEw5bDnA==")
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .test()
                .assertResult("AOU\nÄÖÜ");
    }

    public static String encodeSecret(@NonNull byte[] secret) {
        return RxBase64.encode(secret, Base64.NO_WRAP)
                .blockingGet();
    }

    public static byte[] decodeSecret(@NonNull String encodedSecret) {
        return RxBase64.decode(encodedSecret)
                .blockingGet();
    }

}
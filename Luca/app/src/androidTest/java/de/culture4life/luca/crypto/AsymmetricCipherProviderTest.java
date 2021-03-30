package de.culture4life.luca.crypto;

import android.content.Context;
import android.util.Base64;

import com.nexenio.rxkeystore.RxKeyStore;
import com.nexenio.rxkeystore.util.RxBase64;

import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import androidx.test.platform.app.InstrumentationRegistry;

public class AsymmetricCipherProviderTest {

    private static final String ENCODED_UNCOMPRESSED_PUBLIC_KEY = "BAIDQ7/zTOcV+XXX5io9XZn1t4MUOAswVfZKd6Fpup/MwlNssx4mCEPcO34AIiV0TbL2ywOP3QoHs41cfvv7uTo=";
    private static final String ENCODED_COMPRESSED_PUBLIC_KEY = "AgIDQ7/zTOcV+XXX5io9XZn1t4MUOAswVfZKd6Fpup/M";
    private static final String ENCODED_PRIVATE_KEY = "2J9dWi+NKANgXznZVthBygcElRk3XNy7IUPrqwGtEZE=";

    private Context context;
    private AsymmetricCipherProvider asymmetricCipherProvider;

    @Before
    public void setup() {
        CryptoManager.setupSecurityProviders().blockingAwait();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RxKeyStore keyStore = new RxKeyStore(RxKeyStore.TYPE_BKS, RxKeyStore.PROVIDER_BOUNCY_CASTLE);
        asymmetricCipherProvider = new AsymmetricCipherProvider(keyStore);
    }

    @Test
    public void encode_validPublicKeyUncompressed_emitsEncodedUncompressedKey() {
        ECPublicKey publicKey = decodePublicKey(ENCODED_UNCOMPRESSED_PUBLIC_KEY);

        AsymmetricCipherProvider.encode(publicKey, false)
                .flatMap(bytes -> RxBase64.encode(bytes, Base64.NO_WRAP))
                .test()
                .assertValue(ENCODED_UNCOMPRESSED_PUBLIC_KEY);
    }

    @Test
    public void encode_validPublicKeyCompressed_emitsEncodedCompressedKey() {
        ECPublicKey publicKey = decodePublicKey(ENCODED_COMPRESSED_PUBLIC_KEY);

        AsymmetricCipherProvider.encode(publicKey, true)
                .flatMap(bytes -> RxBase64.encode(bytes, Base64.NO_WRAP))
                .test()
                .assertValue(ENCODED_COMPRESSED_PUBLIC_KEY);
    }

    @Test
    public void decodePublicKey_validUncompressedEncodedKey_emitsDecodedKey() {
        ECPublicKey publicKey = decodePublicKey(ENCODED_UNCOMPRESSED_PUBLIC_KEY);
        byte[] encodedPublicKey = AsymmetricCipherProvider.encode(publicKey, false)
                .blockingGet();

        AsymmetricCipherProvider.decodePublicKey(encodedPublicKey)
                .test()
                .assertValue(publicKey);
    }

    @Test
    public void decodePublicKey_validCompressedEncodedKey_emitsDecodedKey() {
        ECPublicKey publicKey = decodePublicKey(ENCODED_UNCOMPRESSED_PUBLIC_KEY);
        byte[] encodedPublicKey = AsymmetricCipherProvider.encode(publicKey, true)
                .blockingGet();

        AsymmetricCipherProvider.decodePublicKey(encodedPublicKey)
                .test()
                .assertValue(publicKey);
    }

    @Test
    public void decodePrivateKey_validEncodedKey_emitsDecodedKey() {
        byte[] encodedPrivateKey = RxBase64.decode(ENCODED_PRIVATE_KEY, Base64.NO_WRAP)
                .blockingGet();

        AsymmetricCipherProvider.decodePrivateKey(encodedPrivateKey)
                .test()
                .assertValue(ecPrivateKey -> ecPrivateKey.getAlgorithm().equals("ECDSA"))
                .assertValue(ecPrivateKey -> ecPrivateKey.getFormat().equals("PKCS#8"))
                .assertValue(ecPrivateKey -> ecPrivateKey.getS().equals(new BigInteger("97981148271098500605815445696501709458330601205038959054259534545461596459409")));
    }

    public static ECPublicKey decodePublicKey(String encodedKey) {
        return RxBase64.decode(encodedKey)
                .flatMap(AsymmetricCipherProvider::decodePublicKey)
                .blockingGet();
    }

    public static ECPrivateKey decodePrivateKey(String encodedKey) {
        return RxBase64.decode(encodedKey)
                .flatMap(AsymmetricCipherProvider::decodePrivateKey)
                .blockingGet();
    }

}
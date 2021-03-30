package de.culture4life.luca.crypto;

import android.content.Context;

import com.nexenio.rxkeystore.RxKeyStore;
import com.nexenio.rxkeystore.provider.cipher.asymmetric.ec.EcCipherProvider;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.ECPointUtil;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;

import androidx.annotation.NonNull;
import io.reactivex.rxjava3.core.Single;

import static com.nexenio.rxkeystore.RxKeyStore.PROVIDER_BOUNCY_CASTLE;

public class AsymmetricCipherProvider extends EcCipherProvider {

    private static final String CURVE_NAME = "secp256r1";
    private static final String KEY_ALGORITHM = "ECDSA";

    public AsymmetricCipherProvider(RxKeyStore rxKeyStore) {
        super(rxKeyStore);
    }

    @Override
    public Single<AlgorithmParameterSpec> getKeyAlgorithmParameterSpec(@NonNull String alias, @NonNull Context context) {
        return Single.fromCallable(() -> new ECGenParameterSpec(CURVE_NAME));
    }

    public static Single<byte[]> encode(@NonNull ECPublicKey publicKey) {
        return encode(publicKey, false);
    }

    public static Single<byte[]> encode(@NonNull ECPublicKey publicKey, boolean compressed) {
        return Single.just(publicKey)
                .cast(BCECPublicKey.class)
                .map(BCECPublicKey::getQ)
                .map(ecPoint -> ecPoint.getEncoded(compressed));
    }

    public static Single<ECPublicKey> decodePublicKey(@NonNull byte[] encodedKey) {
        return Single.fromCallable(() -> {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER_BOUNCY_CASTLE);
            ECNamedCurveParameterSpec bcParameterSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
            ECCurve bcCurve = bcParameterSpec.getCurve();
            EllipticCurve curve = EC5Util.convertCurve(bcCurve, bcParameterSpec.getSeed());
            ECParameterSpec parameterSpec = EC5Util.convertSpec(curve, bcParameterSpec);
            ECPoint point = ECPointUtil.decodePoint(curve, encodedKey);
            ECPublicKeySpec keySpec = new ECPublicKeySpec(point, parameterSpec);
            return (ECPublicKey) keyFactory.generatePublic(keySpec);
        });
    }

    public static Single<ECPrivateKey> decodePrivateKey(@NonNull byte[] encodedKey) {
        return Single.fromCallable(() -> {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM, PROVIDER_BOUNCY_CASTLE);
            ECNamedCurveParameterSpec bcParameterSpec = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
            ECCurve bcCurve = bcParameterSpec.getCurve();
            EllipticCurve curve = EC5Util.convertCurve(bcCurve, bcParameterSpec.getSeed());
            ECParameterSpec parameterSpec = EC5Util.convertSpec(curve, bcParameterSpec);
            BigInteger s = new BigInteger(1, encodedKey);
            java.security.spec.ECPrivateKeySpec privateKeySpec = new java.security.spec.ECPrivateKeySpec(s, parameterSpec);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
            return (ECPrivateKey) privateKey;
        });
    }

    @Override
    protected String[] getBlockModes() {
        return new String[]{"ECB"};
    }

    @Override
    protected String[] getEncryptionPaddings() {
        return new String[]{};
    }

    @Override
    protected String[] getSignaturePaddings() {
        return new String[]{};
    }

    @Override
    protected String[] getDigests() {
        return new String[]{"SHA-256"};
    }

    @Override
    protected String getTransformationAlgorithm() {
        return "ECIES";
    }

    @Override
    protected String getSignatureAlgorithm() {
        return "SHA256withECDSA";
    }

    @Override
    protected String getKeyAgreementAlgorithm() {
        return "ECDH";
    }

}

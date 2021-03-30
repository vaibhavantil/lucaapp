package de.culture4life.luca.crypto;

import android.content.Context;
import android.os.Build;

import com.nexenio.rxkeystore.RxKeyStore;
import com.nexenio.rxkeystore.provider.cipher.symmetric.aes.AesCipherProvider;

import javax.crypto.SecretKey;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.reactivex.rxjava3.core.Single;

@RequiresApi(api = Build.VERSION_CODES.M)
public class SymmetricCipherProvider extends AesCipherProvider {

    public SymmetricCipherProvider(RxKeyStore rxKeyStore) {
        super(rxKeyStore);
    }

    @Override
    public Single<SecretKey> generateKey(@NonNull String alias, @NonNull Context context) {
        return getKeyGeneratorInstance()
                .map(keyGenerator -> {
                    keyGenerator.init(128);
                    return keyGenerator.generateKey();
                });
    }

    @Override
    protected String[] getBlockModes() {
        return new String[]{"CTR"};
    }

    @Override
    protected String[] getEncryptionPaddings() {
        return new String[]{RxKeyStore.ENCRYPTION_PADDING_NONE};
    }

    @Override
    protected String getTransformationAlgorithm() {
        return "AES/CTR/" + RxKeyStore.ENCRYPTION_PADDING_NONE;
    }

}

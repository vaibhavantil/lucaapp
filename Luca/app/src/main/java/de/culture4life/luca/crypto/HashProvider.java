package de.culture4life.luca.crypto;

import com.nexenio.rxkeystore.RxKeyStore;
import com.nexenio.rxkeystore.provider.hash.Sha256HashProvider;

import androidx.annotation.NonNull;

public class HashProvider extends Sha256HashProvider {

    public HashProvider(@NonNull RxKeyStore rxKeyStore) {
        super(rxKeyStore);
    }

}

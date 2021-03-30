package de.culture4life.luca.crypto;

import com.nexenio.rxkeystore.RxKeyStore;
import com.nexenio.rxkeystore.provider.mac.HmacProvider;

import androidx.annotation.NonNull;

public class MacProvider extends HmacProvider {

    public MacProvider(@NonNull RxKeyStore rxKeyStore) {
        super(rxKeyStore, HASH_ALGORITHM_SHA256);
    }

}

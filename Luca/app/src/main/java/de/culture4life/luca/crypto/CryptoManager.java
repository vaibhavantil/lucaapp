package de.culture4life.luca.crypto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Base64;
import android.util.Pair;

import com.nexenio.rxkeystore.RxKeyStore;
import com.nexenio.rxkeystore.provider.hash.RxHashProvider;
import com.nexenio.rxkeystore.util.RxBase64;

import de.culture4life.luca.Manager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.endpoints.LucaEndpointsV3;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class CryptoManager extends Manager {

    public static final String KEYSTORE_FILE_NAME = "keys.ks";
    public static final String DAILY_KEY_PAIR_PUBLIC_KEY_ID_KEY = "daily_key_pair_public_key_id";
    public static final String DAILY_KEY_PAIR_PUBLIC_KEY_POINT_KEY = "daily_key_pair_public_key";
    public static final String DATA_SECRET_KEY = "user_data_secret_2";
    public static final String TRACE_ID_WRAPPERS_KEY = "tracing_id_wrappers";
    public static final String TRACING_SECRET_KEY_PREFIX = "tracing_secret_";
    public static final String ALIAS_GUEST_KEY_PAIR = "user_master_key_pair";
    public static final String ALIAS_USER_EPHEMERAL_KEY_PAIR = "user_ephemeral_key_pair";
    public static final String ALIAS_SCANNER_EPHEMERAL_KEY_PAIR = "scanner_ephemeral_key_pair";
    public static final String ALIAS_MEETING_EPHEMERAL_KEY_PAIR = "meeting_ephemeral_key_pair";
    public static final String ALIAS_KEYSTORE_PASSWORD = "keystore_secret";
    public static final String ALIAS_SECRET_WRAPPING_KEY_PAIR = "secret_wrapping_key_pair";

    @Deprecated
    public static final String OLD_ROTATING_BACKEND_PUBLIC_KEY_ID_KEY = "rotating_backend_public_key_id";
    @Deprecated
    public static final String OLD_ROTATING_BACKEND_PUBLIC_KEY_POINT_KEY = "rotating_backend_public_key";
    @Deprecated
    public static final String OLD_BACKEND_MASTER_PUBLIC_KEY_ID_KEY = "backend_master_public_key_id";
    @Deprecated
    public static final String OLD_BACKEND_MASTER_PUBLIC_KEY_POINT_KEY = "backend_master_public_key";
    @Deprecated
    public static final String USER_DATA_SECRET_KEY_INSECURE = "user_data_secret";
    @Deprecated
    public static final String USER_TRACE_SECRET_KEY_INSECURE = "user_trace_secret";
    @Deprecated
    public static final String USER_TRACE_SECRET_KEY = "user_trace_secret_2";

    private static final byte[] DATA_ENCRYPTION_SECRET_SUFFIX = new byte[]{0x01};
    private static final byte[] DATA_AUTHENTICATION_SECRET_SUFFIX = new byte[]{0x02};

    private final PreferencesManager preferencesManager;
    private final NetworkManager networkManager;

    private final RxKeyStore androidKeyStore;
    private final RxKeyStore bouncyCastleKeyStore;

    private final WrappingCipherProvider wrappingCipherProvider;
    private final SymmetricCipherProvider symmetricCipherProvider;
    private final AsymmetricCipherProvider asymmetricCipherProvider;
    private final SignatureProvider signatureProvider;
    private final MacProvider macProvider;
    private final HashProvider hashProvider;

    private final SecureRandom secureRandom;

    private Context context;

    @Nullable
    private DailyKeyPairPublicKeyWrapper dailyKeyPairPublicKeyWrapper;

    @SuppressLint("NewApi")
    public CryptoManager(@NonNull PreferencesManager preferencesManager, @NonNull NetworkManager networkManager) {
        this.preferencesManager = preferencesManager;
        this.networkManager = networkManager;

        androidKeyStore = new RxKeyStore(RxKeyStore.TYPE_ANDROID, getAndroidKeyStoreProviderName());
        bouncyCastleKeyStore = new RxKeyStore(RxKeyStore.TYPE_BKS, RxKeyStore.PROVIDER_BOUNCY_CASTLE);

        wrappingCipherProvider = new WrappingCipherProvider(androidKeyStore);
        symmetricCipherProvider = new SymmetricCipherProvider(bouncyCastleKeyStore);
        asymmetricCipherProvider = new AsymmetricCipherProvider(bouncyCastleKeyStore);
        signatureProvider = new SignatureProvider(bouncyCastleKeyStore);
        macProvider = new MacProvider(bouncyCastleKeyStore);
        hashProvider = new HashProvider(bouncyCastleKeyStore);
        secureRandom = new SecureRandom();
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.mergeArray(
                preferencesManager.initialize(context),
                networkManager.initialize(context)
        ).andThen(Completable.fromAction(() -> this.context = context))
                .andThen(setupSecurityProviders())
                .andThen(loadKeyStoreFromFile().onErrorComplete())
                .andThen(Completable.mergeArray(
                        migrateUserTracingSecret().onErrorComplete(),
                        migrateDailyKeyPairPublicKey().onErrorComplete()
                ));
    }

    @Nullable
    private String getAndroidKeyStoreProviderName() {
        Set<String> availableProviders = new HashSet<>();
        for (Provider provider : Security.getProviders()) {
            availableProviders.add(provider.getName());
        }
        Timber.i("Available security providers: %s", availableProviders);
        boolean hasWorkaroundProvider = availableProviders.contains("AndroidKeyStoreBCWorkaround");
        if (hasWorkaroundProvider && Build.VERSION.SDK_INT != Build.VERSION_CODES.M) {
            // Not using the default provider (null), will cause a NoSuchAlgorithmException.
            // See https://stackoverflow.com/questions/36111452/androidkeystore-nosuchalgorithm-exception
            Timber.d("BC workaround provider present, using default provider");
            return null;
        } else {
            return RxKeyStore.PROVIDER_ANDROID_KEY_STORE;
        }
    }

    public static Completable setupSecurityProviders() {
        return Completable.fromAction(() -> {
            final Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (!(provider instanceof BouncyCastleProvider)) {
                // Android registers its own BC provider. As it might be outdated and might not include
                // all needed ciphers, we substitute it with a known BC bundled in the app.
                // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
                // of that it's possible to have another BC implementation loaded in VM.
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
                Security.addProvider(new BouncyCastleProvider());
                Timber.i("Inserted bouncy castle provider");
            }
        });
    }

    /**
     * In app versions before 1.4.8, the daily key pair public key was named "backend master key". To
     * avoid the impression that there would be an all mighty "master key", it has been renamed. In
     * app versions before 1.6.1, the daily key pair public key was named "rotating backend public
     * key". To match with the security concept of luca, it has been renamed.
     */
    private Completable migrateDailyKeyPairPublicKey() {
        Maybe<Integer> restoreId = preferencesManager.restoreIfAvailable(OLD_ROTATING_BACKEND_PUBLIC_KEY_ID_KEY, Integer.class);
        Maybe<ECPublicKey> restoreKey = preferencesManager.restoreIfAvailable(OLD_ROTATING_BACKEND_PUBLIC_KEY_POINT_KEY, String.class)
                .flatMapSingle(CryptoManager::decodeFromString)
                .flatMapSingle(AsymmetricCipherProvider::decodePublicKey);

        return Maybe.zip(restoreId, restoreKey, DailyKeyPairPublicKeyWrapper::new)
                .flatMapCompletable(this::persistDailyKeyPairPublicKeyWrapper)
                .andThen(Completable.mergeArray(
                        preferencesManager.delete(OLD_ROTATING_BACKEND_PUBLIC_KEY_ID_KEY),
                        preferencesManager.delete(OLD_ROTATING_BACKEND_PUBLIC_KEY_POINT_KEY),
                        preferencesManager.delete(OLD_BACKEND_MASTER_PUBLIC_KEY_ID_KEY),
                        preferencesManager.delete(OLD_BACKEND_MASTER_PUBLIC_KEY_POINT_KEY)
                ));
    }

    private Completable loadKeyStoreFromFile() {
        return getKeyStorePasswordOrHardcodedValue()
                .flatMapCompletable(password -> {
                    FileInputStream inputStream = context.openFileInput(KEYSTORE_FILE_NAME);
                    return bouncyCastleKeyStore.load(inputStream, password);
                })
                .doOnSubscribe(disposable -> Timber.d("Loading keystore from file"))
                .doOnError(throwable -> Timber.w("Unable to load keystore from file: %s", throwable.toString()));
    }

    private Completable persistKeyStoreToFile() {
        return getKeyStorePassword()
                .flatMapCompletable(password -> {
                    FileOutputStream outputStream = context.openFileOutput(KEYSTORE_FILE_NAME, Context.MODE_PRIVATE);
                    return bouncyCastleKeyStore.save(outputStream, password);
                })
                .doOnSubscribe(disposable -> Timber.d("Persisting keystore to file"))
                .doOnError(throwable -> Timber.e("Unable to persist keystore to file: %s", throwable.toString()));
    }

    /**
     * In app versions before 1.2.4, the {@link #bouncyCastleKeyStore} was protected with a
     * hardcoded password. For migration purposes, this method either emits that hardcoded password
     * or the result of {@link #getKeyStorePassword()}. Should only be used when loading the key
     * store.
     */
    private Single<String> getKeyStorePasswordOrHardcodedValue() {
        return preferencesManager.containsKey(ALIAS_KEYSTORE_PASSWORD)
                .flatMap(hasPassword -> hasPassword ? getKeyStorePassword() : Single.just("luca"));
    }

    private Single<String> getKeyStorePassword() {
        return restoreWrappedSecretIfAvailable(ALIAS_KEYSTORE_PASSWORD)
                .switchIfEmpty(generateSecureRandomData(128)
                        .doOnSuccess(bytes -> Timber.d("Generated new random key store password"))
                        .flatMap(randomData -> persistWrappedSecret(ALIAS_KEYSTORE_PASSWORD, randomData)
                                .andThen(Single.just(randomData))))
                .map(String::new);
    }

    /*
        Secret wrapping
     */

    /**
     * Will get or generate a key pair using the {@link #wrappingCipherProvider} which may be used
     * for restoring or persisting {@link WrappedSecret}s.
     */
    private Single<KeyPair> getSecretWrappingKeyPair() {
        return wrappingCipherProvider.getKeyPairIfAvailable(ALIAS_SECRET_WRAPPING_KEY_PAIR)
                .switchIfEmpty(wrappingCipherProvider.generateKeyPair(ALIAS_SECRET_WRAPPING_KEY_PAIR, context)
                        .doOnSubscribe(disposable -> Timber.d("Generating new secret wrapping key pair"))
                        .doOnError(throwable -> Timber.e("Unable to generate secret wrapping key pair: %s", throwable.toString())));
    }

    /**
     * Will restore the {@link WrappedSecret} using the {@link #preferencesManager} and decrypt it
     * using the {@link #wrappingCipherProvider}.
     */
    private Maybe<byte[]> restoreWrappedSecretIfAvailable(@NonNull String alias) {
        return preferencesManager.restoreIfAvailable(alias, WrappedSecret.class)
                .flatMapSingle(wrappedSecret -> getSecretWrappingKeyPair()
                        .flatMap(keyPair -> wrappingCipherProvider.decrypt(wrappedSecret.getDeserializedEncryptedSecret(), wrappedSecret.getDeserializedIv(), keyPair.getPrivate())))
                .doOnError(throwable -> Timber.e("Unable to restore wrapped secret: %s", throwable.toString()));
    }

    /**
     * Will encrypt the specified secret using the {@link #wrappingCipherProvider} and persist it as
     * a {@link WrappedSecret} using the {@link #preferencesManager}.
     */
    private Completable persistWrappedSecret(@NonNull String alias, @NonNull byte[] secret) {
        return getSecretWrappingKeyPair()
                .flatMap(keyPair -> wrappingCipherProvider.encrypt(secret, keyPair.getPublic()))
                .map(WrappedSecret::new)
                .flatMapCompletable(wrappedSecret -> preferencesManager.persist(alias, wrappedSecret))
                .doOnError(throwable -> Timber.e("Unable to persist wrapped secret: %s", throwable.toString()));
    }

    /*
        Check-in
     */

    public Single<TraceIdWrapper> getTraceIdWrapper(@NonNull UUID userId) {
        return generateTraceIdWrapper(userId)
                .flatMap(traceIdWrapper -> persistTraceIdWrapper(traceIdWrapper)
                        .andThen(Single.just(traceIdWrapper)));
    }

    private Single<TraceIdWrapper> generateTraceIdWrapper(@NonNull UUID userId) {
        return TimeUtil.getCurrentUnixTimestamp()
                .flatMap(TimeUtil::roundUnixTimestampDownToMinute)
                .flatMap(roundedUnixTimestamp -> generateTraceId(userId, roundedUnixTimestamp)
                        .map(traceId -> new TraceIdWrapper(roundedUnixTimestamp, traceId)));
    }

    public Single<byte[]> generateTraceId(@NonNull UUID userId, long roundedUnixTimestamp) {
        return Single.zip(encode(userId), TimeUtil.encodeUnixTimestamp(roundedUnixTimestamp), Pair::new)
                .flatMap(encodedDataPair -> concatenate(encodedDataPair.first, encodedDataPair.second))
                .flatMap(encodedData -> getCurrentTracingSecret()
                        .flatMap(CryptoManager::createKeyFromSecret)
                        .flatMap(traceKey -> macProvider.sign(encodedData, traceKey)))
                .flatMap(traceId -> trim(traceId, 16))
                .doOnSuccess(traceId -> Timber.d("Generated new trace ID: %s", SerializationUtil.serializeToBase64(traceId).blockingGet()));
    }

    public Observable<TraceIdWrapper> getTraceIdWrappers() {
        return restoreTraceIdWrappers();
    }

    private Observable<TraceIdWrapper> restoreTraceIdWrappers() {
        return preferencesManager.restoreIfAvailable(TRACE_ID_WRAPPERS_KEY, TraceIdWrapperList.class)
                .flatMapObservable(Observable::fromIterable)
                .sorted((first, second) -> Long.compare(first.getTimestamp(), second.getTimestamp()));
    }

    private Completable persistTraceIdWrapper(@NonNull TraceIdWrapper traceIdWrapper) {
        return getTraceIdWrappers()
                .mergeWith(Observable.just(traceIdWrapper))
                .toList()
                .map(TraceIdWrapperList::new)
                .flatMapCompletable(traceIdWrappers -> preferencesManager.persist(TRACE_ID_WRAPPERS_KEY, traceIdWrappers));
    }

    public Completable deleteTraceData() {
        return getTraceIdWrappers()
                .map(TraceIdWrapper::getTraceId)
                .flatMapCompletable(this::deleteUserEphemeralKeyPair)
                .andThen(preferencesManager.delete(TRACE_ID_WRAPPERS_KEY));
    }

    public Single<byte[]> generateEphemeralDiffieHellmanSecret(@NonNull PrivateKey ephemeralUserPrivateKey) {
        return generateSharedDiffieHellmanSecret(ephemeralUserPrivateKey);
    }

    /*
        Daily key pair public key
     */

    /**
     * Will fetch the latest daily key pair public key from the API, update {@link
     * #dailyKeyPairPublicKeyWrapper} and persist it in the preferences.
     *
     * This should be done on each app start, but not required for a successful initialization (e.g.
     * because the user may be offline).
     */
    public Completable updateDailyKeyPairPublicKey() {
        return fetchDailyKeyPairPublicKeyWrapperFromBackend()
                .doOnSuccess(wrapper -> this.dailyKeyPairPublicKeyWrapper = wrapper)
                .flatMapCompletable(this::persistDailyKeyPairPublicKeyWrapper)
                .doOnSubscribe(disposable -> Timber.d("Updating daily key pair public key"));
    }

    public Single<DailyKeyPairPublicKeyWrapper> getDailyKeyPairPublicKeyWrapper() {
        return Maybe.fromCallable(() -> dailyKeyPairPublicKeyWrapper)
                .switchIfEmpty(restoreDailyKeyPairPublicKeyWrapper()
                        .doOnSuccess(restoredKey -> dailyKeyPairPublicKeyWrapper = restoredKey)
                        .switchIfEmpty(updateDailyKeyPairPublicKey()
                                .andThen(Single.fromCallable(() -> dailyKeyPairPublicKeyWrapper))));
    }

    private Single<DailyKeyPairPublicKeyWrapper> fetchDailyKeyPairPublicKeyWrapperFromBackend() {
        return networkManager.getLucaEndpointsV3()
                .flatMap(LucaEndpointsV3::getDailyKeyPairPublicKey)
                .flatMap(jsonObject -> Single.fromCallable(() -> {
                    byte[] encodedPublicKey = decodeFromString(jsonObject.get("publicKey").getAsString()).blockingGet();
                    ECPublicKey publicKey = AsymmetricCipherProvider.decodePublicKey(encodedPublicKey).blockingGet();

                    int creationUnixTimestamp = jsonObject.get("createdAt").getAsInt();
                    byte[] encodedCreationTimestamp = TimeUtil.encodeUnixTimestamp(creationUnixTimestamp).blockingGet();

                    int id = jsonObject.get("keyId").getAsInt();
                    byte[] encodedId = ByteBuffer.allocate(4)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(id)
                            .array();

                    PublicKey issuerSigningKey = getKeyIssuerSigningKey(jsonObject.get("issuerId").getAsString()).blockingGet();
                    byte[] signature = decodeFromString(jsonObject.get("signature").getAsString()).blockingGet();
                    byte[] signedData = concatenate(encodedId, encodedCreationTimestamp, encodedPublicKey).blockingGet();

                    signatureProvider.verify(signedData, signature, issuerSigningKey).blockingAwait();

                    long keyAge = TimeUtil.convertFromUnixTimestamp(creationUnixTimestamp)
                            .map(creationTimestamp -> System.currentTimeMillis() - creationTimestamp)
                            .blockingGet();

                    if (keyAge > TimeUnit.DAYS.toMillis(7)) {
                        throw new IllegalStateException("Daily key pair public key is older than 7 days");
                    }

                    DailyKeyPairPublicKeyWrapper wrapper = new DailyKeyPairPublicKeyWrapper();
                    wrapper.setId(id);
                    wrapper.setPublicKey(publicKey);
                    return wrapper;
                })).doOnSuccess(wrapper -> Timber.d("Fetched daily key pair public key from backend: %s", wrapper));
    }

    private Single<PublicKey> getKeyIssuerSigningKey(@NonNull String issuerId) {
        // TODO: 18.03.21 cache previously requested issuers
        return networkManager.getLucaEndpointsV3()
                .flatMap(lucaEndpointsV3 -> lucaEndpointsV3.getKeyIssuer(issuerId))
                .map(jsonObject -> jsonObject.get("publicHDSKP").getAsString())
                .flatMap(CryptoManager::decodeFromString)
                .flatMap(AsymmetricCipherProvider::decodePublicKey);
    }

    private Maybe<DailyKeyPairPublicKeyWrapper> restoreDailyKeyPairPublicKeyWrapper() {
        Maybe<Integer> restoreId = preferencesManager.restoreIfAvailable(DAILY_KEY_PAIR_PUBLIC_KEY_ID_KEY, Integer.class);
        Maybe<ECPublicKey> restoreKey = preferencesManager.restoreIfAvailable(DAILY_KEY_PAIR_PUBLIC_KEY_POINT_KEY, String.class)
                .flatMapSingle(CryptoManager::decodeFromString)
                .flatMapSingle(AsymmetricCipherProvider::decodePublicKey);
        return Maybe.zip(restoreId, restoreKey, DailyKeyPairPublicKeyWrapper::new);
    }

    private Completable persistDailyKeyPairPublicKeyWrapper(@NonNull DailyKeyPairPublicKeyWrapper wrapper) {
        Completable persistId = preferencesManager.persist(DAILY_KEY_PAIR_PUBLIC_KEY_ID_KEY, wrapper.getId());
        Completable persistKey = AsymmetricCipherProvider.encode(wrapper.getPublicKey(), false)
                .flatMap(CryptoManager::encodeToString)
                .flatMapCompletable(encodedPublicKey -> preferencesManager.persist(DAILY_KEY_PAIR_PUBLIC_KEY_POINT_KEY, encodedPublicKey));
        return Completable.mergeArray(persistId, persistKey);
    }

    /*
        Guest key pair
     */

    public Single<KeyPair> getGuestKeyPair() {
        return restoreGuestKeyPair()
                .switchIfEmpty(generateGuestKeyPair()
                        .observeOn(Schedulers.io())
                        .flatMap(guestKeyPair -> persistGuestKeyPair(guestKeyPair)
                                .andThen(Single.just(guestKeyPair))));
    }

    public Single<ECPrivateKey> getGuestKeyPairPrivateKey() {
        return getGuestKeyPair().map(KeyPair::getPrivate)
                .cast(ECPrivateKey.class);
    }

    public Single<ECPublicKey> getGuestKeyPairPublicKey() {
        return getGuestKeyPair().map(KeyPair::getPublic)
                .cast(ECPublicKey.class);
    }

    private Single<KeyPair> generateGuestKeyPair() {
        return asymmetricCipherProvider.generateKeyPair(ALIAS_GUEST_KEY_PAIR, context)
                .doOnSuccess(guestKeyPair -> Timber.d("Generated new guest key pair: %s", guestKeyPair.getPublic()));
    }

    private Maybe<KeyPair> restoreGuestKeyPair() {
        return asymmetricCipherProvider.getKeyPairIfAvailable(ALIAS_GUEST_KEY_PAIR);
    }

    private Completable persistGuestKeyPair(@NonNull KeyPair keyPair) {
        return asymmetricCipherProvider.setKeyPair(ALIAS_GUEST_KEY_PAIR, keyPair)
                .andThen(persistKeyStoreToFile());
    }

    /*
        User ephemeral key pair
     */

    public Single<KeyPair> getUserEphemeralKeyPair(@NonNull byte[] traceId) {
        return restoreUserEphemeralKeyPair(traceId)
                .switchIfEmpty(generateUserEphemeralKeyPair(traceId)
                        .observeOn(Schedulers.io())
                        .flatMap(keyPair -> persistUserEphemeralKeyPair(traceId, keyPair)
                                .andThen(Single.just(keyPair))));
    }

    public Single<PrivateKey> getUserEphemeralPrivateKey(@NonNull byte[] traceId) {
        return getUserEphemeralKeyPair(traceId).map(KeyPair::getPrivate);
    }

    public Single<PublicKey> getUserEphemeralPublicKey(@NonNull byte[] traceId) {
        return getUserEphemeralKeyPair(traceId).map(KeyPair::getPublic);
    }

    private Single<KeyPair> generateUserEphemeralKeyPair(@NonNull byte[] traceId) {
        return getUserEphemeralKeyPairAlias(traceId)
                .flatMap(alias -> asymmetricCipherProvider.generateKeyPair(alias, context))
                .doOnSuccess(keyPair -> Timber.d("Generated new user ephemeral key pair for trace ID %s: %s", SerializationUtil.serializeToBase64(traceId).blockingGet(), keyPair.getPublic()));
    }

    private Maybe<KeyPair> restoreUserEphemeralKeyPair(@NonNull byte[] traceId) {
        return getUserEphemeralKeyPairAlias(traceId)
                .flatMapMaybe(asymmetricCipherProvider::getKeyPairIfAvailable);
    }

    private Completable persistUserEphemeralKeyPair(@NonNull byte[] traceId, @NonNull KeyPair keyPair) {
        return getUserEphemeralKeyPairAlias(traceId)
                .flatMapCompletable(alias -> asymmetricCipherProvider.setKeyPair(alias, keyPair))
                .andThen(persistKeyStoreToFile());
    }

    private Completable deleteUserEphemeralKeyPair(@NonNull byte[] traceId) {
        return getUserEphemeralKeyPairAlias(traceId)
                .flatMapCompletable(bouncyCastleKeyStore::deleteEntry);
    }

    private static Single<String> getUserEphemeralKeyPairAlias(@NonNull byte[] traceId) {
        return SerializationUtil.serializeToBase64(traceId)
                .map(serializedTraceId -> ALIAS_USER_EPHEMERAL_KEY_PAIR + "-" + serializedTraceId);
    }

    /*
        Tracing secrets
     */

    public Single<byte[]> getCurrentTracingSecret() {
        return restoreCurrentTracingSecret()
                .switchIfEmpty(generateTracingSecret()
                        .observeOn(Schedulers.io())
                        .flatMap(secret -> persistCurrentTracingSecret(secret)
                                .andThen(Single.just(secret))));
    }

    private Single<byte[]> generateTracingSecret() {
        return generateSecureRandomData(16)
                .doOnSuccess(bytes -> Timber.d("Generated new tracing secret"));
    }

    private Maybe<byte[]> restoreCurrentTracingSecret() {
        return restoreRecentTracingSecrets(1)
                .map(pair -> pair.second)
                .firstElement();
    }

    private Completable persistCurrentTracingSecret(@NonNull byte[] secret) {
        return TimeUtil.getStartOfDayTimestamp()
                .map(startOfDayTimestamp -> TRACING_SECRET_KEY_PREFIX + startOfDayTimestamp)
                .flatMapCompletable(preferenceKey -> persistWrappedSecret(preferenceKey, secret));
    }

    public Observable<Pair<Long, byte[]>> restoreRecentTracingSecrets(int days) {
        return generateRecentStartOfDayTimestamps(days)
                .flatMapMaybe(startOfDayTimestamp -> restoreWrappedSecretIfAvailable(TRACING_SECRET_KEY_PREFIX + startOfDayTimestamp)
                        .map(secret -> new Pair<>(startOfDayTimestamp, secret)));
    }

    private Observable<Long> generateRecentStartOfDayTimestamps(int days) {
        return TimeUtil.getStartOfDayTimestamp()
                .flatMapObservable(firstStartOfDayTimestamp -> Observable.range(0, days)
                        .map(dayIndex -> firstStartOfDayTimestamp - (dayIndex * TimeUnit.DAYS.toMillis(dayIndex))));
    }

    /**
     * In app versions before 1.4.1, there was only one user tracing secret (no daily rotation).
     * This method will migrate that secret to the new rotatable format, if available.
     */
    private Completable migrateUserTracingSecret() {
        return restoreWrappedSecretIfAvailable(USER_TRACE_SECRET_KEY)
                .flatMapCompletable(this::persistCurrentTracingSecret)
                .andThen(preferencesManager.delete(USER_TRACE_SECRET_KEY))
                .doOnComplete(() -> Timber.i("Migrated user tracing secret for daily rotation"));
    }

    /*
        User data secret
     */

    public Single<byte[]> getDataSecret() {
        return restoreDataSecret()
                .switchIfEmpty(generateDataSecret()
                        .observeOn(Schedulers.io())
                        .flatMap(secret -> persistDataSecret(secret)
                                .andThen(Single.just(secret))));
    }

    public Single<byte[]> generateDataSecret() {
        return generateSecureRandomData(16)
                .doOnSuccess(bytes -> Timber.d("Generated new user data secret"));
    }

    private Maybe<byte[]> restoreDataSecret() {
        return restoreWrappedSecretIfAvailable(DATA_SECRET_KEY);
    }

    private Completable persistDataSecret(@NonNull byte[] secret) {
        return persistWrappedSecret(DATA_SECRET_KEY, secret);
    }

    /*
        Shared Diffie-Hellman secret
     */

    public Single<byte[]> getSharedDiffieHellmanSecret() {
        return generateSharedDiffieHellmanSecret();
    }

    public Single<byte[]> generateSharedDiffieHellmanSecret() {
        return getGuestKeyPairPrivateKey()
                .flatMap(this::generateSharedDiffieHellmanSecret);
    }

    public Single<byte[]> generateSharedDiffieHellmanSecret(@NonNull PrivateKey privateKey) {
        return getDailyKeyPairPublicKeyWrapper()
                .map(DailyKeyPairPublicKeyWrapper::getPublicKey)
                .flatMap(dailyKeyPairPublicKey -> asymmetricCipherProvider.generateSecret(privateKey, dailyKeyPairPublicKey))
                .doOnSuccess(bytes -> Timber.d("Generated new shared Diffie Hellman secret"));
    }

    /*
        Encryption secret
     */

    public Single<byte[]> generateDataEncryptionSecret(@NonNull byte[] baseSecret) {
        return concatenate(baseSecret, DATA_ENCRYPTION_SECRET_SUFFIX)
                .flatMap(hashProvider::hash)
                .flatMap(secret -> trim(secret, 16));
    }

    /*
        Authentication secret
     */

    public Single<byte[]> generateDataAuthenticationSecret(@NonNull byte[] baseSecret) {
        return concatenate(baseSecret, DATA_AUTHENTICATION_SECRET_SUFFIX)
                .flatMap(hashProvider::hash);
    }

    /*
        Scanner
     */

    public Single<KeyPair> getScannerEphemeralKeyPair() {
        return asymmetricCipherProvider.getKeyPair(ALIAS_SCANNER_EPHEMERAL_KEY_PAIR);
    }

    public Single<KeyPair> generateScannerEphemeralKeyPair() {
        return asymmetricCipherProvider.generateKeyPair(ALIAS_SCANNER_EPHEMERAL_KEY_PAIR, context)
                .doOnSuccess(keyPair -> Timber.d("Generated new scanner ephemeral key pair: %s", keyPair.getPublic()));
    }

    public Completable persistScannerEphemeralKeyPair(@NonNull KeyPair keyPair) {
        return asymmetricCipherProvider.setKeyPair(ALIAS_SCANNER_EPHEMERAL_KEY_PAIR, keyPair)
                .andThen(persistKeyStoreToFile());
    }

    /*
        Meeting
     */

    public Single<KeyPair> getMeetingEphemeralKeyPair(@NonNull UUID meetingId) {
        return restoreMeetingEphemeralKeyPair(meetingId)
                .switchIfEmpty(generateMeetingEphemeralKeyPair()
                        .flatMap(keyPair -> persistMeetingEphemeralKeyPair(meetingId, keyPair)
                                .andThen(Single.just(keyPair))));
    }

    public Single<PrivateKey> getMeetingEphemeralPrivateKey(@NonNull UUID meetingId) {
        return getMeetingEphemeralKeyPair(meetingId).map(KeyPair::getPrivate);
    }

    public Single<PublicKey> getMeetingEphemeralPublicKey(@NonNull UUID meetingId) {
        return getMeetingEphemeralKeyPair(meetingId).map(KeyPair::getPublic);
    }

    public Single<KeyPair> generateMeetingEphemeralKeyPair() {
        return asymmetricCipherProvider.generateKeyPair(ALIAS_MEETING_EPHEMERAL_KEY_PAIR, context)
                .doOnSuccess(keyPair -> Timber.d("Generated new meeting ephemeral key pair: %s", keyPair.getPublic()));
    }

    public Maybe<KeyPair> restoreMeetingEphemeralKeyPair(@NonNull UUID meetingId) {
        return getMeetingEphemeralKeyPairAlias(meetingId)
                .flatMapMaybe(asymmetricCipherProvider::getKeyPairIfAvailable);
    }

    public Completable persistMeetingEphemeralKeyPair(@NonNull UUID meetingId, @NonNull KeyPair keyPair) {
        return getMeetingEphemeralKeyPairAlias(meetingId)
                .flatMapCompletable(alias -> asymmetricCipherProvider.setKeyPair(alias, keyPair))
                .andThen(persistKeyStoreToFile());
    }

    public Completable deleteMeetingEphemeralKeyPair(@NonNull UUID meetingId) {
        return getMeetingEphemeralKeyPairAlias(meetingId)
                .flatMapCompletable(bouncyCastleKeyStore::deleteEntry);
    }

    public static Single<String> getMeetingEphemeralKeyPairAlias(@NonNull UUID meetingId) {
        return Single.just(ALIAS_MEETING_EPHEMERAL_KEY_PAIR + "-" + meetingId.toString());
    }

    /*
        Utilities
     */

    public Single<byte[]> generateSecureRandomData(int length) {
        return Single.fromCallable(() -> {
            byte[] randomBytes = new byte[length];
            secureRandom.nextBytes(randomBytes);
            return randomBytes;
        });
    }

    public static Single<SecretKey> createKeyFromSecret(@NonNull byte[] secret) {
        return Single.just(new SecretKeySpec(secret, 0, secret.length, "AES"));
    }

    public static Single<byte[]> encode(@NonNull UUID uuid) {
        return Single.fromCallable(() -> {
            ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
            byteBuffer.putLong(uuid.getMostSignificantBits());
            byteBuffer.putLong(uuid.getLeastSignificantBits());
            return byteBuffer.array();
        });
    }

    public static Single<String> encodeToString(@NonNull byte[] data) {
        return RxBase64.encode(data, Base64.NO_WRAP);
    }

    public static Single<byte[]> decodeFromString(@NonNull String data) {
        return RxBase64.decode(data, Base64.NO_WRAP);
    }

    public static Single<byte[]> concatenate(byte[]... dataArray) {
        return Single.fromCallable(() -> {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (byte[] data : dataArray) {
                outputStream.write(data);
            }
            return outputStream.toByteArray();
        });
    }

    public static Single<byte[]> trim(byte[] data, int length) {
        return Single.fromCallable(() -> {
            byte[] trimmedData = new byte[length];
            System.arraycopy(data, 0, trimmedData, 0, length);
            return trimmedData;
        });
    }

    /*
        Getter & Setter
     */

    public RxKeyStore getBouncyCastleKeyStore() {
        return bouncyCastleKeyStore;
    }

    public SymmetricCipherProvider getSymmetricCipherProvider() {
        return symmetricCipherProvider;
    }

    public AsymmetricCipherProvider getAsymmetricCipherProvider() {
        return asymmetricCipherProvider;
    }

    public SignatureProvider getSignatureProvider() {
        return signatureProvider;
    }

    public MacProvider getMacProvider() {
        return macProvider;
    }

    public RxHashProvider getHashProvider() {
        return hashProvider;
    }

}
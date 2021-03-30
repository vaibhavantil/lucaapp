package de.culture4life.luca.registration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import android.content.Context;
import android.util.Pair;

import de.culture4life.luca.Manager;
import de.culture4life.luca.crypto.AsymmetricCipherProvider;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.crypto.DailyKeyPairPublicKeyWrapper;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.ContactData;
import de.culture4life.luca.network.pojo.DataTransferRequestData;
import de.culture4life.luca.network.pojo.TransferData;
import de.culture4life.luca.network.pojo.UserRegistrationRequestData;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import timber.log.Timber;

import static de.culture4life.luca.util.SerializationUtil.serializeToBase64;

public class RegistrationManager extends Manager {

    public static final String REGISTRATION_COMPLETED_KEY = "registration_completed_2";
    public static final String REGISTRATION_ID_KEY = "registration_id";
    public static final String REGISTRATION_DATA_KEY = "registration_data_2";
    public static final String USER_ID_KEY = "user_id";

    private final PreferencesManager preferencesManager;
    private final NetworkManager networkManager;
    private final CryptoManager cryptoManager;

    public RegistrationManager(@NonNull PreferencesManager preferencesManager, @NonNull NetworkManager networkManager, @NonNull CryptoManager cryptoManager) {
        this.preferencesManager = preferencesManager;
        this.networkManager = networkManager;
        this.cryptoManager = cryptoManager;
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.mergeArray(
                preferencesManager.initialize(context),
                networkManager.initialize(context),
                cryptoManager.initialize(context)
        ).andThen(Completable.fromAction(() -> this.context = context));
    }

    public Completable deleteRegistrationData() {
        return Completable.mergeArray(
                preferencesManager.persist(REGISTRATION_COMPLETED_KEY, false),
                preferencesManager.delete(REGISTRATION_ID_KEY)
        );
    }

    public Single<Boolean> hasCompletedRegistration() {
        return preferencesManager.restoreOrDefault(REGISTRATION_COMPLETED_KEY, false);
    }

    public Single<RegistrationData> getOrCreateRegistrationData() {
        return preferencesManager.restoreIfAvailable(REGISTRATION_DATA_KEY, RegistrationData.class)
                .switchIfEmpty(createRegistrationData());
    }

    public Single<RegistrationData> createRegistrationData() {
        return Single.just(new RegistrationData())
                .flatMap(registrationData -> persistRegistrationData(registrationData)
                        .andThen(Single.just(registrationData)));
    }

    public Completable persistRegistrationData(@NonNull RegistrationData registrationData) {
        return preferencesManager.persist(REGISTRATION_DATA_KEY, registrationData);
    }

    public Maybe<UUID> getUserIdIfAvailable() {
        return preferencesManager.restoreIfAvailable(USER_ID_KEY, UUID.class);
    }

    public Single<Boolean> hasProvidedRequiredContactData() {
        return getOrCreateRegistrationData()
                .map(registrationData -> {
                    Timber.v("Checking if required contact data has been provided: %s", registrationData);
                    if (!isNonEmptyValue(registrationData.getFirstName())) {
                        return false;
                    } else if (!isNonEmptyValue(registrationData.getLastName())) {
                        return false;
                    } else if (!isNonEmptyValue(registrationData.getPhoneNumber())) {
                        return false;
                    } else if (!isNonEmptyValue(registrationData.getStreet())) {
                        return false;
                    } else if (!isNonEmptyValue(registrationData.getHouseNumber())) {
                        return false;
                    } else if (!isNonEmptyValue(registrationData.getPostalCode())) {
                        return false;
                    } else if (!isNonEmptyValue(registrationData.getCity())) {
                        return false;
                    } else {
                        return true;
                    }
                });
    }

    private boolean isNonEmptyValue(@Nullable String value) {
        return value != null && !value.isEmpty();
    }

    /*
        Phone number verification requests
     */

    /**
     * Request a TAN for the given phone number.
     *
     * @param formattedPhoneNumber Phone number in E.164 (FQTN) format
     */
    public Single<String> requestPhoneNumberVerificationTan(String formattedPhoneNumber) {
        return Single.defer(() -> {
            JsonObject message = new JsonObject();
            message.addProperty("phone", formattedPhoneNumber);
            return networkManager.getLucaEndpoints().requestPhoneNumberVerificationTan(message)
                    .doOnSubscribe(disposable -> Timber.i("Requesting TAN for %s", formattedPhoneNumber))
                    .map(jsonObject -> jsonObject.get("challengeId").getAsString());
        });
    }

    public Completable verifyPhoneNumberWithVerificationTan(String verificationTan, String challengeId) {
        return verifyPhoneNumberWithVerificationTan(verificationTan, Collections.singletonList(challengeId));
    }

    public Completable verifyPhoneNumberWithVerificationTan(String verificationTan, List<String> challengeIds) {
        return Completable.defer(() -> {
            JsonObject jsonObject = new JsonObject();
            JsonArray challengeIdArray = new JsonArray(challengeIds.size());
            for (String challengeId : challengeIds) {
                challengeIdArray.add(challengeId);
            }
            jsonObject.add("challengeIds", challengeIdArray);
            jsonObject.addProperty("tan", verificationTan);
            return networkManager.getLucaEndpoints().verifyPhoneNumberBulk(jsonObject);
        });
    }

    /*
        Registration and update requests
     */

    public Completable registerUser() {
        return createUserRegistrationRequestData()
                .doOnSuccess(data -> Timber.d("User registration request data: %s", data))
                .flatMap(data -> networkManager.getLucaEndpoints().registerUser(data)
                        .map(jsonObject -> jsonObject.get("userId").getAsString())
                        .map(UUID::fromString))
                .doOnSuccess(userId -> Timber.i("Registered user for ID: %s", userId))
                .flatMapCompletable(userId -> Completable.mergeArray(
                        preferencesManager.persist(REGISTRATION_COMPLETED_KEY, true),
                        preferencesManager.persist(USER_ID_KEY, userId),
                        getOrCreateRegistrationData()
                                .doOnSuccess(registrationData -> registrationData.setId(userId))
                                .flatMapCompletable(this::persistRegistrationData)
                ));

    }

    public Completable updateUser() {
        return createUserRegistrationRequestData()
                .doOnSuccess(data -> data.setGuestKeyPairPublicKey(null)) // not part of update request
                .doOnSuccess(data -> Timber.d("User update request data: %s", data))
                .flatMapCompletable(data -> getUserIdIfAvailable()
                        .flatMapCompletable(userId -> networkManager.getLucaEndpoints().updateUser(userId.toString(), data)))
                .doOnComplete(() -> Timber.i("Updated user"));

    }

    private Single<UserRegistrationRequestData> createUserRegistrationRequestData() {
        return getOrCreateRegistrationData()
                .flatMap(this::createContactData)
                .flatMap(this::encryptContactData)
                .flatMap(encryptedDataAndIv -> Single.fromCallable(() -> {
                    UserRegistrationRequestData requestData = new UserRegistrationRequestData();

                    String serializedEncryptedData = serializeToBase64(encryptedDataAndIv.first).blockingGet();
                    requestData.setEncryptedContactData(serializedEncryptedData);

                    String serializedIv = serializeToBase64(encryptedDataAndIv.second).blockingGet();
                    requestData.setIv(serializedIv);

                    byte[] mac = createContactDataMac(encryptedDataAndIv.first).blockingGet();
                    String serializedMac = serializeToBase64(mac).blockingGet();
                    requestData.setMac(serializedMac);

                    byte[] signature = createContactDataSignature(encryptedDataAndIv.first, mac, encryptedDataAndIv.second).blockingGet();
                    String serializedSignature = serializeToBase64(signature).blockingGet();
                    requestData.setSignature(serializedSignature);

                    ECPublicKey publicKey = cryptoManager.getGuestKeyPairPublicKey().blockingGet();
                    String serializedPublicKey = AsymmetricCipherProvider.encode(publicKey)
                            .flatMap(SerializationUtil::serializeToBase64)
                            .blockingGet();
                    requestData.setGuestKeyPairPublicKey(serializedPublicKey);

                    return requestData;
                }));
    }

    public Single<ContactData> createContactData(@NonNull RegistrationData registrationData) {
        return Single.just(new ContactData(registrationData));
    }

    public Single<Pair<byte[], byte[]>> encryptContactData(@NonNull ContactData contactData) {
        return SerializationUtil.serializeToJson(contactData)
                .map(contactDataJson -> contactDataJson.getBytes(StandardCharsets.UTF_8))
                .flatMap(encodedContactData -> Single.zip(
                        cryptoManager.getDataSecret()
                                .flatMap(cryptoManager::generateDataEncryptionSecret)
                                .flatMap(CryptoManager::createKeyFromSecret),
                        cryptoManager.generateSecureRandomData(16),
                        Pair::new
                ).flatMap(dataEncryptionKeyAndIv -> cryptoManager.getSymmetricCipherProvider()
                        .encrypt(encodedContactData, dataEncryptionKeyAndIv.second, dataEncryptionKeyAndIv.first)
                        .map(encryptedData -> new Pair<>(encryptedData, dataEncryptionKeyAndIv.second))));
    }

    public Single<byte[]> createContactDataMac(byte[] encryptedContactData) {
        return cryptoManager.getDataSecret()
                .flatMap(cryptoManager::generateDataAuthenticationSecret)
                .flatMap(CryptoManager::createKeyFromSecret)
                .flatMap(dataAuthenticationKey -> cryptoManager.getMacProvider().sign(encryptedContactData, dataAuthenticationKey));
    }

    public Single<byte[]> createContactDataSignature(byte[] encryptedContactData, byte[] mac, byte[] iv) {
        return CryptoManager.concatenate(encryptedContactData, mac, iv)
                .flatMap(concatenatedData -> cryptoManager.getGuestKeyPairPrivateKey()
                        .flatMap(userMasterPrivateKey -> cryptoManager.getSignatureProvider()
                                .sign(concatenatedData, userMasterPrivateKey)));
    }

    /*
        Data transfer request
     */

    public Single<String> transferUserData() {
        // TODO: 24.03.21 This doesn't seem to belong to the registration process
        return createDataTransferRequestData()
                .doOnSuccess(data -> Timber.d("User data transfer request data: %s", data))
                .flatMap(data -> networkManager.getLucaEndpoints().getTracingTan(data))
                .map(jsonObject -> jsonObject.get("tan").getAsString());
    }

    private Single<DataTransferRequestData> createDataTransferRequestData() {
        return Single.just(new DataTransferRequestData())
                .flatMap(transferRequestData -> createTransferData()
                        .doOnSuccess(transferData -> Timber.i("Encrypting transfer data: %s", transferData))
                        .flatMap(this::encryptTransferData)
                        .flatMap(encryptedTransferDataAndIv -> Completable.mergeArray(
                                serializeToBase64(encryptedTransferDataAndIv.first)
                                        .doOnSuccess(transferRequestData::setEncryptedContactData)
                                        .ignoreElement(),
                                serializeToBase64(encryptedTransferDataAndIv.second)
                                        .doOnSuccess(transferRequestData::setIv)
                                        .ignoreElement(),
                                createTransferDataMac(encryptedTransferDataAndIv.first)
                                        .flatMap(SerializationUtil::serializeToBase64)
                                        .doOnSuccess(transferRequestData::setMac)
                                        .ignoreElement(),
                                cryptoManager.getGuestKeyPairPublicKey()
                                        .flatMap(AsymmetricCipherProvider::encode)
                                        .flatMap(SerializationUtil::serializeToBase64)
                                        .doOnSuccess(transferRequestData::setGuestKeyPairPublicKey)
                                        .ignoreElement(),
                                cryptoManager.getDailyKeyPairPublicKeyWrapper()
                                        .map(DailyKeyPairPublicKeyWrapper::getId)
                                        .doOnSuccess(transferRequestData::setDailyPublicKeyId)
                                        .ignoreElement()
                        ).toSingle(() -> transferRequestData)));
    }

    public Single<TransferData> createTransferData() {
        return Single.just(new TransferData())
                .flatMap(transferData -> Completable.mergeArray(
                        getUserIdIfAvailable()
                                .toSingle()
                                .map(UUID::toString)
                                .doOnSuccess(transferData::setUserId)
                                .ignoreElement(),
                        cryptoManager.restoreRecentTracingSecrets(14)
                                .map(pair -> {
                                    TransferData.TraceSecretWrapper traceSecretWrapper = new TransferData.TraceSecretWrapper();
                                    traceSecretWrapper.setTimestamp(TimeUtil.convertToUnixTimestamp(pair.first).blockingGet());
                                    traceSecretWrapper.setSecret(CryptoManager.encodeToString(pair.second).blockingGet());
                                    return traceSecretWrapper;
                                })
                                .toList()
                                .doOnSuccess(transferData::setTraceSecretWrappers)
                                .ignoreElement(),
                        cryptoManager.getDataSecret()
                                .flatMap(CryptoManager::encodeToString)
                                .doOnSuccess(transferData::setDataSecret)
                                .ignoreElement()
                ).toSingle(() -> transferData));
    }

    public Single<Pair<byte[], byte[]>> encryptTransferData(@NonNull TransferData transferData) {
        return SerializationUtil.serializeToJson(transferData)
                .doOnSuccess(transferDataJson -> Timber.d("Serialized transfer data: %s", transferDataJson))
                .map(transferDataJson -> transferDataJson.getBytes(StandardCharsets.UTF_8))
                .flatMap(encodedContactData -> cryptoManager.generateSecureRandomData(16)
                        .flatMap(iv -> cryptoManager.getSharedDiffieHellmanSecret()
                                .flatMap(cryptoManager::generateDataEncryptionSecret)
                                .flatMap(CryptoManager::createKeyFromSecret)
                                .map(dataEncryptionKey -> new Pair<>(dataEncryptionKey, iv)))
                        .flatMap(dataEncryptionKeyAndIv -> cryptoManager.getSymmetricCipherProvider()
                                .encrypt(encodedContactData, dataEncryptionKeyAndIv.second, dataEncryptionKeyAndIv.first)
                                .map(encryptedData -> new Pair<>(encryptedData, dataEncryptionKeyAndIv.second))));
    }

    public Single<byte[]> createTransferDataMac(byte[] encryptedTransferData) {
        return cryptoManager.getSharedDiffieHellmanSecret()
                .flatMap(cryptoManager::generateDataAuthenticationSecret)
                .flatMap(CryptoManager::createKeyFromSecret)
                .flatMap(dataAuthenticationKey -> cryptoManager.getMacProvider().sign(encryptedTransferData, dataAuthenticationKey));
    }

}

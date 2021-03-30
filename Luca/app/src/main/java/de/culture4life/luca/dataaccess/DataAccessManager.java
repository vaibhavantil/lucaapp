package de.culture4life.luca.dataaccess;

import android.content.Context;
import android.util.Pair;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.Manager;
import de.culture4life.luca.R;
import de.culture4life.luca.checkin.CheckInData;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.history.HistoryItem;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.AccessedHashedTraceIdsData;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.ui.MainActivity;
import de.culture4life.luca.util.TimeUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static de.culture4life.luca.notification.LucaNotificationManager.NOTIFICATION_ID_DATA_ACCESS;

public class DataAccessManager extends Manager {

    private static final String UPDATE_TAG = "data_access_update";
    public static final long UPDATE_INTERVAL = BuildConfig.DEBUG ? PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS : TimeUnit.HOURS.toMillis(12);
    public static final long UPDATE_FLEX_PERIOD = BuildConfig.DEBUG ? PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS : TimeUnit.HOURS.toMillis(2);
    public static final long UPDATE_INITIAL_DELAY = TimeUnit.SECONDS.toMillis(10);

    private static final String LAST_UPDATE_TIMESTAMP_KEY = "last_accessed_data_update_timestamp";
    private static final String LAST_INFO_SHOWN_TIMESTAMP_KEY = "last_accessed_data_info_shown_timestamp";
    private static final String ACCESSED_DATA_KEY = "accessed_data";

    private final PreferencesManager preferencesManager;
    private final NetworkManager networkManager;
    private final LucaNotificationManager notificationManager;
    private final CheckInManager checkInManager;
    private final HistoryManager historyManager;
    private final CryptoManager cryptoManager;

    private WorkManager workManager;

    @Nullable
    private AccessedData accessedData;

    public DataAccessManager(@NonNull PreferencesManager preferencesManager, @NonNull NetworkManager networkManager, @NonNull LucaNotificationManager notificationManager, @NonNull CheckInManager checkInManager, @NonNull HistoryManager historyManager, @NonNull CryptoManager cryptoManager) {
        this.preferencesManager = preferencesManager;
        this.networkManager = networkManager;
        this.notificationManager = notificationManager;
        this.checkInManager = checkInManager;
        this.historyManager = historyManager;
        this.cryptoManager = cryptoManager;
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.mergeArray(
                preferencesManager.initialize(context),
                networkManager.initialize(context),
                notificationManager.initialize(context),
                checkInManager.initialize(context),
                historyManager.initialize(context),
                cryptoManager.initialize(context)
        ).andThen(Completable.fromAction(() -> {
            this.context = context;
            if (!LucaApplication.isRunningUnitTests()) {
                this.workManager = WorkManager.getInstance(context);
            }
        })).andThen(initializeUpdates());
    }

    /*
        Updates
     */

    private Completable initializeUpdates() {
        return Completable.fromAction(() -> managerDisposable.add(startUpdatingInRegularIntervals()
                .delaySubscription(UPDATE_INITIAL_DELAY, TimeUnit.MILLISECONDS)
                .doOnError(throwable -> Timber.e("Unable to start updating in regular intervals: %s", throwable.toString()))
                .onErrorComplete()
                .subscribe()));
    }

    private Completable startUpdatingInRegularIntervals() {
        return getNextRecommendedUpdateDelay()
                .flatMapCompletable(initialDelay -> Completable.fromAction(() -> {
                    if (workManager == null) {
                        managerDisposable.add(Observable.interval(initialDelay, UPDATE_INTERVAL, TimeUnit.MILLISECONDS, Schedulers.io())
                                .flatMapCompletable(tick -> update()
                                        .doOnError(throwable -> Timber.w("Unable to update: %s", throwable.toString()))
                                        .onErrorComplete())
                                .subscribe());
                    } else {
                        Constraints constraints = new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build();

                        WorkRequest updateWorkRequest = new PeriodicWorkRequest.Builder(
                                UpdateWorker.class,
                                UPDATE_INTERVAL, TimeUnit.MILLISECONDS,
                                UPDATE_FLEX_PERIOD, TimeUnit.MILLISECONDS
                        ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                                .setConstraints(constraints)
                                .addTag(UPDATE_TAG)
                                .build();

                        workManager.cancelAllWorkByTag(UPDATE_TAG);
                        workManager.enqueue(updateWorkRequest);
                        Timber.d("Update work request submitted to work manager");
                    }
                }));
    }

    public Completable update() {
        return fetchNewRecentlyAccessedTraceData()
                .toList()
                .flatMapCompletable(this::processNewRecentlyAccessedTraceData)
                .andThen(preferencesManager.persist(LAST_UPDATE_TIMESTAMP_KEY, System.currentTimeMillis()))
                .doOnSubscribe(disposable -> Timber.d("Updating accessed data"))
                .doOnComplete(() -> Timber.d("Accessed data update complete"))
                .doOnError(throwable -> Timber.w("Accessed data update failed: %s", throwable.toString()));
    }

    public Single<Long> getDurationSinceLastUpdate() {
        return preferencesManager.restoreOrDefault(LAST_UPDATE_TIMESTAMP_KEY, 0L)
                .map(lastUpdateTimestamp -> System.currentTimeMillis() - lastUpdateTimestamp);
    }

    public Single<Long> getNextRecommendedUpdateDelay() {
        return getDurationSinceLastUpdate()
                .map(durationSinceLastUpdate -> UPDATE_INTERVAL - durationSinceLastUpdate)
                .map(recommendedDelay -> Math.max(0, recommendedDelay))
                .doOnSuccess(recommendedDelay -> {
                    String readableDelay = TimeUtil.getReadableApproximateDuration(recommendedDelay, context).blockingGet();
                    Timber.v("Recommended update delay: %s", readableDelay);
                });
    }

    public Completable processNewRecentlyAccessedTraceData(@NonNull List<AccessedTraceData> accessedTraceData) {
        return Completable.defer(() -> {
            if (accessedTraceData.isEmpty()) {
                return Completable.complete();
            } else {
                return Completable.mergeArray(
                        addToAccessedData(accessedTraceData).subscribeOn(Schedulers.io()),
                        addHistoryItems(accessedTraceData).subscribeOn(Schedulers.io()),
                        notifyUserAboutDataAccess(accessedTraceData).subscribeOn(Schedulers.io())
                );
            }
        }).doOnSubscribe(disposable -> Timber.d("New accessed trace data: %s", accessedTraceData));
    }

    /**
     * Informs the user that health authorities have accessed data related to recent check-ins.
     */
    public Completable notifyUserAboutDataAccess(@NonNull List<AccessedTraceData> accessedTraceData) {
        return Single.fromCallable(() -> notificationManager.createDataAccessedNotificationBuilder(MainActivity.class).build())
                .flatMapCompletable(notification -> notificationManager.showNotification(NOTIFICATION_ID_DATA_ACCESS, notification));
    }

    /**
     * Creates history items for the specified data, allowing the user to see which data has been
     * accessed after dismissing the notification.
     */
    public Completable addHistoryItems(@NonNull List<AccessedTraceData> accessedTraceData) {
        return Observable.fromIterable(accessedTraceData)
                .flatMapCompletable(historyManager::addTraceDataAccessedItem);
    }

    public Maybe<CheckInData> getCheckInData(@NonNull AccessedTraceData accessedTraceData) {
        return checkInManager.getArchivedCheckInData(accessedTraceData.getTraceId());
    }

    public Single<String> getLocationName(@NonNull AccessedTraceData accessedTraceData) {
        return getCheckInData(accessedTraceData)
                .flatMap(checkInData -> Maybe.fromCallable(checkInData::getLocationDisplayName))
                .defaultIfEmpty(context.getString(R.string.unknown));
    }

    public Single<Pair<Long, Long>> getCheckInAndOutTimestamps(@NonNull AccessedTraceData accessedTraceData) {
        Single<Long> getCheckInTimestamp = getHistoryItemTimestamp(HistoryItem.TYPE_CHECK_IN, accessedTraceData)
                .defaultIfEmpty(accessedTraceData.getAccessTimestamp());

        Single<Long> getCheckOutTimestamp = getHistoryItemTimestamp(HistoryItem.TYPE_CHECK_OUT, accessedTraceData)
                .defaultIfEmpty(accessedTraceData.getAccessTimestamp());

        return Single.zip(getCheckInTimestamp, getCheckOutTimestamp, Pair::new);
    }

    private Maybe<Long> getHistoryItemTimestamp(@HistoryItem.Type int type, @NonNull AccessedTraceData accessedTraceData) {
        return getRelatedHistoryItems(accessedTraceData)
                .filter(historyItem -> historyItem.getType() == type)
                .map(HistoryItem::getTimestamp)
                .firstElement();
    }

    private Observable<HistoryItem> getRelatedHistoryItems(@NonNull AccessedTraceData accessedTraceData) {
        return historyManager.getItems()
                .filter(historyItem -> accessedTraceData.getTraceId().equals(historyItem.getRelatedId()));
    }

    /*
        Trace Data
     */

    /**
     * Emits trace IDs related to recent check-ins.
     */
    public Observable<String> getRecentTraceIds() {
        return checkInManager.getArchivedTraceIds();
    }

    public Observable<AccessedHashedTraceIdsData> fetchAllRecentlyAccessedHashedTraceIdsData() {
        return networkManager.getLucaEndpoints().getAccessedTraces()
                .flatMapObservable(Observable::fromIterable);
    }

    /**
     * Emits trace data that is related to the user and has recently been accessed. The intersection
     * of {@link #getRecentTraceIds()} and {@link #fetchAllRecentlyAccessedHashedTraceIdsData()}.
     */
    public Observable<AccessedTraceData> fetchRecentlyAccessedTraceData() {
        // get all recent trace IDs from the user that could have been accessed
        Observable<String> potentiallyAccessedTraceIds = getRecentTraceIds().cache();

        // get the intersection of data from the user and data that has been accessed
        return fetchAllRecentlyAccessedHashedTraceIdsData()
                .flatMap(accessedHashedData -> potentiallyAccessedTraceIds
                        .flatMapSingle(traceId -> getHashedTraceId(accessedHashedData.getHealthDepartment().getId(), traceId)
                                .map(hashedTraceId -> {
                                    AccessedTraceData potentiallyAccessedData = new AccessedTraceData();
                                    potentiallyAccessedData.setTraceId(traceId);
                                    potentiallyAccessedData.setHashedTraceId(hashedTraceId);
                                    return potentiallyAccessedData;
                                }))
                        .filter(potentiallyAccessedData -> hasBeenAccessed(potentiallyAccessedData, accessedHashedData))
                        .map(accessedData -> {
                            accessedData.setAccessTimestamp(System.currentTimeMillis());
                            accessedData.setHealthDepartmentId(accessedHashedData.getHealthDepartment().getId());
                            accessedData.setHealthDepartmentName(accessedHashedData.getHealthDepartment().getName());
                            accessedData.setLocationName(getLocationName(accessedData).blockingGet());
                            Pair<Long, Long> checkInAndOutTimestamps = getCheckInAndOutTimestamps(accessedData).blockingGet();
                            accessedData.setCheckInTimestamp(checkInAndOutTimestamps.first);
                            accessedData.setCheckOutTimestamp(checkInAndOutTimestamps.second);
                            return accessedData;
                        }));
    }

    /**
     * Emits trace data that has been accessed after the last time the accessed trace data has been
     * updated. So all data from {@link #fetchRecentlyAccessedTraceData()} without the data from
     * {@link #getPreviouslyAccessedTraceData()}.
     */
    public Observable<AccessedTraceData> fetchNewRecentlyAccessedTraceData() {
        return getPreviouslyAccessedTraceData()
                .map(AccessedTraceData::getHashedTraceId)
                .toList()
                .flatMapObservable(previouslyAccessedHashes -> fetchRecentlyAccessedTraceData()
                        .filter(accessedTraceData -> !previouslyAccessedHashes.contains(accessedTraceData.getHashedTraceId())));
    }

    /**
     * Emits trace data that has been accessed before the last time the accessed trace data has been
     * updated.
     */
    public Observable<AccessedTraceData> getPreviouslyAccessedTraceData() {
        return getOrRestoreAccessedData()
                .map(AccessedData::getTraceData)
                .flatMapObservable(Observable::fromIterable);
    }

    /**
     * Emits trace data that has been accessed after the last time the user has been shown an info
     * about previously accessed data.
     */
    public Observable<AccessedTraceData> getAccessedTraceDataNotYetInformedAbout() {
        return preferencesManager.restoreOrDefault(LAST_INFO_SHOWN_TIMESTAMP_KEY, 0L)
                .flatMapObservable(lastInfoTimestamp -> getPreviouslyAccessedTraceData()
                        .filter(accessedTraceData -> accessedTraceData.getAccessTimestamp() > lastInfoTimestamp));
    }

    public Completable markAllAccessedTraceDataAsInformedAbout() {
        return preferencesManager.persist(LAST_INFO_SHOWN_TIMESTAMP_KEY, System.currentTimeMillis());
    }

    /**
     * Checks if the hashed trace ID from the specified {@link AccessedTraceData} is present in the
     * list of accessed hashes from the specified {@link AccessedHashedTraceIdsData}.
     */
    public static boolean hasBeenAccessed(@NonNull AccessedTraceData potentiallyAccessedData, @NonNull AccessedHashedTraceIdsData accessedData) {
        return accessedData.getHashedTraceIds().contains(potentiallyAccessedData.getHashedTraceId());
    }

    /**
     * Emits true if the specified trace ID is part of the accessed data.
     */
    public Single<Boolean> hasBeenAccessed(@NonNull String traceId) {
        return getPreviouslyAccessedTraceData()
                .filter(accessedTraceData -> traceId.equals(accessedTraceData.getTraceId()))
                .firstElement()
                .map(accessedTraceData -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Hashes the specified base64 encoded trace ID and encodes the result back to base64.
     */
    public Single<String> getHashedTraceId(@NonNull String healthDepartmentId, @NonNull String traceId) {
        Single<byte[]> getMessage = Single.just(traceId)
                .flatMap(CryptoManager::decodeFromString);

        Single<SecretKey> getKey = Single.just(UUID.fromString(healthDepartmentId))
                .flatMap(CryptoManager::encode)
                .flatMap(CryptoManager::createKeyFromSecret);

        return Single.zip(getMessage, getKey, (message, key) -> cryptoManager.getMacProvider().sign(message, key))
                .flatMap(sign -> sign)
                .flatMap(signature -> CryptoManager.trim(signature, 16))
                .flatMap(CryptoManager::encodeToString);
    }

    /*
        Accessed Data
     */

    public Single<AccessedData> getOrRestoreAccessedData() {
        return Maybe.fromCallable(() -> accessedData)
                .switchIfEmpty(restoreAccessedData());
    }

    public Single<AccessedData> restoreAccessedData() {
        return preferencesManager.restoreOrDefault(ACCESSED_DATA_KEY, new AccessedData())
                .doOnSubscribe(disposable -> Timber.d("Restoring accessed data"))
                .doOnSuccess(restoredData -> this.accessedData = restoredData);
    }

    public Completable persistAccessedData(@NonNull AccessedData accessedData) {
        return preferencesManager.persist(ACCESSED_DATA_KEY, accessedData)
                .doOnSubscribe(disposable -> {
                    Timber.d("Persisting accessed data");
                    this.accessedData = accessedData;
                });
    }

    /**
     * Persists the specified trace data, so that they will be part of {@link
     * #getPreviouslyAccessedTraceData()}.
     */
    public Completable addToAccessedData(@NonNull List<AccessedTraceData> accessedTraceData) {
        return getOrRestoreAccessedData()
                .doOnSuccess(accessedData -> accessedData.addData(accessedTraceData))
                .flatMapCompletable(this::persistAccessedData)
                .doOnComplete(() -> Timber.d("Added trace data to accessed data: %s", accessedTraceData));
    }

}

package de.culture4life.luca.location;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.Manager;
import de.culture4life.luca.util.RxTasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static android.location.LocationManager.PROVIDERS_CHANGED_ACTION;

public class GeofenceManager extends Manager {

    /**
     * Note that alerts can be late. The geofence service doesn't continuously query for location,
     * so expect some latency when receiving alerts. Usually the latency is less than 2 minutes,
     * even less when the device has been moving. If Background Location Limits are in effect, the
     * latency is about 2-3 minutes on average. If the device has been stationary for a significant
     * period of time, the latency may increase (up to 6 minutes).
     */
    public static final long UPDATE_INTERVAL_DEFAULT = BuildConfig.DEBUG ? TimeUnit.SECONDS.toMillis(30) : TimeUnit.MINUTES.toMillis(2);

    /**
     * From the android documentation regarding geofence creation and monitoring:
     *
     * "For best results, the minimum radius of the geofence should be set between 100 - 150 meters.
     * When Wi-Fi is available location accuracy is usually between 20 - 50 meters. When indoor
     * location is available, the accuracy range can be as small as 5 meters. Unless you know indoor
     * location is available inside the geofence, assume that Wi-Fi location accuracy is about 50
     * meters."
     *
     * @see <a href="https://developer.android.com/training/location/geofencing#Troubleshooting">Create
     *         and monitor geofences</a>
     */
    public static final long MINIMUM_GEOFENCE_RADIUS = 50;
    public static final long MAXIMUM_GEOFENCE_RADIUS = 5000;

    @Nullable
    private BroadcastReceiver providerChangedReceiver;
    private GeofencingClient geofenceClient;

    private final Map<Geofence, PublishProcessor<GeofenceEvent>> eventPublishersMap;
    private final Map<GeofencingRequest, PendingIntent> pendingIntentsMap;

    public GeofenceManager() {
        this.eventPublishersMap = new HashMap<>();
        this.pendingIntentsMap = new HashMap<>();
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.fromAction(() -> geofenceClient = LocationServices.getGeofencingClient(context));
    }

    /*
        Geofence requests
     */

    /**
     * Will call {@link #addGeofences(GeofencingRequest)} and emit the results of {@link
     * #getGeofenceEvents(Geofence)} for all requested geofences. Will also call {@link
     * #removeGeofences(GeofencingRequest)} when disposing the observable.
     *
     * If you just want to get the {@link GeofenceEvent}s from a previously added request, use
     * {@link #getGeofenceEvents(Geofence)} instead.
     */
    @RequiresPermission("android.permission.ACCESS_FINE_LOCATION")
    public Observable<GeofenceEvent> getGeofenceEvents(@NonNull GeofencingRequest geofencingRequest) {
        return addGeofences(geofencingRequest)
                .andThen(Observable.fromIterable(geofencingRequest.getGeofences()))
                .flatMap(this::getGeofenceEvents)
                .doFinally(() -> managerDisposable.add(removeGeofences(geofencingRequest)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                () -> Timber.i("Removed geofences for request: %s", geofencingRequest),
                                throwable -> Timber.e(throwable, "Unable to remove geofences for request: %s", geofencingRequest)
                        )));
    }

    /**
     * Will emit events related to the specified {@link Geofence}.
     *
     * Note that subscribing to this method will not call {@link #addGeofences(GeofencingRequest)}
     * and disposing will not call {@link #removeGeofences(GeofencingRequest)}. Use {@link
     * #getGeofenceEvents(GeofencingRequest)} instead if you want to add (and remove) new
     * geofences.
     */
    public Observable<GeofenceEvent> getGeofenceEvents(@NonNull Geofence geofence) {
        return Observable.defer(() -> getOrCreateEventPublisher(geofence).toObservable());
    }

    /**
     * Will add the specified {@link GeofencingRequest} to the {@link #geofenceClient} and register
     * a new {@link GeofenceBroadcastReceiver} if required.
     */
    @RequiresPermission("android.permission.ACCESS_FINE_LOCATION")
    public Completable addGeofences(@NonNull GeofencingRequest geofencingRequest) {
        return Single.defer(() -> getInitializedField(geofenceClient))
                .flatMapCompletable(geofencingClient -> {
                    PendingIntent pendingIntent = getOrCreatePendingIntent(geofencingRequest);
                    return RxTasks.toCompletable(geofencingClient.addGeofences(geofencingRequest, pendingIntent));
                })
                .andThen(registerProviderChangeBroadcastReceiverIfRequired())
                .doOnSubscribe(disposable -> Timber.i("Adding geofence request: %s", geofencingRequest))
                .onErrorResumeNext(throwable -> Completable.error(
                        new GeofenceException("Unable add geofence request", throwable)
                ));
    }

    /**
     * Will call {@link #removeGeofence(Geofence)} for each {@link Geofence} from the request and
     * remove the specified {@link GeofencingRequest} from the {@link #geofenceClient}.
     */
    public Completable removeGeofences(@NonNull GeofencingRequest geofencingRequest) {
        return Completable.defer(() -> Observable.fromIterable(geofencingRequest.getGeofences())
                .flatMapCompletable(this::removeGeofence))
                .andThen(Single.defer(() -> getInitializedField(geofenceClient)))
                .flatMapCompletable(geofencingClient -> {
                    PendingIntent pendingIntent = getOrCreatePendingIntent(geofencingRequest);
                    return RxTasks.toCompletable(geofencingClient.removeGeofences(pendingIntent));
                })
                .andThen(Completable.fromAction(() -> {
                    synchronized (pendingIntentsMap) {
                        pendingIntentsMap.remove(geofencingRequest);
                    }
                }))
                .doOnSubscribe(disposable -> Timber.i("Removing geofence request: %s", geofencingRequest))
                .onErrorResumeNext(throwable -> Completable.error(
                        new GeofenceException("Unable remove geofence request", throwable)
                ));
    }

    private Completable removeGeofence(@NonNull Geofence geofence) {
        return Completable.fromAction(() -> completeEventPublisher(geofence));
    }

    /*
        Broadcast receiver
     */

    private Completable registerProviderChangeBroadcastReceiver() {
        return Completable.fromAction(() -> {
            Timber.d("Registering provider change broadcast receiver");
            providerChangedReceiver = new GeofenceBroadcastReceiver();
            context.registerReceiver(providerChangedReceiver, new IntentFilter(PROVIDERS_CHANGED_ACTION));

            // unregister the broadcast receiver when the manager gets disposed
            managerDisposable.add(Completable.never()
                    .doOnDispose(() -> unregisterProviderChangeBroadcastReceiverIfRequired()
                            .onErrorComplete()
                            .subscribe())
                    .subscribeOn(Schedulers.io())
                    .subscribe());
        }).doOnError(throwable -> providerChangedReceiver = null);
    }

    private Completable registerProviderChangeBroadcastReceiverIfRequired() {
        return Completable.defer(() -> {
            if (providerChangedReceiver != null) {
                return Completable.complete();
            } else {
                return registerProviderChangeBroadcastReceiver();
            }
        });
    }

    private Completable unregisterProviderChangeBroadcastReceiverIfRequired() {
        return Completable.fromAction(() -> {
            if (providerChangedReceiver == null) {
                return;
            }
            Timber.d("Unregistering provider change broadcast receiver");
            context.unregisterReceiver(providerChangedReceiver);
        }).doOnComplete(() -> providerChangedReceiver = null);
    }

    protected Completable handleBroadcastReceiverIntent(@NonNull Intent intent) {
        return Completable.defer(() -> {
            String action = intent.getAction();
            if (PROVIDERS_CHANGED_ACTION.equals(action)) {
                return handleBroadcastedProviderChangeEvent();
            } else {
                return handleBroadcastedGeofencingEvent(GeofencingEvent.fromIntent(intent));
            }
        });
    }

    private Completable handleBroadcastedProviderChangeEvent() {
        return Completable.defer(() -> {
            if (LocationManager.isLocationServiceEnabled(context)) {
                return Completable.complete();
            } else {
                // We treat a disabled location service as an error here, which will result
                // in geofences being removed. This is specific to our current requirements.
                // We could also ignore this and wait for the location service to be enabled again.
                GeofenceException geofenceException = new GeofenceException("Location service disabled");
                return Observable.fromIterable(getEventPublishers())
                        .filter(this::isEventPublisherActive)
                        .doOnNext(geofenceEventPublishProcessor -> geofenceEventPublishProcessor.onError(geofenceException))
                        .ignoreElements();
            }
        }).doOnSubscribe(disposable -> Timber.d("Handling provider change event"));
    }

    private Completable handleBroadcastedGeofencingEvent(@NonNull GeofencingEvent event) {
        return Single.just(new GeofenceEvent(event))
                .doOnSuccess(geofenceEvent -> Timber.d("Handling geofence event: %s", geofenceEvent))
                .flatMapCompletable(geofenceEvent -> Observable.fromIterable(getEventPublishers())
                        .filter(this::isEventPublisherActive)
                        .doOnNext(eventPublisher -> eventPublisher.onNext(geofenceEvent))
                        .ignoreElements());
    }

    /*
        Event publishers
     */

    private void completeEventPublisher(@NonNull Geofence geofence) {
        PublishProcessor<GeofenceEvent> eventPublisher;
        synchronized (eventPublishersMap) {
            if (!eventPublishersMap.containsKey(geofence)) {
                return;
            }
            eventPublisher = getOrCreateEventPublisher(geofence);
            eventPublishersMap.remove(geofence);
        }
        eventPublisher.onComplete();
    }

    private List<PublishProcessor<GeofenceEvent>> getEventPublishers() {
        List<PublishProcessor<GeofenceEvent>> eventPublishers;
        synchronized (eventPublishersMap) {
            eventPublishers = new ArrayList<>(eventPublishersMap.values());
        }
        return eventPublishers;
    }

    private PublishProcessor<GeofenceEvent> getOrCreateEventPublisher(@NonNull Geofence geofence) {
        PublishProcessor<GeofenceEvent> eventPublisher;
        synchronized (eventPublishersMap) {
            eventPublisher = eventPublishersMap.get(geofence);
            if (!isEventPublisherActive(eventPublisher)) {
                eventPublisher = PublishProcessor.create();
                eventPublishersMap.put(geofence, eventPublisher);
            }
        }
        return eventPublisher;
    }

    private boolean isEventPublisherActive(@Nullable PublishProcessor<GeofenceEvent> publishProcessor) {
        return publishProcessor != null && !publishProcessor.hasComplete() && !publishProcessor.hasThrowable();
    }

    private PendingIntent getOrCreatePendingIntent(@NonNull GeofencingRequest geofencingRequest) {
        PendingIntent pendingIntent;
        synchronized (pendingIntentsMap) {
            pendingIntent = pendingIntentsMap.get(geofencingRequest);
            if (pendingIntent == null) {
                pendingIntent = PendingIntent.getBroadcast(context, geofencingRequest.hashCode(), new Intent(context, GeofenceBroadcastReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT);
                pendingIntentsMap.put(geofencingRequest, pendingIntent);
            }
        }
        return pendingIntent;
    }

}

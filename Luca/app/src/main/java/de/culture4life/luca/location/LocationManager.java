package de.culture4life.luca.location;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;

import de.culture4life.luca.Manager;
import de.culture4life.luca.util.RxTasks;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class LocationManager extends Manager {

    private Context context;
    private FusedLocationProviderClient fusedLocationProviderClient;

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.fromAction(() -> {
            this.context = context;
            fusedLocationProviderClient = new FusedLocationProviderClient(context);
        });
    }

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Maybe<Location> getLastKnownLocation() {
        return getFusedLocationProviderClient()
                .flatMapMaybe(locationProvider -> RxTasks.toMaybe(locationProvider.getLastLocation()));
    }

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Maybe<Double> getLastKnownDistanceTo(@NonNull Location location) {
        return getLastKnownLocation()
                .map(lastKnownLocation -> (double) lastKnownLocation.distanceTo(location));
    }

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Observable<Location> getLocationUpdates() {
        return Single.fromCallable(() -> {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
            locationRequest.setInterval(TimeUnit.MINUTES.toMillis(1));
            locationRequest.setFastestInterval(TimeUnit.SECONDS.toMillis(1));
            return locationRequest;
        }).flatMapObservable(this::getLocationUpdates);
    }

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Observable<Location> getLocationUpdates(@NonNull LocationRequest locationRequest) {
        return getFusedLocationProviderClient()
                .flatMapObservable(locationProvider -> Observable.create(emitter -> {
                    LocationCallback locationCallback = new LocationCallback() {
                        @Override
                        public void onLocationResult(LocationResult locationResult) {
                            Location lastLocation = locationResult.getLastLocation();
                            if (!emitter.isDisposed() && lastLocation != null) {
                                emitter.onNext(lastLocation);
                            }
                        }

                        @Override
                        public void onLocationAvailability(LocationAvailability locationAvailability) {
                            if (!emitter.isDisposed() && !locationAvailability.isLocationAvailable()) {
                                emitter.onError(new LocationUnavailableException("Location provider claims that location is not available"));
                            }
                        }
                    };
                    locationProvider.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                    emitter.setCancellable(() -> locationProvider.removeLocationUpdates(locationCallback));
                }));
    }

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Observable<Double> getDistanceUpdatesTo(@NonNull Location location) {
        return getLocationUpdates()
                .flatMapMaybe(updatedLocation -> getLastKnownDistanceTo(location));
    }

    public Single<FusedLocationProviderClient> getFusedLocationProviderClient() {
        return Maybe.fromCallable(() -> fusedLocationProviderClient)
                .switchIfEmpty(Single.error(new IllegalStateException("Location manager has not been initialized yet")));
    }

    public boolean isLocationServiceEnabled() {
        return isLocationServiceEnabled(context);
    }

    public boolean hasLocationPermission() {
        return hasLocationPermission(context);
    }

    public static boolean isLocationServiceEnabled(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.location.LocationManager locationManager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return locationManager.isLocationEnabled();
        } else {
            int locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        }
    }

    public static boolean hasLocationPermission(@NonNull Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

}

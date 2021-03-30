package de.culture4life.luca.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.culture4life.luca.LucaApplication;

import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.d("onReceive() called with: context = [%s], intent = [%s]", context, intent);
        LucaApplication application = (LucaApplication) context.getApplicationContext();
        GeofenceManager geofenceManager = application.getGeofenceManager();
        geofenceManager.initialize(context)
                .andThen(geofenceManager.handleBroadcastReceiverIntent(intent))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.i("Handled broadcast intent: %s", intent),
                        throwable -> Timber.e(throwable, "Unable to handle broadcast intent: %s", intent)
                );
    }

}

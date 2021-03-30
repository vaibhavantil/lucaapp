package de.culture4life.luca.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;

import de.culture4life.luca.Manager;
import de.culture4life.luca.R;
import de.culture4life.luca.service.LucaService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;

import static android.content.Context.NOTIFICATION_SERVICE;

public class LucaNotificationManager extends Manager {

    public static final int NOTIFICATION_ID_STATUS = 1;
    public static final int NOTIFICATION_ID_DATA_ACCESS = 2;
    public static final int NOTIFICATION_ID_EVENT = 3;
    private static final String NOTIFICATION_CHANNEL_ID_PREFIX = "channel_";
    private static final String NOTIFICATION_CHANNEL_ID_STATUS = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_STATUS;
    private static final String NOTIFICATION_CHANNEL_ID_DATA_ACCESS = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_DATA_ACCESS;
    public static final String NOTIFICATION_CHANNEL_ID_EVENT = NOTIFICATION_CHANNEL_ID_PREFIX + NOTIFICATION_ID_EVENT;

    private static final String BUNDLE_KEY = "notification_bundle";
    private static final String ACTION_KEY = "notification_action";

    public static final int ACTION_STOP = 1;
    public static final int ACTION_CHECKOUT = 2;
    public static final int ACTION_END_MEETING = 3;
    public static final int ACTION_SHOW_ACCESSED_DATA = 4;

    private static final long VIBRATION_DURATION = 200;

    private Context context;
    private NotificationManager notificationManager;

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.fromAction(() -> {
            this.context = context;
            this.notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannels();
            }
        });
    }

    /**
     * Will show the specified notification.
     */
    public Completable showNotification(int notificationId, @NonNull Notification notification) {
        return Completable.fromAction(() -> notificationManager.notify(notificationId, notification));
    }

    /**
     * Will hide the notification with the specified ID.
     */
    public Completable hideNotification(int notificationId) {
        return Completable.fromAction(() -> notificationManager.cancel(notificationId));
    }

    /**
     * Will show the specified notification until the subscription gets disposed.
     */
    public Completable showNotificationUntilDisposed(int notificationId, @NonNull Notification notification) {
        return Completable.fromAction(() -> notificationManager.notify(notificationId, notification))
                .doFinally(() -> notificationManager.cancel(notificationId));
    }

    public Completable vibrate() {
        return Completable.fromAction(() -> {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VIBRATION_DURATION);
            }
        });
    }

    /**
     * Creates all notification channels that might be used by the app, if targeting Android O or
     * later.
     *
     * @see <a href="https://developer.android.com/preview/features/notification-channels.html">Notification
     *         Channels</a>
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannels() {
        createStatusNotificationChannel();
        createAccessNotificationChannel();
        createEventNotificationChannel();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createStatusNotificationChannel() {
        CharSequence channelName = context.getString(R.string.notification_channel_status_title);
        String channelDescription = context.getString(R.string.notification_channel_status_description);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_STATUS, channelName, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(channelDescription);
        channel.enableLights(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createAccessNotificationChannel() {
        CharSequence channelName = context.getString(R.string.notification_channel_access_title);
        String channelDescription = context.getString(R.string.notification_channel_access_description);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_DATA_ACCESS, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(channelDescription);
        notificationManager.createNotificationChannel(channel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createEventNotificationChannel() {
        CharSequence channelName = context.getString(R.string.notification_channel_event_title);
        String channelDescription = context.getString(R.string.notification_channel_event_description);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_EVENT, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(channelDescription);
        channel.enableLights(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createCheckedInNotificationBuilder(Class activityClass) {
        return createStatusNotificationBuilder(activityClass, R.string.notification_service_title, R.string.notification_service_description)
                .addAction(createCheckoutAction());
    }

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createMeetingHostNotificationBuilder(Class activityClass) {
        return createStatusNotificationBuilder(activityClass, R.string.notification_meeting_host_title, R.string.notification_meeting_host_description)
                .addAction(createEndMeetingAction());
    }

    /**
     * Creates the default status notification builder, intended to serve the ongoing foreground
     * service notification.
     */
    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createStatusNotificationBuilder(Class activityClass, @StringRes int titleResourceId, @StringRes int descriptionResourceId) {
        return createBaseNotificationBuilder(activityClass, NOTIFICATION_CHANNEL_ID_STATUS, titleResourceId, descriptionResourceId)
                .setSmallIcon(R.drawable.ic_person_pin)
                .setContentIntent(createActivityIntent(activityClass, null))
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_STATUS);
    }

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createEventNotificationBuilder(Class activityClass, @StringRes int titleResourceId, @NonNull String description) {
        return createBaseNotificationBuilder(activityClass, NOTIFICATION_CHANNEL_ID_EVENT, titleResourceId, description)
                .setSmallIcon(R.drawable.ic_information_outline)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_EVENT);
    }

    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createErrorNotificationBuilder(Class activityClass, @NonNull String title, @NonNull String description) {
        return createBaseNotificationBuilder(activityClass, NOTIFICATION_CHANNEL_ID_EVENT, R.string.notification_error_title, description)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_error_outline)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_ERROR);
    }

    @SuppressWarnings("rawtypes")
    private NotificationCompat.Builder createBaseNotificationBuilder(Class activityClass, String notificationChannelId, @StringRes int titleResourceId, @StringRes int descriptionResourceId) {
        return createBaseNotificationBuilder(activityClass, notificationChannelId, titleResourceId, context.getText(descriptionResourceId).toString());
    }

    @SuppressWarnings("rawtypes")
    private NotificationCompat.Builder createBaseNotificationBuilder(Class activityClass, String notificationChannelId, @StringRes int titleResourceId, String description) {
        return new NotificationCompat.Builder(context, notificationChannelId)
                .setContentTitle(context.getText(titleResourceId))
                .setContentText(description)
                .setContentIntent(createActivityIntent(activityClass, null))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(description))
                .setOnlyAlertOnce(true);
    }

    public NotificationCompat.Builder createDataAccessedNotificationBuilder(Class activityClass) {
        return createAccessNotificationBuilder(activityClass, R.string.notification_data_accessed_title, R.string.notification_data_accessed_description);
    }

    /**
     * Creates the default data access notification builder, intended to inform the user that an
     * health department has accessed data related to the user.
     */
    @SuppressWarnings("rawtypes")
    public NotificationCompat.Builder createAccessNotificationBuilder(Class activityClass, @StringRes int titleResourceId, @StringRes int descriptionResourceId) {
        Bundle notificationBundle = new Bundle();
        notificationBundle.putInt(ACTION_KEY, ACTION_SHOW_ACCESSED_DATA);

        return new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID_DATA_ACCESS)
                .setContentTitle(context.getText(titleResourceId))
                .setContentText(context.getText(descriptionResourceId))
                .setSmallIcon(R.drawable.ic_information_outline)
                .setContentIntent(createActivityIntent(activityClass, notificationBundle))
                .setAutoCancel(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getText(descriptionResourceId)));
    }

    /**
     * Creates a pending intent that will start the specified activity when invoked.
     */
    @SuppressWarnings("rawtypes")
    public PendingIntent createActivityIntent(Class intentClass, @Nullable Bundle notificationBundle) {
        Intent contentIntent = new Intent(context, intentClass);
        if (notificationBundle != null) {
            contentIntent.putExtra(BUNDLE_KEY, notificationBundle);
        }
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Creates a pending intent that will start the specified service when invoked.
     */
    public PendingIntent createServiceIntent(@Nullable Bundle notificationBundle) {
        Intent contentIntent = new Intent(context, LucaService.class);
        if (notificationBundle != null) {
            contentIntent.putExtra(BUNDLE_KEY, notificationBundle);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            return PendingIntent.getService(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    private NotificationCompat.Action createCheckoutAction() {
        Bundle bundle = new Bundle();
        bundle.putInt(ACTION_KEY, ACTION_CHECKOUT);
        PendingIntent pendingIntent = createServiceIntent(bundle);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_close,
                context.getString(R.string.notification_action_check_out),
                pendingIntent
        ).build();
    }

    private NotificationCompat.Action createEndMeetingAction() {
        Bundle bundle = new Bundle();
        bundle.putInt(ACTION_KEY, ACTION_END_MEETING);
        PendingIntent pendingIntent = createServiceIntent(bundle);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_close,
                context.getString(R.string.notification_action_end_meeting),
                pendingIntent
        ).build();
    }

    private NotificationCompat.Action createStopServiceAction() {
        Bundle bundle = new Bundle();
        bundle.putInt(ACTION_KEY, ACTION_STOP);
        PendingIntent pendingIntent = createServiceIntent(bundle);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.notification_action_stop_service),
                pendingIntent
        ).build();
    }

    /**
     * Attempts to retrieve the bundle that has been added when using {@link
     * #createServiceIntent(Bundle)}.
     */
    public static Maybe<Bundle> getBundleFromIntent(@Nullable Intent intent) {
        return Maybe.defer(() -> {
            if (intent != null && intent.getExtras() != null) {
                Bundle extras = intent.getExtras();
                if (extras.containsKey(BUNDLE_KEY)) {
                    return Maybe.fromCallable(() -> extras.getBundle(BUNDLE_KEY));
                }
            }
            return Maybe.empty();
        });
    }

    public static Maybe<Integer> getActionFromBundle(@Nullable Bundle bundle) {
        return Maybe.defer(() -> {
            if (bundle != null && bundle.containsKey(ACTION_KEY)) {
                return Maybe.fromCallable(() -> bundle.getInt(ACTION_KEY));
            }
            return Maybe.empty();
        });
    }

}

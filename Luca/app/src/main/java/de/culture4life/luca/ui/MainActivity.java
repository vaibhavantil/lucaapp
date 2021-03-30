package de.culture4life.luca.ui;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.content.Intent;
import android.os.Bundle;

import de.culture4life.luca.R;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.ui.registration.RegistrationActivity;

import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import io.reactivex.rxjava3.core.Completable;
import timber.log.Timber;

public class MainActivity extends BaseActivity {

    private NavController navigationController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeNavigation();
        hideActionBar();
        processIntent(getIntent());
    }

    private void initializeNavigation() {
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.qrCodeFragment, R.id.historyFragment
        ).build();
        navigationController = Navigation.findNavController(this, R.id.navigationHostFragment);
        NavigationUI.setupActionBarWithNavController(this, navigationController, appBarConfiguration);
        BottomNavigationView navigationView = findViewById(R.id.bottomNavigationView);
        NavigationUI.setupWithNavController(navigationView, navigationController);
        navigationView.setSelectedItemId(R.id.qrCodeFragment);

        if (application.isInDarkMode()) {
            // workaround for removing the elevation color overlay
            // https://github.com/material-components/material-components-android/issues/1148
            navigationView.setElevation(0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processIntent(intent);
    }

    private void processIntent(@Nullable Intent intent) {
        Timber.d("processIntent() called with: intent = [%s]", intent);
        LucaNotificationManager.getBundleFromIntent(intent)
                .flatMap(LucaNotificationManager::getActionFromBundle)
                .flatMapCompletable(this::processNotificationAction)
                .subscribe(
                        () -> Timber.d("Processed intent: %s", intent),
                        throwable -> Timber.w(throwable, "Unable to process intent")
                );
    }

    private Completable processNotificationAction(int action) {
        return Completable.fromAction(() -> {
            switch (action) {
                case LucaNotificationManager.ACTION_SHOW_ACCESSED_DATA:
                    Timber.d("Showing accessed data");
                    navigationController.navigate(R.id.historyFragment);
                    break;
                default:
                    Timber.w("Unknown notification action: %d", action);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showRegistrationIfRequired();
    }

    private void showRegistrationIfRequired() {
        activityDisposable.add(application.getRegistrationManager().hasCompletedRegistration()
                .onErrorReturnItem(false)
                .subscribe(registrationCompleted -> {
                    if (!registrationCompleted) {
                        Intent intent = new Intent(this, RegistrationActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                }));
    }

}
package de.culture4life.luca.ui.splash;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import de.culture4life.luca.R;
import de.culture4life.luca.ui.BaseActivity;
import de.culture4life.luca.ui.MainActivity;
import de.culture4life.luca.ui.onboarding.OnboardingActivity;
import de.culture4life.luca.ui.registration.RegistrationActivity;

public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_App_DayNight);
        super.onCreate(savedInstanceState);
        hideActionBar();

        // check for app link data
        Intent intent = getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null) {
                application.handleDeepLink(intent.getData())
                        .blockingAwait();
            }
        }

        if (!hasSeenWelcomeScreenBefore()) {
            navigate(OnboardingActivity.class);
        } else if (!hasCompletedRegistration()) {
            navigate(RegistrationActivity.class);
        } else {
            navigate(MainActivity.class);
        }
    }

    private void navigate(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean hasSeenWelcomeScreenBefore() {
        return application.getPreferencesManager()
                .restoreOrDefault(OnboardingActivity.WELCOME_SCREEN_SEEN_KEY, false)
                .onErrorReturnItem(false)
                .blockingGet();
    }

    private boolean hasCompletedRegistration() {
        return application.getRegistrationManager()
                .hasCompletedRegistration()
                .onErrorReturnItem(false)
                .blockingGet();
    }

}
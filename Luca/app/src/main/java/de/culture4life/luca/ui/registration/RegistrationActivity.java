package de.culture4life.luca.ui.registration;

import android.content.Intent;
import android.os.Bundle;

import de.culture4life.luca.R;
import de.culture4life.luca.ui.BaseActivity;
import de.culture4life.luca.ui.MainActivity;

public class RegistrationActivity extends BaseActivity {

    public static final String REGISTRATION_COMPLETED_SCREEN_SEEN_KEY = "registration_completed_screen_seen";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);
        getSupportActionBar().hide();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentFrameLayout, new RegistrationFragment())
                .commit();
    }

    public void onEditingContactDataCompleted() {
        finish();
    }

    public void onRegistrationCompleted() {
        if (hasSeenRegistrationCompletedScreenBefore()) {
            finish();
        } else {
            showRegistrationCompletedScreen();
        }
    }

    private void showMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showRegistrationCompletedScreen() {
        activityDisposable.add(application.getPreferencesManager()
                .persist(REGISTRATION_COMPLETED_SCREEN_SEEN_KEY, true)
                .onErrorComplete()
                .subscribe());

        setContentView(R.layout.fragment_onboarding_complete);
        findViewById(R.id.primaryActionButton).setOnClickListener(v -> showMainApp());
    }

    private boolean hasSeenRegistrationCompletedScreenBefore() {
        return application.getPreferencesManager()
                .restoreOrDefault(REGISTRATION_COMPLETED_SCREEN_SEEN_KEY, false)
                .onErrorReturnItem(false)
                .blockingGet();
    }

}
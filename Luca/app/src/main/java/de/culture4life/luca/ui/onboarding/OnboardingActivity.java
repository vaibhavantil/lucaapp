package de.culture4life.luca.ui.onboarding;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.CompoundButton;
import android.widget.TextView;

import de.culture4life.luca.R;
import de.culture4life.luca.ui.BaseActivity;
import de.culture4life.luca.ui.registration.RegistrationActivity;

public class OnboardingActivity extends BaseActivity {

    public static final String WELCOME_SCREEN_SEEN_KEY = "welcome_screen_seen";

    private MaterialButton primaryActionButton;
    private MaterialCheckBox termsCheckBox;
    private MaterialCheckBox privacyCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showWelcomeScreen();
        hideActionBar();
    }

    private void showWelcomeScreen() {
        setContentView(R.layout.fragment_onboarding_welcome);

        primaryActionButton = findViewById(R.id.primaryActionButton);
        primaryActionButton.setOnClickListener(view -> {
            if (termsCheckBox.isChecked()) {
                activityDisposable.add(application.getPreferencesManager()
                        .persist(WELCOME_SCREEN_SEEN_KEY, true)
                        .onErrorComplete()
                        .subscribe(this::showInfoScreen));
            }
        });

        CompoundButton.OnCheckedChangeListener checkBoxListener = (view, isChecked) -> {
            primaryActionButton.setEnabled(termsCheckBox.isChecked() && privacyCheckBox.isChecked());
        };

        termsCheckBox = findViewById(R.id.termsCheckBox);
        termsCheckBox.setOnCheckedChangeListener(checkBoxListener);

        privacyCheckBox = findViewById(R.id.privacyCheckBox);
        privacyCheckBox.setOnCheckedChangeListener(checkBoxListener);

        TextView termsTextView = findViewById(R.id.termsTextView);
        termsTextView.setMovementMethod(LinkMovementMethod.getInstance());

        TextView privacyTextView = findViewById(R.id.privacyTextView);
        privacyTextView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void showInfoScreen() {
        setContentView(R.layout.fragment_onboarding_info);
        primaryActionButton = findViewById(R.id.primaryActionButton);
        primaryActionButton.setOnClickListener(view -> showRegistration());
    }

    private void showRegistration() {
        Intent intent = new Intent(this, RegistrationActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

}
package de.culture4life.luca.ui.registration;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import android.animation.ObjectAnimator;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.R;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.DefaultTextWatcher;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import de.culture4life.luca.util.TimeUtil;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static android.view.inputmethod.EditorInfo.IME_ACTION_DONE;

public class RegistrationFragment extends BaseFragment<RegistrationViewModel> {

    private static final long DELAY_DURATION = RegistrationViewModel.DEBOUNCE_DURATION;

    private LinearProgressIndicator progressIndicator;
    private TextView headingTextView;
    private TextView contactInfoTextView;
    private TextView addressInfoTextView;
    private RegistrationTextInputLayout firstNameLayout;
    private RegistrationTextInputLayout lastNameLayout;
    private RegistrationTextInputLayout phoneNumberLayout;
    private RegistrationTextInputLayout emailLayout;
    private RegistrationTextInputLayout streetLayout;
    private RegistrationTextInputLayout houseNumberLayout;
    private RegistrationTextInputLayout postalCodeLayout;
    private RegistrationTextInputLayout cityNameLayout;
    private MaterialButton confirmationButton;

    private Observer<Boolean> completionObserver;

    @Override
    protected int getLayoutResource() {
        return R.layout.fragment_registration_all;
    }

    @Override
    protected Class<RegistrationViewModel> getViewModelClass() {
        return RegistrationViewModel.class;
    }

    @Override
    protected Completable initializeViews() {
        return super.initializeViews()
                .andThen(Completable.fromAction(() -> {
                    initializeSharedViews();
                    initializeNameViews();
                    initializeContactViews();
                    initializeAddressViews();
                }));
    }

    private void initializeSharedViews() {
        progressIndicator = getView().findViewById(R.id.registrationProgressIndicator);
        observe(viewModel.getProgress(), this::indicateProgress);

        headingTextView = getView().findViewById(R.id.registrationHeading);
        confirmationButton = getView().findViewById(R.id.registrationActionButton);

        if (viewModel.isInEditMode()) {
            initializeSharedViewsInEditMode();
        } else {
            initializeSharedViewsInRegistrationMode();
        }

        observe(viewModel.getCompleted(), completed -> {
            if (completionObserver != null) {
                completionObserver.onChanged(completed);
            }
        });

        observe(viewModel.getIsLoading(), loading -> {
            confirmationButton.setEnabled(!loading);

            // indeterminate state can only be changed while invisible
            // see: https://github.com/material-components/material-components-android/issues/1921
            int visibility = progressIndicator.getVisibility();
            progressIndicator.setVisibility(View.GONE);
            progressIndicator.setIndeterminate(loading);
            progressIndicator.setVisibility(visibility);
        });
    }

    private void initializeSharedViewsInRegistrationMode() {
        headingTextView.setText(getString(R.string.registration_heading_name));
        if (BuildConfig.DEBUG) {
            headingTextView.setOnClickListener(v -> viewModel.useDebugRegistrationData().subscribe());
        }
        confirmationButton.setOnClickListener(v -> viewDisposable.add(Completable.fromAction(
                () -> {
                    if (isNameStepCompleted()) {
                        viewModel.updateRegistrationDataWithFormValuesAsSideEffect();
                        showContactStep();
                    }
                }).delaySubscription(DELAY_DURATION, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe()));
    }

    private void initializeSharedViewsInEditMode() {
        headingTextView.setText(getString(R.string.navigation_contact_data));
        confirmationButton.setText(R.string.action_update);

        confirmationButton.setOnClickListener(v -> viewDisposable.add(viewModel.updatePhoneNumberVerificationStatus()
                .andThen(viewModel.getPhoneNumberVerificationStatus())
                .delaySubscription(DELAY_DURATION, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> hideKeyboard())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        numberVerified -> {
                            if (!numberVerified && !shouldSkipVerification()) {
                                showCurrentPhoneNumberVerificationStep();
                            } else if (areAllStepsCompleted()) {
                                viewModel.onUserDataUpdateRequested();
                            } else {
                                showMissingInfoDialog();
                            }
                        }
                )));

        completionObserver = completed -> {
            if (completed) {
                ((RegistrationActivity) requireActivity()).onEditingContactDataCompleted();
            }
        };
    }

    private void initializeNameViews() {
        firstNameLayout = getView().findViewById(R.id.firstNameLayout);
        bindToLiveData(firstNameLayout, viewModel.getFirstName());

        lastNameLayout = getView().findViewById(R.id.lastNameLayout);
        bindToLiveData(lastNameLayout, viewModel.getLastName());

        if (!viewModel.isInEditMode()) {
            addConfirmationAction(lastNameLayout);
        } else {
            EditText editText = firstNameLayout.getEditText();
            editText.post(() -> editText.setSelection(editText.getText().length()));
        }
    }

    private void initializeContactViews() {
        contactInfoTextView = getView().findViewById(R.id.contactInfoTextView);
        contactInfoTextView.setVisibility(View.GONE);

        phoneNumberLayout = getView().findViewById(R.id.phoneNumberLayout);
        bindToLiveData(phoneNumberLayout, viewModel.getPhoneNumber());

        emailLayout = getView().findViewById(R.id.emailLayout);
        emailLayout.setRequired(false);
        bindToLiveData(emailLayout, viewModel.getEmail());

        addConfirmationAction(emailLayout);
        if (!viewModel.isInEditMode()) {
            phoneNumberLayout.setVisibility(View.GONE);
            emailLayout.setVisibility(View.GONE);
        }
    }

    private void initializeAddressViews() {
        addressInfoTextView = getView().findViewById(R.id.addressInfoTextView);
        addressInfoTextView.setVisibility(View.GONE);

        streetLayout = getView().findViewById(R.id.streetLayout);
        bindToLiveData(streetLayout, viewModel.getStreet());

        houseNumberLayout = getView().findViewById(R.id.houseNumberLayout);
        bindToLiveData(houseNumberLayout, viewModel.getHouseNumber());

        postalCodeLayout = getView().findViewById(R.id.postalCodeLayout);
        bindToLiveData(postalCodeLayout, viewModel.getPostalCode());

        cityNameLayout = getView().findViewById(R.id.cityNameLayout);
        bindToLiveData(cityNameLayout, viewModel.getCity());
        addConfirmationAction(cityNameLayout);

        if (!viewModel.isInEditMode()) {
            streetLayout.setVisibility(View.GONE);
            houseNumberLayout.setVisibility(View.GONE);
            postalCodeLayout.setVisibility(View.GONE);
            cityNameLayout.setVisibility(View.GONE);
        } else {
            streetLayout.setRequired(true);
            houseNumberLayout.setRequired(true);
            postalCodeLayout.setRequired(true);
            cityNameLayout.setRequired(true);
        }
    }

    private void addConfirmationAction(@NonNull TextInputLayout textInputLayout) {
        Objects.requireNonNull(textInputLayout.getEditText()).setImeOptions(IME_ACTION_DONE);
        Objects.requireNonNull(textInputLayout.getEditText())
                .setOnEditorActionListener((textView, actionId, event) -> confirmationButton.callOnClick());
    }

    private void bindToLiveData(RegistrationTextInputLayout textInputLayout, LiveData<String> textLiveData) {
        EditText editText = Objects.requireNonNull(textInputLayout.getEditText());
        editText.addTextChangedListener(new DefaultTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                viewModel.onFormValueChanged(textLiveData, editable.toString());
            }
        });
        editText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                textInputLayout.hideError();
            } else {
                viewDisposable.add(Completable.timer(DELAY_DURATION, TimeUnit.MILLISECONDS, Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .andThen(Completable.fromAction(() -> {
                            if (textInputLayout.isEmptyButRequired()) {
                                textInputLayout.setError(getString(R.string.registration_empty_but_required_field_error));
                            } else if (!textInputLayout.isValid() && textInputLayout.isRequired()) {
                                textInputLayout.setError(getString(R.string.registration_invalid_value_field_error));
                            } else {
                                textInputLayout.hideError();
                            }
                        })).subscribe());
            }
        });
        observe(textLiveData, value -> {
            if (!value.trim().equals(editText.getText().toString().trim())) {
                editText.setText(value);
            }
        });
        observe(viewModel.getValidationStatus(textLiveData), textInputLayout::setValid);
    }

    private void indicateProgress(double progress) {
        int percent = (int) Math.max(0, Math.min(100, progress * 100));
        ObjectAnimator.ofInt(progressIndicator, "progress", percent)
                .setDuration(250)
                .start();
    }

    private void showContactStep() {
        headingTextView.setText(getString(R.string.registration_heading_contact));

        firstNameLayout.setVisibility(View.GONE);
        lastNameLayout.setVisibility(View.GONE);

        contactInfoTextView.setVisibility(View.VISIBLE);
        phoneNumberLayout.setVisibility(View.VISIBLE);
        emailLayout.setVisibility(View.VISIBLE);
        phoneNumberLayout.requestFocus();

        confirmationButton.setOnClickListener(v -> viewDisposable.add(viewModel.updatePhoneNumberVerificationStatus()
                .andThen(viewModel.getPhoneNumberVerificationStatus())
                .flatMapCompletable(phoneNumberVerified -> Completable.fromAction(() -> {
                    if (!isContactStepCompleted()) {
                        return;
                    }
                    viewModel.updateRegistrationDataWithFormValuesAsSideEffect();
                    if (phoneNumberVerified || shouldSkipVerification()) {
                        showAddressStep();
                    } else {
                        showCurrentPhoneNumberVerificationStep();
                    }
                })).delaySubscription(DELAY_DURATION, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe()));
    }

    private void showCurrentPhoneNumberVerificationStep() {
        if (viewModel.getShouldRequestNewVerificationTan().getValue()) {
            long nextPossibleVerificationTanRequestTimestamp = viewModel.getNextPossibleTanRequestTimestamp().getValue();
            if (nextPossibleVerificationTanRequestTimestamp > System.currentTimeMillis()) {
                showPhoneNumberRequestTimeoutDialog(nextPossibleVerificationTanRequestTimestamp);
            } else {
                showPhoneNumberVerificationConfirmationDialog();
            }
        } else {
            showPhoneNumberVerificationTanDialog();
        }
    }

    private void showPhoneNumberVerificationConfirmationDialog() {
        String number = viewModel.getFormattedPhoneNumber(PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
        boolean isMobileNumber = viewModel.isMobilePhoneNumber(number);
        int messageResource = isMobileNumber ? R.string.verification_explanation_sms_description : R.string.verification_explanation_landline_description;
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(getString(R.string.verification_explanation_title))
                .setMessage(getString(messageResource, number))
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> requestPhoneNumberVerificationTan())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel()))
                .show();
    }

    private void requestPhoneNumberVerificationTan() {
        viewDisposable.add(viewModel.requestPhoneNumberVerificationTan()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnComplete(this::showPhoneNumberVerificationTanDialog)
                .subscribe(
                        () -> Timber.i("Phone number verification TAN sent"),
                        throwable -> Timber.w("Unable to request phone number verification TAN: %s", throwable.toString())
                ));
    }

    private void showPhoneNumberVerificationTanDialog() {
        ViewGroup viewGroup = getActivity().findViewById(android.R.id.content);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.tan_dialog, viewGroup, false);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setView(dialogView)
                .setTitle(R.string.verification_enter_tan_title)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // will be overwritten later on to enable dialog to stay open
                })
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> {
                    viewModel.onPhoneNumberVerificationCanceled();
                    dialog.cancel();
                });

        AlertDialog alertDialog = builder.create();
        alertDialog.setCancelable(false);
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> verifyTanDialogInput(alertDialog));

        TextInputEditText tanEditText = dialogView.findViewById(R.id.tanInputEditText);
        tanEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                verifyTanDialogInput(alertDialog);
                return true;
            }
            return false;
        });

        dialogView.findViewById(R.id.infoImageView).setOnClickListener(v -> showTanNotReceivedDialog());
        dialogView.findViewById(R.id.infoTextView).setOnClickListener(v -> showTanNotReceivedDialog());
    }

    private void showMissingInfoDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.registration_missing_info)
                .setMessage(R.string.registration_missing_fields_error_text)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // do nothing
                });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void verifyTanDialogInput(@NonNull AlertDialog alertDialog) {
        hideKeyboard();
        completionObserver = completed -> {
            if (completed) {
                ((RegistrationActivity) requireActivity()).onRegistrationCompleted();
            }
        };

        TextInputLayout tanInputLayout = alertDialog.findViewById(R.id.tanInputLayout);
        String verificationTan = tanInputLayout.getEditText().getText().toString();

        if (verificationTan.length() != 6) {
            tanInputLayout.setError(getString(R.string.verification_enter_tan_error));
            return;
        }

        viewDisposable.add(viewModel.verifyTan(verificationTan)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            Timber.i("Phone number verified");
                            alertDialog.dismiss();
                            if (viewModel.isInEditMode()) {
                                hideKeyboard();
                                viewModel.onUserDataUpdateRequested();
                            } else {
                                showAddressStep();
                            }
                        },
                        throwable -> {
                            Timber.w("Phone number verification failed: %s", throwable.toString());
                            boolean isForbidden = NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_FORBIDDEN);
                            boolean isBadRequest = NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_BAD_REQUEST);
                            if (isForbidden || isBadRequest) {
                                // TAN was incorrect
                                tanInputLayout.setError(getString(R.string.verification_enter_tan_error));
                            }
                        }
                ));
    }

    private void showTanNotReceivedDialog() {
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.verification_tan_delayed_title)
                .setMessage(R.string.verification_tan_delayed_description)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> dialog.cancel()))
                .show();
    }

    private void showPhoneNumberRequestTimeoutDialog(long nextPossibleTanRequestTimestamp) {
        long duration = nextPossibleTanRequestTimestamp - System.currentTimeMillis();
        String readableDuration = TimeUtil.getReadableApproximateDuration(duration, getContext()).blockingGet();
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.verification_timeout_error_title)
                .setMessage(getString(R.string.verification_timeout_error_description, readableDuration))
                .setPositiveButton(R.string.action_ok, (dialog, which) -> dialog.cancel())
                .setNeutralButton(R.string.verification_timeout_action_use_last, (dialog, which) -> {
                    showPhoneNumberVerificationTanDialog();
                }))
                .show();
    }

    private void showAddressStep() {
        headingTextView.setText(getString(R.string.registration_heading_address));

        phoneNumberLayout.setVisibility(View.GONE);
        emailLayout.setVisibility(View.GONE);
        contactInfoTextView.setVisibility(View.GONE);

        addressInfoTextView.setVisibility(View.VISIBLE);
        streetLayout.setVisibility(View.VISIBLE);
        houseNumberLayout.setVisibility(View.VISIBLE);
        postalCodeLayout.setVisibility(View.VISIBLE);
        cityNameLayout.setVisibility(View.VISIBLE);
        streetLayout.requestFocus();

        confirmationButton.setText(getString(R.string.action_finish));
        confirmationButton.setOnClickListener(view -> viewDisposable.add(Completable.fromAction(
                () -> {
                    if (!isAddressStepCompleted()) {
                        return;
                    }
                    viewModel.updateRegistrationDataWithFormValuesAsSideEffect();
                    completionObserver = completed -> {
                        if (completed) {
                            ((RegistrationActivity) requireActivity()).onRegistrationCompleted();
                        }
                    };
                    viewModel.onRegistrationRequested();
                }).delaySubscription(DELAY_DURATION, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .subscribe()));
    }

    private boolean isNameStepCompleted() {
        return areFieldsValidOrEmptyAndNotRequired(Arrays.asList(firstNameLayout, lastNameLayout));
    }

    private boolean isContactStepCompleted() {
        return areFieldsValidOrEmptyAndNotRequired(Collections.singletonList(phoneNumberLayout));
    }

    private boolean isAddressStepCompleted() {
        return areFieldsValidOrEmptyAndNotRequired(Arrays.asList(streetLayout, houseNumberLayout, postalCodeLayout, cityNameLayout));
    }

    private boolean areAllStepsCompleted() {
        return isNameStepCompleted() && isContactStepCompleted() && isAddressStepCompleted();
    }

    private boolean areFieldsValidOrEmptyAndNotRequired(List<RegistrationTextInputLayout> fields) {
        boolean completed = true;
        for (RegistrationTextInputLayout textLayout : fields) {
            if (textLayout.isValidOrEmptyAndNotRequired()) {
                continue;
            }
            if (completed) {
                completed = false;
                textLayout.requestFocus();
            }
            if (textLayout.isEmptyButRequired()) {
                textLayout.setError(getString(R.string.registration_empty_but_required_field_error));
            } else if (!textLayout.isValid()) {
                textLayout.setError(getString(R.string.registration_invalid_value_field_error));
            }
        }
        return completed;
    }

    private boolean shouldSkipVerification() {
        return viewModel.isUsingTestingCredentials() || RegistrationViewModel.SKIP_PHONE_NUMBER_VERIFICATION;
    }

}

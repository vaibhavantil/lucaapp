package de.culture4life.luca.ui.venue.details;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.ncorti.slidetoact.SlideToActView;
import com.tbruyelle.rxpermissions3.Permission;

import de.culture4life.luca.R;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Completable;
import timber.log.Timber;

public class VenueDetailsFragment extends BaseFragment<VenueDetailsViewModel> {

    private static final int REQUEST_ENABLE_LOCATION_SERVICES = 2;

    private TextView headingTextView;
    private TextView descriptionTextView;
    private TextView checkInDurationHeadingTextView;
    private TextView checkInDurationTextView;
    private ImageView automaticCheckOutInfoImageView;
    private SwitchMaterial automaticCheckoutSwitch;
    private SlideToActView slideToActView;
    private Completable handleGrantedLocationAccess;
    private Completable handleDeniedLocationAccess;

    @Override
    protected int getLayoutResource() {
        return R.layout.fragment_venue_details;
    }

    @Override
    protected Class<VenueDetailsViewModel> getViewModelClass() {
        return VenueDetailsViewModel.class;
    }

    @Override
    protected Completable initializeViews() {
        return super.initializeViews()
                .andThen(Completable.fromAction(() -> {
                    headingTextView = getView().findViewById(R.id.headingTextView);
                    observe(viewModel.getTitle(), value -> headingTextView.setText(value));

                    descriptionTextView = getView().findViewById(R.id.subHeadingTextView);
                    observe(viewModel.getDescription(), value -> descriptionTextView.setText(value));

                    checkInDurationHeadingTextView = getView().findViewById(R.id.checkInDurationHeadingTextView);

                    checkInDurationTextView = getView().findViewById(R.id.checkInDurationTextView);
                    observe(viewModel.getCheckInDuration(), value -> checkInDurationTextView.setText(value));

                    initializeAutomaticCheckoutViews();
                    initializeSlideToActView();
                }));
    }

    private void initializeAutomaticCheckoutViews() {
        automaticCheckOutInfoImageView = getView().findViewById(R.id.automaticCheckoutInfoImageView);
        automaticCheckOutInfoImageView.setOnClickListener(view -> showAutomaticCheckOutInfoDialog());

        automaticCheckoutSwitch = getView().findViewById(R.id.automaticCheckoutToggle);

        observe(viewModel.getHasLocationRestriction(), hasLocation -> {
            getView().findViewById(R.id.automaticCheckOutTextView).setVisibility(hasLocation ? View.VISIBLE : View.GONE);
            getView().findViewById(R.id.automaticCheckoutInfoImageView).setVisibility(hasLocation ? View.VISIBLE : View.GONE);
            automaticCheckoutSwitch.setVisibility(hasLocation ? View.VISIBLE : View.GONE);
        });

        automaticCheckoutSwitch.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (automaticCheckoutSwitch.isEnabled() && isChecked) {
                Boolean shouldShowLocationAccessDialog = viewModel.getShouldShowLocationAccessDialog().getValue();
                if (shouldShowLocationAccessDialog == null || shouldShowLocationAccessDialog) {
                    showGrantLocationAccessDialog();
                } else {
                    viewModel.enableAutomaticCheckout();
                }
            } else {
                viewModel.disableAutomaticCheckout();
            }
        });

        observe(viewModel.getShouldEnableAutomaticCheckOut(), isActive -> automaticCheckoutSwitch.setChecked(isActive));

        observe(viewModel.getShouldEnableLocationServices(), shouldEnable -> {
            if (shouldEnable && !viewModel.isLocationServiceEnabled()) {
                handleGrantedLocationAccess = Completable.fromAction(() -> {
                    automaticCheckoutSwitch.setEnabled(false);
                    automaticCheckoutSwitch.setChecked(true);
                    automaticCheckoutSwitch.setEnabled(true);
                    viewModel.enableAutomaticCheckout();
                });
                handleDeniedLocationAccess = Completable.fromAction(() -> automaticCheckoutSwitch.setChecked(false));
                showLocationServicesDisabledDialog();
            }
        });
    }

    private void initializeSlideToActView() {
        slideToActView = getView().findViewById(R.id.slideToActView);
        slideToActView.setOnSlideCompleteListener(view -> viewModel.onSlideCompleted());

        observe(viewModel.getIsCheckedIn(), isCheckedIn -> {
            slideToActView.setReversed(isCheckedIn);
            slideToActView.setText(getString(isCheckedIn ? R.string.venue_check_out_action : R.string.venue_check_in_action));
            checkInDurationHeadingTextView.setVisibility(isCheckedIn ? View.VISIBLE : View.GONE);
            checkInDurationTextView.setVisibility(isCheckedIn ? View.VISIBLE : View.GONE);
            if (!isCheckedIn) {
                navigationController.navigate(R.id.action_venueDetailFragment_to_qrCodeFragment);
            }
        });

        observe(viewModel.getIsLoading(), loading -> {
            if (!loading) {
                slideToActView.resetSlider();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ENABLE_LOCATION_SERVICES) {
            return;
        }
        viewDisposable.add(Completable.defer(() -> {
            if (viewModel.isLocationServiceEnabled()) {
                Timber.i("Successfully enabled location services");
                return handleGrantedLocationAccess;
            } else {
                Timber.i("Failed to enable location services");
                return handleDeniedLocationAccess;
            }
        })
                .doOnError(throwable -> Timber.e("Unable to handle location service change: %s", throwable.toString()))
                .onErrorComplete()
                .doFinally(this::clearRequestResultActions)
                .subscribe());
    }

    @SuppressLint("NewApi")
    @Override
    protected void onPermissionResult(Permission permission) {
        super.onPermissionResult(permission);
        boolean isLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION.equals(permission.name);
        boolean isBackgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION.equals(permission.name);
        if (permission.granted || !(isLocationPermission || isBackgroundLocationPermission)) {
            return;
        }
        if (permission.shouldShowRequestPermissionRationale) {
            showRequestLocationPermissionRationale(permission, false);
        } else {
            showLocationPermissionPermanentlyDeniedError(permission);
        }
    }

    private void showRequestLocationPermissionRationale(@NonNull Permission permission, boolean permanentlyDenied) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> viewModel.onEnablingAutomaticCheckOutFailed())
                .setOnCancelListener(dialogInterface -> viewModel.onEnablingAutomaticCheckOutFailed())
                .setOnDismissListener(dialogInterface -> viewModel.onEnablingAutomaticCheckOutFailed());

        if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission.name) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            builder.setTitle(R.string.auto_checkout_location_access_title);
            builder.setMessage(R.string.auto_checkout_location_access_description);
        } else {
            builder.setTitle(R.string.auto_checkout_background_location_access_title);
            builder.setMessage(getString(R.string.auto_checkout_background_location_access_description, application.getPackageManager().getBackgroundPermissionOptionLabel()));
        }

        if (permanentlyDenied) {
            builder.setPositiveButton(R.string.action_settings, (dialog, which) -> application.openAppSettings());
        } else {
            builder.setPositiveButton(R.string.action_grant, (dialog, which) -> {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission.name)) {
                    viewModel.requestLocationPermissionForAutomaticCheckOut();
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    viewModel.requestBackgroundLocationPermissionForAutomaticCheckOut();
                }
            });
        }

        new BaseDialogFragment(builder).show();
    }

    private void showAutomaticCheckOutInfoDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.auto_checkout_info_title)
                .setMessage(R.string.auto_checkout_info_description)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> dialog.cancel());
        new BaseDialogFragment(builder).show();
    }

    private void showGrantLocationAccessDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.auto_checkout_location_access_title)
                .setMessage(R.string.auto_checkout_location_access_description)
                .setPositiveButton(R.string.action_enable, (dialog, which) -> viewModel.enableAutomaticCheckout())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> {
                    automaticCheckoutSwitch.setChecked(false);
                    dialog.cancel();
                });
        new BaseDialogFragment(builder).show();
    }

    private void showLocationServicesDisabledDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.auto_checkout_enable_location_title)
                .setMessage(R.string.auto_checkout_enable_location_description)
                .setPositiveButton(R.string.action_settings, (dialog, which) -> requestLocationServiceActivation())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> handleDeniedLocationAccess.onErrorComplete()
                        .doFinally(this::clearRequestResultActions)
                        .subscribe());
        new BaseDialogFragment(builder).show();
    }

    private void requestLocationServiceActivation() {
        Timber.d("Requesting to enable location services");
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivityForResult(intent, REQUEST_ENABLE_LOCATION_SERVICES);
    }

    private void clearRequestResultActions() {
        handleGrantedLocationAccess = null;
        handleDeniedLocationAccess = null;
    }

    private void showLocationPermissionPermanentlyDeniedError(@NonNull Permission permission) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        ViewError viewError = new ViewError.Builder(context)
                .withTitle(getString(R.string.missing_permission_arg, getString(R.string.permission_name_location)))
                .withDescription(getString(R.string.missing_permission_arg, getString(R.string.permission_name_location)))
                .withResolveLabel(getString(R.string.action_resolve))
                .withResolveAction(Completable.fromAction(() -> showRequestLocationPermissionRationale(permission, true)))
                .build();

        showErrorAsSnackbar(viewError);
    }

}

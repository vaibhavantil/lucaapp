package de.culture4life.luca.ui.qrcode;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.R;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import de.culture4life.luca.ui.registration.RegistrationActivity;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import timber.log.Timber;

public class QrCodeFragment extends BaseFragment<QrCodeViewModel> {

    private ImageView qrCodeImageView;
    private PreviewView cameraPreviewView;
    private View loadingView;
    private TextView qrCodeCaptionTextView;

    private TextView nameTextView;
    private TextView addressTextView;
    private TextView phoneNumberTextView;

    private MaterialButton cameraToggleButton;
    private MaterialButton createMeetingButton;

    private ProcessCameraProvider cameraProvider;

    private Disposable cameraPreviewDisposable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        qrCodeImageView = view.findViewById(R.id.qrCodeImageView);
        cameraPreviewView = view.findViewById(R.id.cameraPreviewView);
        loadingView = view.findViewById(R.id.loadingLayout);

        qrCodeCaptionTextView = view.findViewById(R.id.qrCodeCaptionTextView);

        nameTextView = view.findViewById(R.id.nameTextView);
        addressTextView = view.findViewById(R.id.descriptionTextView);
        phoneNumberTextView = view.findViewById(R.id.phoneNumberTextView);

        cameraToggleButton = view.findViewById(R.id.cameraToggleButton);
        createMeetingButton = view.findViewById(R.id.createMeetingButton);

        return view;
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.fragment_qr_code;
    }

    @Override
    protected Class<QrCodeViewModel> getViewModelClass() {
        return QrCodeViewModel.class;
    }

    @Override
    protected Completable initializeViews() {
        return super.initializeViews()
                .andThen(Completable.fromAction(() -> {

                    if (BuildConfig.DEBUG) {
                        // simulate check in when clicking on the QR code in debug builds
                        qrCodeImageView.setOnClickListener(v -> viewModel.onDebuggingCheckInRequested());
                    }

                    cameraToggleButton.setOnClickListener(v -> toggleCameraPreview());
                    createMeetingButton.setOnClickListener(v -> showCreatePrivateMeetingDialog());

                    observe(viewModel.getQrCode(), value -> qrCodeImageView.setImageBitmap(value));

                    observe(viewModel.getName(), value -> nameTextView.setText(value));
                    observe(viewModel.getAddress(), value -> addressTextView.setText(value));
                    observe(viewModel.getPhoneNumber(), value -> phoneNumberTextView.setText(value));

                    observe(viewModel.isNetworkAvailable(), value -> {
                        if (value) {
                            qrCodeCaptionTextView.setVisibility(View.GONE);
                        } else {
                            qrCodeCaptionTextView.setVisibility(View.VISIBLE);
                            qrCodeCaptionTextView.setText(R.string.error_no_network_connection);
                        }
                    });

                    observe(viewModel.getIsLoading(), loading -> loadingView.setVisibility(loading ? View.VISIBLE : View.GONE));

                    observe(viewModel.isContactDataMissing(), contactDataMissing -> {
                        if (contactDataMissing) {
                            showContactDataDialog();
                        }
                    });

                    observe(viewModel.isUpdateRequired(), updateRequired -> {
                        if (updateRequired) {
                            showUpdateDialog();
                        }
                    });

                    observe(viewModel.getPrivateMeetingUrl(), privateMeetingUrl -> {
                        if (privateMeetingUrl != null) {
                            showJoinPrivateMeetingDialog(privateMeetingUrl);
                        }
                    });
                }));
    }

    @Override
    public void onResume() {
        super.onResume();
        hideKeyboard();
        viewModel.checkIfContactDataMissing();
        viewModel.checkIfUpdateIsRequired();
        viewModel.checkIfHostingMeeting();
    }

    private void showContactDataDialog() {
        BaseDialogFragment dialogFragment = new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.registration_missing_info)
                .setMessage(R.string.registration_address_mandatory)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    Intent intent = new Intent(application, RegistrationActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    application.startActivity(intent);
                }));
        dialogFragment.setCancelable(false);
        dialogFragment.show();
    }

    private void showUpdateDialog() {
        BaseDialogFragment dialogFragment = new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.update_required_title)
                .setMessage(R.string.update_required_description)
                .setPositiveButton(R.string.action_update, (dialog, which) -> {
                    try {
                        application.openUrl("market://details?id=" + BuildConfig.APPLICATION_ID.replace(".debug", ""));
                    } catch (android.content.ActivityNotFoundException e) {
                        application.openUrl("https://luca-app.de");
                    }
                }));
        dialogFragment.setCancelable(false);
        dialogFragment.show();
    }

    private void showJoinPrivateMeetingDialog(@NonNull String privateMeetingUrl) {
        BaseDialogFragment dialogFragment = new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.meeting_join_heading)
                .setMessage(R.string.meeting_join_description)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> viewModel.onPrivateMeetingJoinApproved(privateMeetingUrl))
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> viewModel.onPrivateMeetingJoinDismissed(privateMeetingUrl)));
        dialogFragment.setCancelable(false);
        dialogFragment.show();
    }

    private void showCreatePrivateMeetingDialog() {
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.meeting_create_modal_heading)
                .setMessage(R.string.meeting_create_modal_description)
                .setPositiveButton(R.string.meeting_create_modal_action, (dialog, which) -> viewModel.onPrivateMeetingCreationRequested())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.cancel()))
                .show();
    }

    private void toggleCameraPreview() {
        if (cameraPreviewDisposable == null) {
            showCameraPreview();
        } else {
            hideCameraPreview();
        }
    }

    private void showCameraPreview() {
        cameraPreviewDisposable = getCameraPermission()
                .doOnComplete(() -> {
                    cameraPreviewView.setVisibility(View.VISIBLE);
                    qrCodeImageView.setVisibility(View.GONE);
                    cameraToggleButton.setText(R.string.show_qr_code);
                })
                .andThen(startCameraPreview())
                .doOnError(throwable -> Timber.w("Unable to show camera preview: %s", throwable.toString()))
                .doFinally(this::hideCameraPreview)
                .onErrorComplete()
                .subscribe();

        viewDisposable.add(cameraPreviewDisposable);
    }

    private void hideCameraPreview() {
        if (cameraPreviewDisposable != null) {
            cameraPreviewDisposable.dispose();
            cameraPreviewDisposable = null;
        }
        cameraPreviewView.setVisibility(View.GONE);
        qrCodeImageView.setVisibility(View.VISIBLE);
        cameraToggleButton.setText(R.string.scan_qr_code);
    }

    private Completable getCameraPermission() {
        return rxPermissions.request(Manifest.permission.CAMERA)
                .flatMapCompletable(granted -> {
                    if (granted) {
                        return Completable.complete();
                    } else {
                        return Completable.error(new IllegalStateException("Camera permission missing"));
                    }
                });
    }

    public Completable startCameraPreview() {
        return Maybe.fromCallable(() -> cameraProvider)
                .switchIfEmpty(Single.create(emitter -> {
                    ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
                    cameraProviderFuture.addListener(() -> {
                        try {
                            cameraProvider = cameraProviderFuture.get();
                            emitter.onSuccess(cameraProvider);
                        } catch (ExecutionException | InterruptedException e) {
                            emitter.onError(e);
                        }
                    }, ContextCompat.getMainExecutor(getContext()));
                }))
                .flatMapCompletable(cameraProvider -> Completable.create(emitter -> {
                    bindCameraPreview(cameraProvider);
                    emitter.setCancellable(this::unbindCameraPreview);
                }));
    }

    private void bindCameraPreview(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        Preview preview = new Preview.Builder().build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(2048, 2048))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), viewModel);

        preview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());
        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) getContext(), cameraSelector, imageAnalysis, preview);
    }

    private void unbindCameraPreview() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
    }

}

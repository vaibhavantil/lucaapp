package de.culture4life.luca.ui;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.tbruyelle.rxpermissions3.Permission;
import com.tbruyelle.rxpermissions3.RxPermissions;

import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.R;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;
import de.culture4life.luca.ui.registration.RegistrationActivity;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public abstract class BaseFragment<ViewModelType extends BaseViewModel> extends Fragment {

    protected LucaApplication application;

    protected BaseActivity baseActivity;

    protected ViewModelType viewModel;

    protected CompositeDisposable viewDisposable;

    protected RxPermissions rxPermissions;

    protected NavController navigationController;

    @Nullable
    protected Snackbar errorSnackbar;

    @Nullable
    protected ImageView menuImageView;

    protected boolean initialized;

    @NonNull
    @CallSuper
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewDisposable = new CompositeDisposable();
        return inflater.inflate(getLayoutResource(), container, false);
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        baseActivity = (BaseActivity) getActivity();
        application = (LucaApplication) baseActivity.getApplication();
        rxPermissions = new RxPermissions(this);
        try {
            navigationController = Navigation.findNavController(getView());
        } catch (Exception e) {
            Timber.w("No navigation controller available");
        }
        // TODO: 08.01.21 java.lang.IllegalStateException: Cannot invoke observe on a background thread
        //  happened on emulator twice
        initializeViewModel()
                .observeOn(AndroidSchedulers.mainThread())
                .andThen(initializeViews())
                .doOnComplete(() -> this.initialized = true)
                .subscribe(
                        () -> Timber.d("Initialized %s with %s", this, viewModel),
                        throwable -> Timber.e("Unable to initialize %s with %s: %s", this, viewModel, throwable.toString())
                );
    }

    @CallSuper
    @Override
    public void onStart() {
        super.onStart();
        observeErrors();
        observeRequiredPermissions();
        viewDisposable = new CompositeDisposable();
        viewDisposable.add(waitUntilInitializationCompleted()
                .andThen(viewModel.keepDataUpdated())
                .doOnSubscribe(disposable -> Timber.d("Keeping data updated for %s", this))
                .doOnError(throwable -> Timber.w(throwable, "Unable to keep data updated for %s", this))
                .retryWhen(errors -> errors.delay(1, TimeUnit.SECONDS))
                .doFinally(() -> Timber.d("Stopping to keep data updated for %s", this))
                .subscribe());
    }

    @CallSuper
    @Override
    public void onStop() {
        viewDisposable.dispose();
        if (errorSnackbar != null && errorSnackbar.isShown()) {
            errorSnackbar.dismiss();
        }
        super.onStop();
    }

    @LayoutRes
    protected abstract int getLayoutResource();

    protected abstract Class<ViewModelType> getViewModelClass();

    private Completable waitUntilInitializationCompleted() {
        return Observable.interval(0, 50, TimeUnit.MILLISECONDS)
                .filter(tick -> initialized)
                .firstOrError()
                .ignoreElement();
    }

    @CallSuper
    protected Completable initializeViewModel() {
        return Single.fromCallable(() -> new ViewModelProvider(getActivity()).get(getViewModelClass()))
                .doOnSuccess(createdViewModel -> {
                    viewModel = createdViewModel;
                    viewModel.setNavigationController(navigationController);
                })
                .flatMapCompletable(BaseViewModel::initialize);
    }

    @CallSuper
    protected Completable initializeViews() {
        return setupMenu();
    }

    protected Completable setupMenu() {
        return Completable.fromAction(() -> {
            menuImageView = getView().findViewById(R.id.menuImageView);
            if (menuImageView == null) {
                return;
            }
            menuImageView.setOnClickListener(view -> {
                PopupMenu popupMenu = new PopupMenu(getContext(), menuImageView);
                popupMenu.getMenuInflater().inflate(R.menu.main_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(this::onMenuItemClick);
                popupMenu.show();
            });
        });
    }

    @SuppressWarnings("SameReturnValue")
    protected boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.contactDataMenuItem: {
                Intent intent = new Intent(getActivity(), RegistrationActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            }
            case R.id.clearHistoryMenuItem: {
                new BaseDialogFragment(new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.history_clear_title)
                        .setMessage(R.string.history_clear_description)
                        .setPositiveButton(R.string.history_clear_action, (dialog, which) -> {
                            application.getHistoryManager().clearItems()
                                    .subscribeOn(Schedulers.io())
                                    .subscribe(
                                            () -> Timber.i("History cleared"),
                                            throwable -> Timber.w("Unable to clear history: %s", throwable.toString())
                                    );
                        })
                        .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.dismiss()))
                        .show();
                break;
            }
            case R.id.privacyPolicyMenuItem: {
                application.openUrl(getString(R.string.url_privacy_policy));
                break;
            }
            case R.id.termsAndConditionsMenuItem: {
                application.openUrl(getString(R.string.url_terms_and_conditions));
                break;
            }
            case R.id.imprintMenuItem: {
                application.openUrl(getString(R.string.url_imprint));
                break;
            }
            case R.id.appDataMenuItem: {
                application.openAppSettings();
                break;
            }
            default: {
                Timber.w("Unknown menu item selected: %s", item.getTitle());
                return false;
            }
        }
        return true;
    }

    protected <ValueType> void observe(@NonNull LiveData<ValueType> liveData, @NonNull Observer<ValueType> observer) {
        liveData.observe(getViewLifecycleOwner(), observer);
    }

    protected void observeRequiredPermissions() {
        observe(viewModel.getRequiredPermissionsViewEvent(), permissionsViewEvent -> {
            Set<String> permissions = permissionsViewEvent.getValue();
            if (permissionsViewEvent.hasBeenHandled() || permissions.isEmpty()) {
                return;
            }
            permissionsViewEvent.setHandled(true);

            String[] keys = new String[permissions.size()];
            permissions.toArray(keys);

            rxPermissions.requestEach(keys)
                    .doOnSubscribe(disposable -> Timber.d("Requesting required permissions: %s", permissions))
                    .doOnError(throwable -> Timber.e(throwable, "Unable to request permissions: %s", permissions))
                    .onErrorComplete()
                    .subscribe(this::onPermissionResult);
        });
    }

    protected void onPermissionResult(Permission permission) {
        Timber.v("Permission result: %s", permission);
        viewModel.onPermissionResult(permission);
    }

    protected void observeErrors() {
        observe(viewModel.getErrors(), errors -> {
            if (errors.isEmpty()) {
                indicateNoErrors();
            } else {
                indicateErrors(errors);
            }
        });
    }

    protected void indicateNoErrors() {
        Timber.d("indicateNoErrors() called");
        if (errorSnackbar != null) {
            errorSnackbar.dismiss();
        }
    }

    protected void indicateErrors(@NonNull Set<ViewError> errors) {
        Timber.d("indicateErrors() called with: errors = [%s]", errors);
        for (ViewError error : errors) {
            showErrorAsDialog(error);
        }
    }

    protected void showErrorAsToast(@NonNull ViewError error) {
        if (getContext() == null) {
            return;
        }
        Toast.makeText(getContext(), error.getTitle(), Toast.LENGTH_LONG).show();
        viewModel.onErrorShown(error);
        viewModel.onErrorDismissed(error);
    }

    protected void showErrorAsSnackbar(@NonNull ViewError error) {
        if (getView() == null) {
            return;
        }
        if (errorSnackbar != null) {
            errorSnackbar.dismiss();
        }
        int duration = error.isResolvable() ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG;
        errorSnackbar = Snackbar.make(getView(), error.getTitle(), duration);
        errorSnackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onShown(Snackbar sb) {
                viewModel.onErrorShown(error);
            }

            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                viewModel.onErrorDismissed(error);
            }
        });

        if (error.isResolvable()) {
            errorSnackbar.setAction(error.getResolveLabel(), action -> viewDisposable.add(error.getResolveAction()
                    .subscribe(
                            () -> Timber.d("Error resolved"),
                            throwable -> Timber.w("Unable to resolve error: %s", throwable.toString())
                    )));
        }
        errorSnackbar.show();
    }

    protected void showErrorAsDialog(@NonNull ViewError error) {
        if (getView() == null) {
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity())
                .setTitle(error.getTitle())
                .setMessage(error.getDescription());

        if (error.isResolvable()) {
            builder.setPositiveButton(error.getResolveLabel(), (dialog, which) -> viewDisposable.add(error.getResolveAction()
                    .subscribe(
                            () -> Timber.d("Error resolved"),
                            throwable -> Timber.w("Unable to resolve error: %s", throwable.toString())
                    )));
        } else {
            builder.setPositiveButton(R.string.action_ok, (dialog, which) -> {
                // do nothing
            });
        }

        BaseDialogFragment dialogFragment = new BaseDialogFragment(builder);
        dialogFragment.setOnDismissListener(dialog -> viewModel.onErrorDismissed(error));
        dialogFragment.show();

        viewModel.onErrorShown(error);
    }

    protected void hideKeyboard() {
        View view = getView();
        Context context = getContext();
        if (view == null && context == null) {
            Timber.w("Unable to hide keyboard, view or context not available");
            return;
        }
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getRootView().getWindowToken(), 0);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}
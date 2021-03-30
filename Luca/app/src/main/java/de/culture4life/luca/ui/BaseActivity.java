package de.culture4life.luca.ui;

import android.os.Bundle;

import de.culture4life.luca.LucaApplication;

import androidx.annotation.CallSuper;
import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public abstract class BaseActivity extends AppCompatActivity {

    protected LucaApplication application;

    protected final CompositeDisposable activityDisposable = new CompositeDisposable();

    @CallSuper
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        application = (LucaApplication) getApplication();
    }

    @Override
    protected void onStart() {
        super.onStart();
        application.onActivityStarted(this);
    }

    @Override
    protected void onStop() {
        application.onActivityStopped(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        activityDisposable.dispose();
        super.onDestroy();
    }

    public void showActionBar() {
        getSupportActionBar().show();
    }

    public void hideActionBar() {
        getSupportActionBar().hide();
    }

}

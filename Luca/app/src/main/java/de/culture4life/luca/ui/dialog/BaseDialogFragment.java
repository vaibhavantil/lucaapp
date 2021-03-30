package de.culture4life.luca.ui.dialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class BaseDialogFragment extends DialogFragment {

    private final AlertDialog.Builder builder;

    @Nullable
    private DialogInterface.OnDismissListener onDismissListener;

    public BaseDialogFragment(MaterialAlertDialogBuilder builder) {
        this.builder = builder;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return builder.create();
    }

    public void show() {
        FragmentManager fragmentManager = getFragmentManager(builder.getContext());
        if (fragmentManager != null) {
            super.show(fragmentManager, null);
        } else {
            throw new IllegalStateException("No fragment manager available");
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        forwardDismissEvent(dialog);
        super.onDismiss(dialog);
    }

    private void forwardDismissEvent(@NonNull DialogInterface dialog) {
        if (onDismissListener != null) {
            onDismissListener.onDismiss(dialog);
        }
    }

    @Nullable
    private static FragmentManager getFragmentManager(@NonNull Context context) {
        Activity activity = getActivity(context);
        if (activity instanceof FragmentActivity) {
            return ((FragmentActivity) activity).getSupportFragmentManager();
        } else {
            return null;
        }
    }

    @Nullable
    private static Activity getActivity(Context context) {
        if (context == null) {
            return null;
        } else if (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            } else {
                return getActivity(((ContextWrapper) context).getBaseContext());
            }
        }
        return null;
    }

    @Nullable
    public DialogInterface.OnDismissListener getOnDismissListener() {
        return onDismissListener;
    }

    public void setOnDismissListener(@Nullable DialogInterface.OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

}

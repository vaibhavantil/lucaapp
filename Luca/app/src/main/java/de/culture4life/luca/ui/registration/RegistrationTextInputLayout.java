package de.culture4life.luca.ui.registration;

import com.google.android.material.textfield.TextInputLayout;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RegistrationTextInputLayout extends TextInputLayout {

    private boolean valid = false;
    private boolean required = true;

    public RegistrationTextInputLayout(@NonNull Context context) {
        super(context);
    }

    public RegistrationTextInputLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RegistrationTextInputLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void hideError() {
        setError(null);
    }

    public boolean isValidOrEmptyAndNotRequired() {
        return valid || isEmptyAndNotRequired();
    }

    public boolean isEmptyButRequired() {
        return required && getEditText().getText().toString().isEmpty();
    }

    public boolean isEmptyAndNotRequired() {
        return !required && getEditText().getText().toString().isEmpty();
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
        if (valid) {
            hideError();
        }
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

}

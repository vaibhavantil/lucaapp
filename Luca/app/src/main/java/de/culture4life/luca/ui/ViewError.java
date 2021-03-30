package de.culture4life.luca.ui;

import android.content.Context;

import de.culture4life.luca.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import hu.akarnokd.rxjava3.debug.RxJavaAssemblyException;
import io.reactivex.rxjava3.core.Completable;

public class ViewError {

    @NonNull
    private String title;

    @NonNull
    private String description;

    @Nullable
    private Throwable cause;

    @Nullable
    private String resolveLabel;

    @Nullable
    private Completable resolveAction;

    private boolean removeWhenShown;

    private boolean canBeShownAsNotification;

    private boolean isExpected;

    public ViewError(@NonNull String title, @NonNull String description) {
        this.title = title;
        this.description = description;
        this.removeWhenShown = true;
    }

    public boolean isResolvable() {
        return resolveAction != null;
    }

    public Completable resolve() {
        return Completable.defer(() -> {
            if (isResolvable()) {
                return resolveAction;
            } else {
                return Completable.error(new IllegalStateException("Error is not resolvable"));
            }
        });
    }

    @Override
    public String toString() {
        return "ViewError{" +
                "cause=" + cause +
                ", resolveLabel='" + resolveLabel + '\'' +
                '}';
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    public void setDescription(@NonNull String description) {
        this.description = description;
    }

    @Nullable
    public Throwable getCause() {
        return cause;
    }

    public void setCause(@Nullable Throwable cause) {
        this.cause = cause;
    }

    @Nullable
    public String getResolveLabel() {
        return resolveLabel;
    }

    public void setResolveLabel(@Nullable String resolveLabel) {
        this.resolveLabel = resolveLabel;
    }

    @Nullable
    public Completable getResolveAction() {
        return resolveAction;
    }

    public void setResolveAction(@Nullable Completable resolveAction) {
        this.resolveAction = resolveAction;
    }

    public boolean getRemoveWhenShown() {
        return removeWhenShown;
    }

    public void setRemoveWhenShown(boolean removeWhenShown) {
        this.removeWhenShown = removeWhenShown;
    }

    public boolean canBeShownAsNotification() {
        return canBeShownAsNotification;
    }

    public void setCanBeShownAsNotification(boolean canBeShownAsNotification) {
        this.canBeShownAsNotification = canBeShownAsNotification;
    }

    public boolean isExpected() {
        return isExpected;
    }

    public void setExpected(boolean expected) {
        isExpected = expected;
    }

    private static boolean isGenericException(@NonNull Throwable throwable) {
        return throwable instanceof NullPointerException;
    }

    private static String createTitle(@NonNull Throwable throwable, @NonNull Context context) {
        if (isGenericException(throwable)) {
            return context.getString(R.string.error_generic_title);
        } else {
            String type = throwable.getClass().getSimpleName();
            return context.getString(R.string.error_specific_title, type);
        }
    }

    private static String createDescription(@NonNull Throwable throwable, @NonNull Context context) {
        String description = getMessagesFromThrowableAndCauses(throwable);
        if (description != null) {
            return context.getString(R.string.error_specific_description, description);
        } else {
            return context.getString(R.string.error_generic_description);
        }
    }

    @Nullable
    private static String getMessagesFromThrowableAndCauses(@NonNull Throwable throwable) {
        if (throwable instanceof RxJavaAssemblyException) {
            // these donn't have any meaningful messages
            return null;
        }
        String message = throwable.getLocalizedMessage();
        if (message == null) {
            message = throwable.getClass().getSimpleName();
        }
        if (!message.endsWith(".")) {
            message += ".";
        }
        if (throwable.getCause() != null) {
            String causeMessage = getMessagesFromThrowableAndCauses(throwable.getCause());
            if (causeMessage != null) {
                message += " " + causeMessage;
            }
        }

        return message;
    }

    public static class Builder {

        private final Context context;

        private String title;
        private String description;
        private Throwable cause;
        private String resolveLabel;
        private Completable resolveAction;
        private boolean removeWhenShown;
        private boolean canBeShownAsNotification;
        private boolean isExpected;

        public Builder(@NonNull Context context) {
            this.context = context;
        }

        public Builder withTitle(@NonNull String title) {
            this.title = title;
            return this;
        }

        public Builder withTitle(@StringRes int stringResource) {
            return withTitle(context.getString(stringResource));
        }

        public Builder withDescription(@NonNull String description) {
            this.description = description;
            return this;
        }

        public Builder withDescription(@StringRes int stringResource) {
            return withDescription(context.getString(stringResource));
        }

        public Builder withCause(@NonNull Throwable cause) {
            this.cause = cause;
            if (title == null) {
                title = createTitle(cause, context);
            }
            if (description == null) {
                description = createDescription(cause, context);
            }
            return this;
        }

        public Builder withResolveLabel(@NonNull String resolveLabel) {
            this.resolveLabel = resolveLabel;
            return this;
        }

        public Builder withResolveLabel(@StringRes int stringResource) {
            return withResolveLabel(context.getString(stringResource));
        }

        public Builder withResolveAction(@NonNull Completable resolveAction) {
            this.resolveAction = resolveAction;
            return this;
        }

        public Builder removeWhenShown() {
            removeWhenShown = true;
            return this;
        }

        public Builder canBeShownAsNotification() {
            canBeShownAsNotification = true;
            return this;
        }

        public Builder isExpected() {
            isExpected = true;
            return this;
        }

        public Builder setExpected(boolean isExpected) {
            this.isExpected = isExpected;
            return this;
        }

        public ViewError build() {
            if (title == null) {
                throw new IllegalStateException("No error title set");
            } else if (description == null) {
                throw new IllegalStateException("No error description set");
            } else if (resolveAction != null && resolveLabel == null) {
                throw new IllegalStateException("No resolve label set");
            }

            ViewError viewError = new ViewError(title, description);
            viewError.setCause(cause);
            viewError.setResolveAction(resolveAction);
            viewError.setResolveLabel(resolveLabel);
            viewError.setRemoveWhenShown(removeWhenShown);
            viewError.setCanBeShownAsNotification(canBeShownAsNotification);
            viewError.setExpected(isExpected);

            return viewError;
        }

    }

}

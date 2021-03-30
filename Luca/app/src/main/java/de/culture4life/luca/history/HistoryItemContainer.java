package de.culture4life.luca.history;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collection;

import androidx.annotation.NonNull;

/**
 * Helper class for persisting a list of {@link HistoryItem}. As we use {@link Gson} for our
 * preferences, but use only classes for re-constructing our classes we are unable to persist
 * classes with generic types.
 */
public class HistoryItemContainer extends ArrayList<HistoryItem> {

    public HistoryItemContainer() {
    }

    public HistoryItemContainer(@NonNull Collection<? extends HistoryItem> collection) {
        super(collection);
    }

}

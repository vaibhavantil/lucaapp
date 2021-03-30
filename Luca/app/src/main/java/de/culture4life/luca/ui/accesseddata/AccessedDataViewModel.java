package de.culture4life.luca.ui.accesseddata;

import android.app.Application;

import de.culture4life.luca.R;
import de.culture4life.luca.dataaccess.AccessedTraceData;
import de.culture4life.luca.dataaccess.DataAccessManager;
import de.culture4life.luca.history.HistoryItem;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.history.TraceDataAccessedItem;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.ViewEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class AccessedDataViewModel extends BaseViewModel {

    private final HistoryManager historyManager;
    private final DataAccessManager dataAccessManager;

    private final SimpleDateFormat readableDateFormat;

    private final MutableLiveData<List<AccessedDataListItem>> accessedDataItems = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<List<AccessedTraceData>>> accessedData = new MutableLiveData<>();

    private ViewError dataSharingError;

    public AccessedDataViewModel(@NonNull Application application) {
        super(application);
        historyManager = this.application.getHistoryManager();
        dataAccessManager = this.application.getDataAccessManager();
        readableDateFormat = new SimpleDateFormat(application.getString(R.string.venue_checked_in_time_format), Locale.GERMANY);
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.mergeArray(
                        historyManager.initialize(application),
                        dataAccessManager.initialize(application)
                ))
                .andThen(invokeAccessedDataUpdate());
    }

    private Completable invokeAccessedDataUpdate() {
        return Completable.fromAction(() -> modelDisposable.add(updateAccessedDataItems()
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.i("Updated history"),
                        throwable -> Timber.w("Unable to update history: %s", throwable.toString())
                )));
    }

    private Completable updateAccessedDataItems() {
        return loadAccessedDataItems()
                .toList()
                .flatMapCompletable(items -> update(accessedDataItems, items));
    }

    private Observable<AccessedDataListItem> loadAccessedDataItems() {
        return historyManager.getItems()
                .filter(historyItem -> historyItem.getType() == HistoryItem.TYPE_TRACE_DATA_ACCESSED)
                .cast(TraceDataAccessedItem.class)
                .flatMapMaybe(this::createAccessedDataListItem)
                .sorted((first, second) -> Long.compare(second.getTimestamp(), first.getTimestamp()));
    }

    private Maybe<AccessedDataListItem> createAccessedDataListItem(@NonNull TraceDataAccessedItem dataAccessedItem) {
        return Maybe.fromCallable(() -> {
            AccessedDataListItem item = new AccessedDataListItem(application);
            item.setTimestamp(dataAccessedItem.getTimestamp());
            item.setTime(application.getString(R.string.accessed_data_time,
                    getReadableTime(dataAccessedItem.getCheckInTimestamp()),
                    getReadableTime(dataAccessedItem.getCheckOutTimestamp())
            ));
            item.setTitle(application.getString(R.string.accessed_data_title, dataAccessedItem.getHealthDepartmentName()));
            item.setDescription(application.getString(R.string.accessed_data_description, dataAccessedItem.getLocationName()));
            return item;
        });
    }

    private String getReadableTime(long timestamp) {
        return readableDateFormat.format(new Date(timestamp));
    }

    public LiveData<List<AccessedDataListItem>> getAccessedDataItems() {
        return accessedDataItems;
    }

    public LiveData<ViewEvent<List<AccessedTraceData>>> getAccessedData() {
        return accessedData;
    }

}

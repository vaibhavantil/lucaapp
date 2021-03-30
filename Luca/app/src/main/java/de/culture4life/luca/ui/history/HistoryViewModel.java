package de.culture4life.luca.ui.history;

import android.app.Application;

import de.culture4life.luca.R;
import de.culture4life.luca.dataaccess.AccessedTraceData;
import de.culture4life.luca.dataaccess.DataAccessManager;
import de.culture4life.luca.history.HistoryItem;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.history.MeetingEndedItem;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.ViewEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static de.culture4life.luca.history.HistoryManager.createUnorderedList;

public class HistoryViewModel extends BaseViewModel {

    private static final int TAN_CHARS_PER_SECTION = 4;

    private final HistoryManager historyManager;
    private final DataAccessManager dataAccessManager;

    private final SimpleDateFormat readableDateFormat;

    private final MutableLiveData<List<HistoryListItem>> historyItems = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<String>> tracingTanEvent = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<List<AccessedTraceData>>> newAccessedData = new MutableLiveData<>();

    private ViewError dataSharingError;

    public HistoryViewModel(@NonNull Application application) {
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
                .andThen(invokeHistoryUpdate())
                .andThen(invokeNewAccessedDataUpdate());
    }

    private Completable invokeHistoryUpdate() {
        return Completable.fromAction(() -> modelDisposable.add(updateHistoryItems()
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.i("Updated history"),
                        throwable -> Timber.w("Unable to update history: %s", throwable.toString())
                )));
    }

    private Completable invokeNewAccessedDataUpdate() {
        return Completable.fromAction(() -> modelDisposable.add(showNewAccessedDataIfAvailable()
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.i("Updated accessed data"),
                        throwable -> Timber.w("Unable to update accessed data: %s", throwable.toString())
                )));
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                observeHistoryChanges()
        );
    }

    private Completable observeHistoryChanges() {
        return historyManager.getNewItems()
                .throttleLatest(1, TimeUnit.SECONDS)
                .switchMapCompletable(historyItem -> updateHistoryItems());
    }

    private Completable showNewAccessedDataIfAvailable() {
        return dataAccessManager.getAccessedTraceDataNotYetInformedAbout()
                .toList()
                .filter(accessedTraceData -> !accessedTraceData.isEmpty())
                .flatMapCompletable(this::informAboutDataAccess);
    }

    private Completable informAboutDataAccess(List<AccessedTraceData> accessedTraceData) {
        return update(newAccessedData, new ViewEvent<>(accessedTraceData))
                .andThen(dataAccessManager.markAllAccessedTraceDataAsInformedAbout());
    }

    private Completable updateHistoryItems() {
        return loadHistoryItems()
                .toList()
                .flatMapCompletable(items -> update(historyItems, items));
    }

    private Observable<HistoryListItem> loadHistoryItems() {
        // get all available items
        Observable<HistoryItem> cachedItems = historyManager.getItems().cache();

        // find items that belong to each other
        Observable<Pair<HistoryItem, HistoryItem>> cachedStartAndEndItems = cachedItems
                .flatMapMaybe(endItem -> getHistoryStartItem(endItem, cachedItems)
                        .map(startItem -> new Pair<>(startItem, endItem)))
                .cache();

        // find items that don't belong to another item
        Observable<HistoryItem> cachedSingleItems = cachedStartAndEndItems
                .flatMap(historyItemPair -> Observable.just(historyItemPair.first, historyItemPair.second))
                .toList()
                .flatMapObservable(startAndEndItemsList -> cachedItems
                        .filter(historyItem -> !startAndEndItemsList.contains(historyItem)));

        // convert and merge items
        return Observable.mergeArray(
                cachedStartAndEndItems.flatMapMaybe(this::createHistoryViewItem),
                cachedSingleItems.flatMapMaybe(this::createHistoryViewItem)
        ).sorted((first, second) -> Long.compare(second.getTimestamp(), first.getTimestamp()));
    }

    /**
     * Attempts to find the start item for the specified end item.
     *
     * @param endItem      the item to find the matching start item for
     * @param historyItems all available items, sorted by timestamp (descending)
     */
    private Maybe<HistoryItem> getHistoryStartItem(@NonNull HistoryItem endItem, @NonNull Observable<HistoryItem> historyItems) {
        return Maybe.fromCallable(() -> {
            // get the matching start item type
            if (endItem.getType() == HistoryItem.TYPE_CHECK_OUT) {
                return HistoryItem.TYPE_CHECK_IN;
            } else if (endItem.getType() == HistoryItem.TYPE_MEETING_ENDED) {
                return HistoryItem.TYPE_MEETING_STARTED;
            } else {
                return null;
            }
        }).flatMap(startItemType -> historyItems.filter(historyItem -> historyItem.getType() == startItemType)
                .filter(historyItem -> historyItem.getTimestamp() < endItem.getTimestamp())
                .firstElement());
    }

    private Maybe<HistoryListItem> createHistoryViewItem(@NonNull Pair<HistoryItem, HistoryItem> historyItemPair) {
        return Maybe.zip(createHistoryViewItem(historyItemPair.first), createHistoryViewItem(historyItemPair.second), (start, end) -> {
            HistoryListItem merged = new HistoryListItem(application);
            merged.setTitle(end.getTitle());
            merged.setDescription(end.getDescription());
            merged.setAdditionalDetails(end.getAdditionalDetails());
            merged.setTime(application.getString(R.string.history_time_merged, start.getTime(), end.getTime()));
            merged.setTimestamp(end.getTimestamp());
            merged.setIconResourceId(end.getIconResourceId());
            return merged;
        });
    }

    private Maybe<HistoryListItem> createHistoryViewItem(@NonNull HistoryItem historyItem) {
        return Maybe.fromCallable(() -> {
            HistoryListItem item = new HistoryListItem(application);
            item.setTimestamp(historyItem.getTimestamp());
            item.setTime(application.getString(R.string.history_time, getReadableTime(historyItem.getTimestamp())));
            switch (historyItem.getType()) {
                case HistoryItem.TYPE_CHECK_IN: {
                    // intended fall-through
                }
                case HistoryItem.TYPE_CHECK_OUT: {
                    item.setTitle(historyItem.getDisplayName());
                    boolean accessed = dataAccessManager.hasBeenAccessed(historyItem.getRelatedId()).blockingGet();
                    if (accessed) {
                        item.setAdditionalDetails(application.getString(R.string.history_data_accessed_details));
                        item.setIconResourceId(R.drawable.ic_eye);
                    }
                    break;
                }
                case HistoryItem.TYPE_MEETING_STARTED: {
                    item.setTitle(application.getString(R.string.history_meeting_started_title));
                    break;
                }
                case HistoryItem.TYPE_MEETING_ENDED: {
                    MeetingEndedItem meetingEndedItem = (MeetingEndedItem) historyItem;
                    item.setTitle(application.getString(R.string.history_meeting_ended_title));
                    if (meetingEndedItem.getGuests().isEmpty()) {
                        item.setDescription(application.getString(R.string.history_meeting_empty_description));
                    } else {
                        int guestsCount = meetingEndedItem.getGuests().size();
                        item.setDescription(application.getString(R.string.history_meeting_not_empty_description, guestsCount));
                        String guestList = createUnorderedList(meetingEndedItem.getGuests());
                        if (guestList.isEmpty()) {
                            guestList = getApplication().getString(R.string.history_meeting_empty_description);
                        }
                        item.setAdditionalDetails(application.getString(R.string.history_meeting_ended_description, guestList));
                        item.setIconResourceId(R.drawable.ic_information_outline);
                    }
                    break;
                }
                case HistoryItem.TYPE_CONTACT_DATA_REQUEST: {
                    item.setTitle(application.getString(R.string.history_data_shared_title));
                    item.setAdditionalDetails(application.getString(R.string.history_data_shared_description));
                    item.setIconResourceId(R.drawable.ic_information_outline);
                    break;
                }
                default: {
                    Timber.w("Unknown history item type: %d", historyItem.getType());
                    return null;
                }
            }
            return item;
        });
    }

    public void onShareHistoryRequested() {
        Timber.d("onShareHistoryRequested() called");
        modelDisposable.add(application.getRegistrationManager()
                .transferUserData()
                .doOnSubscribe(disposable -> {
                    updateAsSideEffect(isLoading, true);
                    removeError(dataSharingError);
                })
                .map(String::toUpperCase)
                .map(tracingTan -> {
                    if (tracingTan.contains("-")) {
                        return tracingTan;
                    }
                    StringBuilder tracingTanBuilder = new StringBuilder(tracingTan);
                    for (int i = 1; i < tracingTan.length() / TAN_CHARS_PER_SECTION; i++) {
                        int hyphenPosition = tracingTanBuilder.lastIndexOf("-") + TAN_CHARS_PER_SECTION + 1;
                        tracingTanBuilder.insert(hyphenPosition, "-");
                    }
                    return tracingTanBuilder.toString();
                })
                .flatMapCompletable(tracingTan -> Completable.mergeArray(
                        update(tracingTanEvent, new ViewEvent<>(tracingTan)),
                        historyManager.addDataSharedItem(tracingTan)
                ))
                .doOnError(throwable -> {
                    dataSharingError = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title)
                            .withResolveAction(Completable.fromAction(this::onShareHistoryRequested))
                            .withResolveLabel(R.string.action_retry)
                            .build();
                    addError(dataSharingError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.d("Received data sharing TAN"),
                        throwable -> Timber.w("Unable to get data sharing TAN: %s", throwable.toString())
                ));
    }

    public void onShowAccessedDataRequested() {
        navigationController.navigate(R.id.action_historyFragment_to_accessedDataFragment);
    }

    private String getReadableTime(long timestamp) {
        return readableDateFormat.format(new Date(timestamp));
    }

    public LiveData<List<HistoryListItem>> getHistoryItems() {
        return historyItems;
    }

    public LiveData<ViewEvent<String>> getTracingTanEvent() {
        return tracingTanEvent;
    }

    public LiveData<ViewEvent<List<AccessedTraceData>>> getNewAccessedData() {
        return newAccessedData;
    }

}

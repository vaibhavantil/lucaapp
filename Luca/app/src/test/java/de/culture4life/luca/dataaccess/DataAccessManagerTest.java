package de.culture4life.luca.dataaccess;

import de.culture4life.luca.LucaUnitTest;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.AccessedHashedTraceIdsData;
import de.culture4life.luca.network.pojo.HealthDepartment;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.preference.PreferencesManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import androidx.test.runner.AndroidJUnit4;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Config(sdk = 28)
@RunWith(AndroidJUnit4.class)
public class DataAccessManagerTest extends LucaUnitTest {

    PreferencesManager preferencesManager;
    LucaNotificationManager notificationManager;
    LocationManager locationManager;
    NetworkManager networkManager;
    HistoryManager historyManager;
    CryptoManager cryptoManager;
    CheckInManager checkInManager;
    DataAccessManager dataAccessManager;

    @Before
    public void setUp() {
        preferencesManager = spy(new PreferencesManager());
        notificationManager = spy(new LucaNotificationManager());
        locationManager = spy(new LocationManager());
        networkManager = spy(new NetworkManager());
        historyManager = spy(new HistoryManager(preferencesManager));
        cryptoManager = spy(new CryptoManager(preferencesManager, networkManager));
        checkInManager = spy(new CheckInManager(preferencesManager, networkManager, locationManager, historyManager, cryptoManager));

        dataAccessManager = spy(new DataAccessManager(preferencesManager, networkManager, notificationManager, checkInManager, historyManager, cryptoManager));
        dataAccessManager.initialize(application).blockingAwait();
    }

    @Test
    public void update_successful_updatesLastUpdateTimestamp() {
        doReturn(Observable.empty())
                .when(dataAccessManager)
                .fetchNewRecentlyAccessedTraceData();

        long previousDuration = dataAccessManager.getDurationSinceLastUpdate().blockingGet();

        dataAccessManager.update()
                .andThen(dataAccessManager.getDurationSinceLastUpdate())
                .test()
                .assertValue(durationSinceLastUpdate -> durationSinceLastUpdate < previousDuration);
    }

    @Test
    public void update_unsuccessful_doesNotUpdateLastUpdateTimestamp() {
        doReturn(Observable.error(new RuntimeException()))
                .when(dataAccessManager)
                .fetchNewRecentlyAccessedTraceData();

        long previousDuration = dataAccessManager.getDurationSinceLastUpdate().blockingGet();

        dataAccessManager.update().onErrorComplete()
                .andThen(dataAccessManager.getDurationSinceLastUpdate())
                .test()
                .assertValue(previousDuration);
    }

    @Test
    public void getDurationSinceLastUpdate_justUpdated_emitsLowDuration() {
        doReturn(Observable.empty())
                .when(dataAccessManager)
                .fetchNewRecentlyAccessedTraceData();

        dataAccessManager.update()
                .andThen(dataAccessManager.getDurationSinceLastUpdate())
                .test()
                .assertValue(durationSinceLastUpdate -> durationSinceLastUpdate < 1000);
    }

    @Test
    public void getDurationSinceLastUpdate_neverUpdated_emitsHighDuration() {
        dataAccessManager.getDurationSinceLastUpdate()
                .test()
                .assertValue(durationSinceLastUpdate -> durationSinceLastUpdate > System.currentTimeMillis() - 1000);
    }

    @Test
    public void getNextRecommendedUpdateDelay_justUpdated_emitsUpdateInterval() {
        doReturn(Single.just(0L))
                .when(dataAccessManager)
                .getDurationSinceLastUpdate();

        dataAccessManager.getNextRecommendedUpdateDelay()
                .test()
                .assertValue(DataAccessManager.UPDATE_INTERVAL);
    }

    @Test
    public void getNextRecommendedUpdateDelay_neverUpdated_emitsLowDelay() {
        doReturn(Single.just(System.currentTimeMillis()))
                .when(dataAccessManager)
                .getDurationSinceLastUpdate();

        dataAccessManager.getNextRecommendedUpdateDelay()
                .test()
                .assertValue(DataAccessManager.UPDATE_INITIAL_DELAY);
    }

    @Test
    public void processNewRecentlyAccessedTraceData_dataAvailable_performsProcessing() throws InterruptedException {
        AccessedTraceData newAccessedTraceData = new AccessedTraceData();
        newAccessedTraceData.setHashedTraceId("LLJMzA/HqlS77qkpUGNJrA==");

        dataAccessManager.processNewRecentlyAccessedTraceData(Collections.singletonList(newAccessedTraceData))
                .test()
                .await()
                .assertComplete();

        verify(dataAccessManager, times(1)).addToAccessedData(any());
        verify(dataAccessManager, times(1)).addHistoryItems(any());
        verify(dataAccessManager, times(1)).notifyUserAboutDataAccess(any());
    }

    @Test
    public void processNewRecentlyAccessedTraceData_noDataAvailable_performsNothing() throws InterruptedException {
        dataAccessManager.processNewRecentlyAccessedTraceData(Collections.emptyList())
                .test()
                .await()
                .assertComplete();

        verify(dataAccessManager, never()).addToAccessedData(any());
        verify(dataAccessManager, never()).addHistoryItems(any());
        verify(dataAccessManager, never()).notifyUserAboutDataAccess(any());
    }

    @Test
    public void notifyUserAboutDataAccess_validData_showsNotification() {
        AccessedTraceData newAccessedTraceData = new AccessedTraceData();
        newAccessedTraceData.setHashedTraceId("LLJMzA/HqlS77qkpUGNJrA==");

        dataAccessManager.notifyUserAboutDataAccess(Collections.singletonList(newAccessedTraceData))
                .test()
                .assertComplete();

        verify(notificationManager, times(1))
                .showNotification(eq(LucaNotificationManager.NOTIFICATION_ID_DATA_ACCESS), any());
    }

    @Test
    public void getRecentTraceIds_checkInsAvailable_emitsTraceIdsFromCheckIns() {
        String traceId = "9bZZ5Ak465V60PXv92aMFA==";
        doReturn(Observable.just(traceId))
                .when(checkInManager)
                .getArchivedTraceIds();

        dataAccessManager.getRecentTraceIds()
                .test()
                .assertValues(traceId)
                .assertComplete();
    }

    @Test
    public void fetchRecentlyAccessedTraceData_noRecentTraceIds_completesEmpty() {
        AccessedHashedTraceIdsData accessedHashedTraceIdsData = new AccessedHashedTraceIdsData();
        doReturn(Observable.just(accessedHashedTraceIdsData)).when(dataAccessManager)
                .fetchAllRecentlyAccessedHashedTraceIdsData();

        doReturn(Observable.empty()).when(dataAccessManager).getRecentTraceIds();

        dataAccessManager.fetchRecentlyAccessedTraceData()
                .test()
                .assertNoValues()
                .assertComplete();
    }

    @Test
    public void fetchRecentlyAccessedTraceData_noRecentAccessedHashedTraceIds_completesEmpty() {
        doReturn(Observable.empty())
                .when(dataAccessManager)
                .fetchAllRecentlyAccessedHashedTraceIdsData();

        doReturn(Observable.just("hCvt6FNlhomxbBmL50PYDw=="))
                .when(dataAccessManager)
                .getRecentTraceIds();

        dataAccessManager.fetchRecentlyAccessedTraceData()
                .test()
                .assertNoValues()
                .assertComplete();
    }

    @Test
    public void fetchRecentlyAccessedTraceData_noDataAccessed_completesEmpty() {
        HealthDepartment healthDepartment = new HealthDepartment();
        healthDepartment.setId("8fa43091-261a-45f0-a893-548fc1271025");

        AccessedHashedTraceIdsData accessedHashedTraceIdsData = new AccessedHashedTraceIdsData();
        accessedHashedTraceIdsData.setHealthDepartment(healthDepartment);
        accessedHashedTraceIdsData.getHashedTraceIds().add("LLJMzA/HqlS77qkpUGNJrA=="); // 99FmQcylJT5e/cyHOjT6Hw==

        doReturn(Observable.just(accessedHashedTraceIdsData))
                .when(dataAccessManager)
                .fetchAllRecentlyAccessedHashedTraceIdsData();

        doReturn(Observable.just("hCvt6FNlhomxbBmL50PYDw=="))
                .when(dataAccessManager)
                .getRecentTraceIds();

        dataAccessManager.fetchRecentlyAccessedTraceData()
                .test()
                .assertNoValues()
                .assertComplete();
    }

    @Test
    public void fetchRecentlyAccessedTraceData_someDataAccessed_emitsAccessedData() {
        HealthDepartment healthDepartment = new HealthDepartment();
        healthDepartment.setId("8fa43091-261a-45f0-a893-548fc1271025");

        AccessedHashedTraceIdsData accessedHashedTraceIdsData = new AccessedHashedTraceIdsData();
        accessedHashedTraceIdsData.setHealthDepartment(healthDepartment);
        accessedHashedTraceIdsData.getHashedTraceIds().add("qiqA2+SpnoioxRMWb7IDsw=="); // 9bZZ5Ak465V60PXv92aMFA==
        accessedHashedTraceIdsData.getHashedTraceIds().add("LLJMzA/HqlS77qkpUGNJrA=="); // 99FmQcylJT5e/cyHOjT6Hw==

        doReturn(Observable.just(accessedHashedTraceIdsData))
                .when(dataAccessManager)
                .fetchAllRecentlyAccessedHashedTraceIdsData();

        doReturn(Observable.just("9bZZ5Ak465V60PXv92aMFA==", "hCvt6FNlhomxbBmL50PYDw=="))
                .when(dataAccessManager)
                .getRecentTraceIds();

        dataAccessManager.fetchRecentlyAccessedTraceData()
                .map(AccessedTraceData::getTraceId)
                .test()
                .assertValues("9bZZ5Ak465V60PXv92aMFA==")
                .assertComplete();
    }

    @Test
    public void fetchNewRecentlyAccessedTraceData_someNewDataAccessed_emitsNewAccessedData() {
        AccessedTraceData newAccessedTraceData = new AccessedTraceData();
        newAccessedTraceData.setHashedTraceId("LLJMzA/HqlS77qkpUGNJrA==");

        AccessedTraceData previouslyAccessedTraceData = new AccessedTraceData();
        previouslyAccessedTraceData.setHashedTraceId("qiqA2+SpnoioxRMWb7IDsw==");

        doReturn(Observable.just(previouslyAccessedTraceData, newAccessedTraceData))
                .when(dataAccessManager)
                .fetchRecentlyAccessedTraceData();

        doReturn(Observable.just(previouslyAccessedTraceData))
                .when(dataAccessManager)
                .getPreviouslyAccessedTraceData();

        dataAccessManager.fetchNewRecentlyAccessedTraceData()
                .test()
                .assertValues(newAccessedTraceData)
                .assertComplete();
    }

    @Test
    public void getPreviouslyAccessedTraceData_noDataPreviouslyAccessed_completesEmpty() {
        dataAccessManager.getPreviouslyAccessedTraceData()
                .test()
                .assertNoValues()
                .assertComplete();
    }

    @Test
    public void getPreviouslyAccessedTraceData_someDataPreviouslyAccessed_emitsPreviouslyAccessedData() {
        AccessedTraceData previouslyAccessedTraceData = new AccessedTraceData();
        previouslyAccessedTraceData.setHashedTraceId("qiqA2+SpnoioxRMWb7IDsw==");

        dataAccessManager.addToAccessedData(Collections.singletonList(previouslyAccessedTraceData))
                .andThen(dataAccessManager.getPreviouslyAccessedTraceData())
                .map(AccessedTraceData::getHashedTraceId)
                .test()
                .assertValues(previouslyAccessedTraceData.getHashedTraceId())
                .assertComplete();
    }

    @Test
    public void getAccessedTraceDataNotYetInformedAbout_noDataAvailable_completesEmpty() {
        AccessedTraceData previouslyAccessedTraceData = new AccessedTraceData();
        previouslyAccessedTraceData.setHashedTraceId("qiqA2+SpnoioxRMWb7IDsw==");

        dataAccessManager.addToAccessedData(Collections.singletonList(previouslyAccessedTraceData))
                .andThen(dataAccessManager.markAllAccessedTraceDataAsInformedAbout())
                .andThen(dataAccessManager.getAccessedTraceDataNotYetInformedAbout())
                .test()
                .assertNoValues()
                .assertComplete();
    }

    @Test
    public void getAccessedTraceDataNotYetInformedAbout_someDataAvailable_emitsData() {
        AccessedTraceData previouslyAccessedTraceData = new AccessedTraceData();
        previouslyAccessedTraceData.setHashedTraceId("qiqA2+SpnoioxRMWb7IDsw==");
        previouslyAccessedTraceData.setAccessTimestamp(System.currentTimeMillis() - 1000);

        AccessedTraceData newAccessedTraceData = new AccessedTraceData();
        newAccessedTraceData.setHashedTraceId("LLJMzA/HqlS77qkpUGNJrA==");
        newAccessedTraceData.setAccessTimestamp(System.currentTimeMillis() + 1000);

        dataAccessManager.addToAccessedData(Collections.singletonList(previouslyAccessedTraceData))
                .andThen(dataAccessManager.markAllAccessedTraceDataAsInformedAbout())
                .andThen(dataAccessManager.addToAccessedData(Collections.singletonList(newAccessedTraceData)))
                .andThen(dataAccessManager.getAccessedTraceDataNotYetInformedAbout())
                .test()
                .assertValues(newAccessedTraceData)
                .assertComplete();
    }

    @Test
    public void hasBeenAccessed_matchingHashedTraceIds_returnsTrue() {
        AccessedTraceData potentiallyAccessedData = new AccessedTraceData();
        potentiallyAccessedData.setHashedTraceId("qiqA2+SpnoioxRMWb7IDsw==");

        AccessedHashedTraceIdsData accessedData = new AccessedHashedTraceIdsData();
        accessedData.getHashedTraceIds().add(potentiallyAccessedData.getHashedTraceId());

        assertTrue(DataAccessManager.hasBeenAccessed(potentiallyAccessedData, accessedData));
    }

    @Test
    public void hasBeenAccessed_nonMatchingHashingTraceIds_returnsFalse() {
        AccessedTraceData potentiallyAccessedData = new AccessedTraceData();
        potentiallyAccessedData.setHashedTraceId("qiqA2+SpnoioxRMWb7IDsw==");

        AccessedHashedTraceIdsData accessedData = new AccessedHashedTraceIdsData();

        assertFalse(DataAccessManager.hasBeenAccessed(potentiallyAccessedData, accessedData));
    }

    @Test
    public void hasBeenAccessed_accessedTraceId_emitsTrue() {
        AccessedTraceData newAccessedTraceData = new AccessedTraceData();
        newAccessedTraceData.setTraceId("9bZZ5Ak465V60PXv92aMFA==");

        dataAccessManager.addToAccessedData(Collections.singletonList(newAccessedTraceData))
                .test()
                .assertComplete();

        dataAccessManager.hasBeenAccessed(newAccessedTraceData.getTraceId())
                .test()
                .assertValue(true);
    }

    @Test
    public void hasBeenAccessed_nonAccessedTraceId_emitsFalse() {
        dataAccessManager.hasBeenAccessed("9bZZ5Ak465V60PXv92aMFA==")
                .test()
                .assertValue(false);
    }

    @Test
    public void getHashedTraceId_validTraceId_emitsExpectedHash() {
        dataAccessManager.getHashedTraceId("8fa43091-261a-45f0-a893-548fc1271025", "9bZZ5Ak465V60PXv92aMFA==")
                .test()
                .assertValue("qiqA2+SpnoioxRMWb7IDsw==");
    }

    @Test
    public void getHashedTraceId_validTraceId_emitsExpectedHash2() {
        dataAccessManager.getHashedTraceId("8fa43091-261a-45f0-a893-548fc1271025", "99FmQcylJT5e/cyHOjT6Hw==")
                .test()
                .assertValue("LLJMzA/HqlS77qkpUGNJrA==");
    }

    @Test
    public void getHashedTraceId_distinctTraceIds_emitsDistinctHashes() {
        Single<String> firstHash = dataAccessManager.getHashedTraceId("8fa43091-261a-45f0-a893-548fc1271025", "9bZZ5Ak465V60PXv92aMFA==");
        Single<String> secondHash = dataAccessManager.getHashedTraceId("8fa43091-261a-45f0-a893-548fc1271025", "99FmQcylJT5e/cyHOjT6Hw==");
        Single.zip(firstHash, secondHash, Objects::equals)
                .test()
                .assertValue(false);
    }

    @Test
    public void getHashedTraceId_distinctHealthDepartments_emitsDistinctHashes() {
        Single<String> firstHash = dataAccessManager.getHashedTraceId("8fa43091-261a-45f0-a893-548fc1271025", "9bZZ5Ak465V60PXv92aMFA==");
        Single<String> secondHash = dataAccessManager.getHashedTraceId("de4c27f1-2bda-4d50-90cf-7489207de45c", "9bZZ5Ak465V60PXv92aMFA==");
        Single.zip(firstHash, secondHash, Objects::equals)
                .test()
                .assertValue(false);
    }

    @Test
    public void restoreAccessedData_noDataPreviouslyPersisted_emitsEmptyData() {
        dataAccessManager.restoreAccessedData()
                .map(AccessedData::getTraceData)
                .map(List::size)
                .test()
                .assertValue(0);
    }

    @Test
    public void restoreAccessedData_someDataPreviouslyPersisted_emitsPersistedData() {
        AccessedTraceData newAccessedTraceData = new AccessedTraceData();
        newAccessedTraceData.setTraceId("9bZZ5Ak465V60PXv92aMFA==");

        AccessedData newAccessedData = new AccessedData();
        newAccessedData.getTraceData().add(newAccessedTraceData);

        dataAccessManager.persistAccessedData(newAccessedData)
                .test()
                .assertComplete();

        dataAccessManager.restoreAccessedData()
                .map(AccessedData::getTraceData)
                .map(accessedTraceData -> accessedTraceData.get(0))
                .map(AccessedTraceData::getTraceId)
                .test()
                .assertValue(newAccessedTraceData.getTraceId());
    }

    @Test
    public void persistAccessedData_validData_persistsData() {
        AccessedTraceData newAccessedTraceData = new AccessedTraceData();
        newAccessedTraceData.setTraceId("traceId");

        AccessedData newAccessedData = new AccessedData();
        newAccessedData.getTraceData().add(newAccessedTraceData);

        dataAccessManager.persistAccessedData(newAccessedData)
                .test()
                .assertComplete();

        dataAccessManager.getOrRestoreAccessedData()
                .test()
                .assertValue(newAccessedData);

        dataAccessManager.restoreAccessedData()
                .map(AccessedData::getTraceData)
                .map(accessedTraceData -> accessedTraceData.get(0))
                .map(AccessedTraceData::getTraceId)
                .test()
                .assertValue(newAccessedTraceData.getTraceId());
    }

    @Test
    public void addToAccessedData_validData_updatesAccessedData() {
        AccessedTraceData newAccessedTraceData = new AccessedTraceData();

        dataAccessManager.addToAccessedData(Collections.singletonList(newAccessedTraceData))
                .test()
                .assertComplete();

        dataAccessManager.getOrRestoreAccessedData()
                .test()
                .assertValue(accessedData -> accessedData.getTraceData().contains(newAccessedTraceData));

        dataAccessManager.restoreAccessedData()
                .map(accessedData -> accessedData.getTraceData().size())
                .test()
                .assertValue(1);
    }

}
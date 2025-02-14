/*
 *    Copyright 2020 Criteo
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.criteo.publisher;

import static com.criteo.publisher.concurrent.ThreadingUtil.waitForMessageQueueToBeIdle;
import static com.criteo.publisher.util.AdUnitType.CRITEO_BANNER;
import static com.criteo.publisher.util.CompletableFuture.completedFuture;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.annotation.NonNull;
import com.criteo.publisher.bid.BidLifecycleListener;
import com.criteo.publisher.cache.SdkCache;
import com.criteo.publisher.context.ContextData;
import com.criteo.publisher.context.ContextProvider;
import com.criteo.publisher.csm.MetricSendingQueueConsumer;
import com.criteo.publisher.integration.Integration;
import com.criteo.publisher.integration.IntegrationRegistry;
import com.criteo.publisher.logging.Logger;
import com.criteo.publisher.logging.RemoteLogSendingQueueConsumer;
import com.criteo.publisher.mock.MockBean;
import com.criteo.publisher.mock.MockedDependenciesRule;
import com.criteo.publisher.mock.SpyBean;
import com.criteo.publisher.model.AdSize;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.AdUnitMapper;
import com.criteo.publisher.model.BannerAdUnit;
import com.criteo.publisher.model.CacheAdUnit;
import com.criteo.publisher.model.CdbRequest;
import com.criteo.publisher.model.CdbRequestSlot;
import com.criteo.publisher.model.CdbResponse;
import com.criteo.publisher.model.CdbResponseSlot;
import com.criteo.publisher.model.Config;
import com.criteo.publisher.model.DeviceInfo;
import com.criteo.publisher.model.Publisher;
import com.criteo.publisher.model.RemoteConfigResponse;
import com.criteo.publisher.model.RewardedAdUnit;
import com.criteo.publisher.model.User;
import com.criteo.publisher.network.LiveBidRequestSender;
import com.criteo.publisher.network.PubSdkApi;
import com.criteo.publisher.privacy.UserPrivacyUtil;
import com.criteo.publisher.privacy.gdpr.GdprData;
import com.criteo.publisher.util.AdUnitType;
import com.criteo.publisher.util.AdvertisingInfo;
import com.criteo.publisher.util.BuildConfigWrapper;
import com.criteo.publisher.util.DeviceUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class BidManagerFunctionalTest {

  /**
   * Default TTL (in seconds) overridden on immediate bids (CPM > 0, TTL = 0)
   */
  private static final int DEFAULT_TTL_IN_SECONDS = 900;

  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule().withSpiedLogger();

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @SpyBean
  private SdkCache cache;

  @MockBean
  private Config config;

  @MockBean
  private PubSdkApi api;

  @MockBean
  private Clock clock;

  @SpyBean
  private LiveBidRequestSender liveBidRequestSender;

  @MockBean
  private BidLifecycleListener bidLifecycleListener;

  @SpyBean
  private BuildConfigWrapper buildConfigWrapper;

  @SpyBean
  private IntegrationRegistry integrationRegistry;

  @MockBean
  private MetricSendingQueueConsumer metricSendingQueueConsumer;

  @MockBean
  private RemoteLogSendingQueueConsumer remoteLogSendingQueueConsumer;

  @SpyBean
  private UserPrivacyUtil userPrivacyUtil;

  @SpyBean
  private DeviceInfo deviceInfo;

  @SpyBean
  private DeviceUtil deviceUtil;

  @SpyBean
  private Context context;

  @Inject
  private AdvertisingInfo advertisingInfo;

  @Mock
  private ContextData contextData;

  @MockBean
  private ContextProvider contextProvider;

  @SpyBean
  private AdUnitMapper adUnitMapper;

  @SpyBean
  private Executor executor;

  @Inject
  private Logger logger;

  @Inject
  private BidManager bidManager;

  private int adUnitId = 0;

  @Before
  public void setUp() throws Exception {
    when(context.getPackageName()).thenReturn("bundle.id");
    when(config.isPrefetchOnInitEnabled()).thenReturn(true);

    // Should be set to at least 1 because user-level silent mode is set the 0 included
    givenMockedClockSetTo(1);

    // Given unrelated ad units in the cache, the tests should ignore them
    givenNotExpiredValidCachedBid(sampleAdUnit());
    givenExpiredValidCachedBid(sampleAdUnit());
    givenNotExpiredSilentModeBidCached(sampleAdUnit());
    givenExpiredSilentModeBidCached(sampleAdUnit());
    givenNoLastBid(sampleAdUnit());
    givenTimeBudgetRespectedWhenFetchingLiveBids();
  }

  @Test
  public void unsupportedAdFormat_prefetch_GivenUnsupportedAdFormatForAnIntegration_ShouldFilterUnsupportedAdUnits() throws Exception {
    doReturn(Integration.FALLBACK).when(integrationRegistry).readIntegration();

    BannerAdUnit notFiltered = new BannerAdUnit("not_filtered", new AdSize(1, 2));
    RewardedAdUnit filtered1 = new RewardedAdUnit("filtered1");
    RewardedAdUnit filtered2 = new RewardedAdUnit("filtered2");
    List<AdUnit> prefetchAdUnits = Arrays.asList(notFiltered, filtered1, filtered2);

    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    bidManager.prefetch(prefetchAdUnits);
    waitForIdleState();

    CacheAdUnit expected = new CacheAdUnit(
        notFiltered.getSize(),
        notFiltered.getAdUnitId(),
        notFiltered.getAdUnitType()
    );

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(expected), slot);
    verify(logger).log(BiddingLogMessage.onUnsupportedAdFormat(toCacheAdUnit(filtered1), Integration.FALLBACK));
    verify(logger).log(BiddingLogMessage.onUnsupportedAdFormat(toCacheAdUnit(filtered2), Integration.FALLBACK));
  }

  @Test
  public void unsupportedAdFormat_prefetch_GivenSupportedAdFormatForAnIntegration_ShouldNotFilterIt() throws Exception {
    doReturn(Integration.MOPUB_APP_BIDDING).when(integrationRegistry).readIntegration();

    RewardedAdUnit notFiltered = new RewardedAdUnit("not_filtered");
    List<AdUnit> prefetchAdUnits = singletonList(notFiltered);

    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    bidManager.prefetch(prefetchAdUnits);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(toCacheAdUnit(notFiltered)), slot);
  }

  @Test
  public void unsupportedAdFormat_fetchForLiveBidRequest_GivenUnsupportedAdFormatForAnIntegration_ShouldFilterIt() throws Exception {
    doReturn(Integration.FALLBACK).when(integrationRegistry).readIntegration();

    RewardedAdUnit adUnit = new RewardedAdUnit("filtered");

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertNoLiveBidIsCached();
    assertNoLiveBidIsConsumedFromCache();
    assertShouldNotCallCdbAndNotPopulateCache();
    verify(logger).log(BiddingLogMessage.onUnsupportedAdFormat(toCacheAdUnit(adUnit), Integration.FALLBACK));
  }

  @Test
  public void unsupportedAdFormat_fetchForLiveBidRequest_GivenSupportedAdFormatForAnIntegration_ShouldHandleIt() throws Exception {
    givenMockedClockSetTo(42);
    givenTimeBudgetRespectedWhenFetchingLiveBids();

    doReturn(Integration.MOPUB_APP_BIDDING).when(integrationRegistry).readIntegration();

    RewardedAdUnit adUnit = new RewardedAdUnit("not_filtered");

    CdbResponseSlot slot = givenMockedCdbRespondingSlot();
    when(slot.getCpmAsNumber()).thenReturn(1.);
    when(slot.getTtlInSeconds()).thenReturn(0);

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    InOrder inOrder = inOrder(bidListener, slot);
    inOrder.verify(slot).setTimeOfDownload(42);
    inOrder.verify(bidListener).onBidResponse(slot);
    assertLiveBidIsConsumedDirectly(toCacheAdUnit(adUnit), slot);
    assertNoLiveBidIsCached();
  }

  @Test
  public void unsupportedAdFormat_getBidForAdUnitAndPrefetch_GivenUnsupportedAdFormatForAnIntegration_ShouldFilterIt() throws Exception {
    doReturn(Integration.FALLBACK).when(integrationRegistry).readIntegration();

    RewardedAdUnit adUnit = new RewardedAdUnit("filtered");

    CdbResponseSlot slot = bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertThat(slot).isNull();
    assertShouldNotCallCdbAndNotPopulateCache();
    verify(logger).log(BiddingLogMessage.onUnsupportedAdFormat(toCacheAdUnit(adUnit), Integration.FALLBACK));
  }

  @Test
  public void unsupportedAdFormat_getBidForAdUnitAndPrefetch_GivenSupportedAdFormatForAnIntegration_ShouldHandleIt() throws Exception {
    doReturn(Integration.MOPUB_APP_BIDDING).when(integrationRegistry).readIntegration();

    RewardedAdUnit adUnit = new RewardedAdUnit("filtered");

    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(toCacheAdUnit(adUnit)), slot);
  }

  @Test
  public void prefetch_GivenAdUnitsAndPrefetchDisabled_ShouldCallRemoteConfigButNotCdb() throws Exception {
    when(config.isPrefetchOnInitEnabled()).thenReturn(false);

    RemoteConfigResponse response = mock(RemoteConfigResponse.class);
    when(api.loadConfig(any())).thenReturn(response);

    List<AdUnit> prefetchAdUnits = Arrays.asList(
        mock(AdUnit.class),
        mock(AdUnit.class),
        mock(AdUnit.class)
    );

    bidManager.prefetch(prefetchAdUnits);
    waitForIdleState();

    verifyNoInteractions(adUnitMapper);
    assertShouldNotCallCdbAndNotPopulateCache();
    verify(config).refreshConfig(response);
  }

  @Test
  public void prefetch_GivenNoAdUnit_ShouldNotCallCdbAndPopulateCache() throws Exception {
    bidManager.prefetch(emptyList());
    waitForIdleState();

    assertShouldNotCallCdbAndNotPopulateCache();
  }

  @Test
  public void prefetch_GivenNoAdUnit_ShouldUpdateConfig() throws Exception {
    RemoteConfigResponse response = mock(RemoteConfigResponse.class);
    when(api.loadConfig(any())).thenReturn(response);

    bidManager.prefetch(emptyList());
    waitForIdleState();

    verify(config).refreshConfig(response);
    verify(api, never()).loadCdb(any(), any());
  }

  @Test
  public void prefetch_GivenAdUnits_ShouldCallCdbAndPopulateCache() throws Exception {
    List<AdUnit> prefetchAdUnits = Arrays.asList(
        mock(AdUnit.class),
        mock(AdUnit.class),
        mock(AdUnit.class)
    );

    List<List<CacheAdUnit>> mappedAdUnitsChunks = singletonList(Arrays.asList(
        sampleAdUnit(),
        sampleAdUnit()
    ));

    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    doReturn(mappedAdUnitsChunks).when(adUnitMapper).mapToChunks(prefetchAdUnits);

    bidManager.prefetch(prefetchAdUnits);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(mappedAdUnitsChunks.get(0), slot);
  }

  @Test
  public void prefetch_GivenMapperSplittingIntoChunks_ExecuteChunksIndependently()
      throws Exception {
    // Remove concurrency. This would make this test really hard to follow.
    // We should wait for idle state of main thread every time because the async task post execution
    // is running on it.
    doAnswer(answerVoid((Runnable runnable) -> {
      runnable.run();
      waitForMessageQueueToBeIdle();
    })).when(executor).execute(any());

    // Deactivate logging as it interferes with verification
    doNothing().when(logger).log(any());

    List<AdUnit> prefetchAdUnits = Arrays.asList(
        mock(AdUnit.class),
        mock(AdUnit.class),
        mock(AdUnit.class)
    );

    List<CacheAdUnit> requestedAdUnits1 = singletonList(sampleAdUnit());
    List<CacheAdUnit> requestedAdUnits2 = singletonList(sampleAdUnit());
    List<CacheAdUnit> requestedAdUnits3 = singletonList(sampleAdUnit());
    List<List<CacheAdUnit>> mappedAdUnitsChunks = Arrays.asList(
        requestedAdUnits1,
        requestedAdUnits2,
        requestedAdUnits3
    );

    doReturn(mappedAdUnitsChunks).when(adUnitMapper).mapToChunks(prefetchAdUnits);

    CdbResponse response1 = givenMockedCdbResponseWithValidSlot(1);
    CdbResponse response3 = givenMockedCdbResponseWithValidSlot(3);
    RemoteConfigResponse remoteConfigResponse = mock(RemoteConfigResponse.class);

    when(api.loadCdb(any(), any()))
        .thenReturn(response1)
        .thenThrow(IOException.class)
        .thenReturn(response3);
    when(api.loadConfig(any())).thenReturn(remoteConfigResponse);

    bidManager = spy(bidManager);
    bidManager.prefetch(prefetchAdUnits);
    waitForIdleState();

    InOrder inOrder = inOrder(bidManager, cache, api, config);

    // First call with only config call
    inOrder.verify(config).refreshConfig(remoteConfigResponse);

    // First call to CDB
    inOrder.verify(config, never()).refreshConfig(any());
    inOrder.verify(api)
        .loadCdb(argThat(cdb -> requestedAdUnits1.equals(getRequestedAdUnits(cdb))), any());
    response1.getSlots().forEach(inOrder.verify(cache)::add);
    inOrder.verify(bidManager).setTimeToNextCall(1);

    // Second call with error
    inOrder.verify(api)
        .loadCdb(argThat(cdb -> requestedAdUnits2.equals(getRequestedAdUnits(cdb))), any());

    // Third call in success but without the config call
    inOrder.verify(config, never()).refreshConfig(any());
    inOrder.verify(api)
        .loadCdb(argThat(cdb -> requestedAdUnits3.equals(getRequestedAdUnits(cdb))), any());
    response3.getSlots().forEach(inOrder.verify(cache)::add);
    inOrder.verify(bidManager).setTimeToNextCall(3);

    inOrder.verifyNoMoreInteractions();
  }

  private CdbResponse givenMockedCdbResponseWithValidSlot(int timeToNextCall) {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.isValid()).thenReturn(true);

    CdbResponse response = mock(CdbResponse.class);
    when(response.getSlots()).thenReturn(singletonList(slot));
    when(response.getTimeToNextCall()).thenReturn(timeToNextCall);
    return response;
  }

  @Test
  public void prefetch_GivenKillSwitchIsEnabled_ShouldNotCallCdbAndNotPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenKillSwitchIs(true);

    bidManager.prefetch(singletonList(adUnit));
    waitForIdleState();

    assertShouldNotCallCdbAndNotPopulateCache();
  }

  @Test
  public void prefetch_GivenRemoteConfigWithKillSwitchEnabled_WhenGettingBidShouldNotCallCdbAndNotPopulateCacheAndReturnNull()
      throws Exception {
    givenKillSwitchIs(false);
    doAnswer(answerVoid((RemoteConfigResponse response) -> {
      Boolean killSwitch = response.getKillSwitch();
      when(config.isKillSwitchEnabled()).thenReturn(killSwitch);
    })).when(config).refreshConfig(any());

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenRemoteConfigWithKillSwitchEnabled();

    bidManager.prefetch(singletonList(adUnit));
    waitForIdleState();

    clearInvocations(cache);
    clearInvocations(api);
    clearInvocations(bidLifecycleListener);

    CdbResponseSlot bid = bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);

    assertShouldNotCallCdbAndNotPopulateCache();
    assertNull(bid);
  }

  @Test
  public void prefetch_GivenAdUnitAndGlobalInformation_ShouldCallCdbWithExpectedInfo()
      throws Exception {
    callingCdb_GivenAdUnitAndGlobalInformation_ShouldCallCdbWithExpectedInfo(adUnit -> {
      bidManager.prefetch(singletonList(adUnit));
    });
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenAdUnitAndGlobalInformation_ShouldCallCdbWithExpectedInfo()
      throws Exception {
    callingCdb_GivenAdUnitAndGlobalInformation_ShouldCallCdbWithExpectedInfo(adUnit -> {
      bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    });
  }

  private void callingCdb_GivenAdUnitAndGlobalInformation_ShouldCallCdbWithExpectedInfo(
      Consumer<AdUnit> callingCdb
  ) throws Exception {
    doReturn(completedFuture("expectedUserAgent")).when(deviceInfo).getUserAgent();

    GdprData expectedGdpr = mock(GdprData.class);
    when(userPrivacyUtil.getGdprData()).thenReturn(expectedGdpr);
    when(userPrivacyUtil.getUsPrivacyOptout()).thenReturn("");
    when(userPrivacyUtil.getIabUsPrivacyString()).thenReturn("");
    when(userPrivacyUtil.getMopubConsent()).thenReturn("");

    when(buildConfigWrapper.getSdkVersion()).thenReturn("1.2.3");
    doReturn(42).when(integrationRegistry).getProfileId();
    when(contextProvider.fetchUserContext()).thenReturn(new HashMap<>());

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);

    callingCdb.accept(adUnit);
    waitForIdleState();

    Publisher expectedPublisher = Publisher.create(
        "bundle.id",
        CriteoUtil.TEST_CP_ID,
        new HashMap<>()
    );

    User expectedUser = User.create(
        advertisingInfo.getAdvertisingId(),
        null,
        null,
        null,
        new HashMap<>()
    );

    verify(api).loadCdb(argThat(cdb -> {
      assertThat(cdb.getPublisher()).isEqualTo(expectedPublisher);
      assertThat(cdb.getUser()).isEqualTo(expectedUser);
      assertThat(getRequestedAdUnits(cdb)).containsExactly(cacheAdUnit);
      assertThat(cdb.getSdkVersion()).isEqualTo("1.2.3");
      assertThat(cdb.getProfileId()).isEqualTo(42);
      assertThat(cdb.getGdprData()).isEqualTo(expectedGdpr);

      return true;
    }), eq("expectedUserAgent"));
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenNotValidAdUnit_ReturnNullAndDoNotCallCdb()
      throws Exception {
    AdUnit adUnit = givenMockedAdUnitMappingTo(null);

    CdbResponseSlot bid = bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertNull(bid);
    verify(api, never()).loadCdb(any(), any());
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenNullAdUnit_ReturnNullAndDoNotCallCdb()
      throws Exception {
    CdbResponseSlot bid = bidManager.getBidForAdUnitAndPrefetch(null, contextData);
    waitForIdleState();

    assertNull(bid);
    verify(api, never()).loadCdb(any(), any());
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenNotExpiredValidCachedBid_ReturnItAndRemoveItFromCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenNotExpiredValidCachedBid(cacheAdUnit);

    CdbResponseSlot bid = bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);

    assertEquals(slot, bid);
    verify(cache).remove(cacheAdUnit);
    assertListenerIsNotifyForBidConsumed(cacheAdUnit, bid);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenNotExpiredValidCachedBid_ShouldCallCdbAndPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenNotExpiredValidCachedBid(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(cacheAdUnit), slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenAdUnitBeingLoaded_ShouldCallCdbAndPopulateCacheOnlyOnceForThePendingCall()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);

    CdbResponse response = mock(CdbResponse.class);
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.isValid()).thenReturn(true);
    when(response.getSlots()).thenReturn(singletonList(slot));

    // We force a synchronization here to make the test deterministic.
    // Hence we can predict that the second bid manager call is done after the cdb call.
    // The test should also work in the other way (see the other "given ad unit being loaded" test).
    CountDownLatch cdbRequestHasStarted = new CountDownLatch(1);

    CountDownLatch cdbRequestIsPending = new CountDownLatch(1);
    when(api.loadCdb(any(), any())).thenAnswer(invocation -> {
      cdbRequestHasStarted.countDown();
      cdbRequestIsPending.await();
      return response;
    });

    bidManager = spy(bidManager);
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    cdbRequestHasStarted.await();
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    cdbRequestIsPending.countDown();
    waitForIdleState();

    // It is expected, with those two calls to the bid manager, that only one CDB call and only one
    // cache update is done. Indeed, the only CDB call correspond to the one mocked above with the
    // latch "slowing the network call". The only cache update is the one done after this single CDB
    // call. Hence, the second bid manager call, which happen between the CDB call and the cache
    // update should do nothing.

    InOrder inOrder = inOrder(bidManager, api, cache);
    inOrder.verify(bidManager).getBidForAdUnitAndPrefetch(adUnit, contextData);
    inOrder.verify(api).loadCdb(any(), any());
    inOrder.verify(bidManager).getBidForAdUnitAndPrefetch(adUnit, contextData);
    inOrder.verify(cache).add(slot);
    inOrder.verify(bidManager).setTimeToNextCall(anyInt());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenAdUnitBeingLoaded_ShouldCallCdbAndPopulateCacheOnlyOnceForThePendingCall2()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    // We force the CDB call to be after the second bid manager call to make the test deterministic.
    CountDownLatch bidManagerIsCalledASecondTime = givenExecutorWaitingOn();

    bidManager = spy(bidManager);
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    bidManagerIsCalledASecondTime.countDown();
    waitForIdleState();

    // It is expected, with those two calls to the bid manager, that only one CDB call and only one
    // cache update is done. Indeed, the only CDB call correspond to the one triggered by the first
    // bid manager call but run after the second bid manager call. The only cache update is the one
    // done after this single CDB call. Hence, the second bid manager call, which happen before the
    // CDB call and the cache update should do nothing.

    InOrder inOrder = inOrder(bidManager, api, cache);
    inOrder.verify(bidManager).getBidForAdUnitAndPrefetch(adUnit, contextData);
    inOrder.verify(bidManager).getBidForAdUnitAndPrefetch(adUnit, contextData);
    inOrder.verify(api, timeout(1000)).loadCdb(any(), any());
    inOrder.verify(cache).add(slot);
    inOrder.verify(bidManager).setTimeToNextCall(anyInt());
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenEmptyCache_ReturnNull() throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);

    givenNoLastBid(cacheAdUnit);

    CdbResponseSlot bid = bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);

    assertNull(bid);
    assertListenerIsNotNotifyForBidConsumed();
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenEmptyCache_ShouldCallCdbAndPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    givenNoLastBid(cacheAdUnit);

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(cacheAdUnit), slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenEmptyCacheAndApiError_ShouldNotifyListener()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);

    givenNoLastBid(cacheAdUnit);

    when(api.loadCdb(any(), any())).thenThrow(IOException.class);

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    verify(bidLifecycleListener).onCdbCallStarted(any());
    verify(bidLifecycleListener).onCdbCallFailed(any(), any());
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenClockAtFixedTime_CacheShouldContainATimestampedBid()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();
    givenMockedClockSetTo(42);

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    InOrder inOrder = inOrder(cache, slot);
    inOrder.verify(slot).setTimeOfDownload(42);
    inOrder.verify(cache).add(slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenExpiredValidCachedBid_ReturnNull() throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot internalBid = givenExpiredValidCachedBid(cacheAdUnit);

    CdbResponseSlot bid = bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);

    assertNull(bid);
    assertListenerIsNotifyForBidConsumed(cacheAdUnit, internalBid);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenExpiredValidCachedBid_ShouldCallCdbAndPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenExpiredValidCachedBid(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(cacheAdUnit), slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenNoBidFetched_ShouldNotPopulateCache() throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();
    when(slot.getCpmAsNumber()).thenReturn(0.);
    when(slot.getTtlInSeconds()).thenReturn(0);

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertNoLiveBidIsCached();
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenSilentBidFetched_ShouldPopulateCache() throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenSilentBidFetched();

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertLiveBidIsCached(slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenNotExpiredSilentModeBidCached_ReturnNullAndDoNotRemoveIt()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenNotExpiredSilentModeBidCached(cacheAdUnit);

    CdbResponseSlot bid = bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);

    assertNull(bid);
    verify(cache, never()).remove(cacheAdUnit);
    assertListenerIsNotNotifyForBidConsumed();
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenNotExpiredSilentModeBidCached_ShouldNotCallCdbAndNotPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenNotExpiredSilentModeBidCached(cacheAdUnit);

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldNotCallCdbAndNotPopulateCache();
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenExpiredSilentModeBidCached_ReturnNull()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot internalBid = givenExpiredSilentModeBidCached(cacheAdUnit);

    CdbResponseSlot bid = bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);

    assertNull(bid);
    assertListenerIsNotifyForBidConsumed(cacheAdUnit, internalBid);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenExpiredSilentModeBidCached_ShouldCallCdbAndPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenExpiredSilentModeBidCached(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(cacheAdUnit), slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenNotExpiredUserLevelSilentMode_ShouldNotCallCdbAndNotPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);

    givenMockedClockSetTo(0);
    bidManager.setTimeToNextCall(60); // Silent until 60_000 excluded
    givenMockedClockSetTo(60_000 - 1);
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldNotCallCdbAndNotPopulateCache();
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenExpiredUserLevelSilentMode_ShouldCallCdbAndPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    givenMockedClockSetTo(0);
    bidManager.setTimeToNextCall(60); // Silent until 60_000 included
    givenMockedClockSetTo(60_001);
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(cacheAdUnit), slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenCdbCallAndCachedPopulatedWithUserLevelSilentMode_UserLevelSilentModeIsUpdated()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponse cdbResponse = givenMockedCdbResponse();

    when(cdbResponse.getTimeToNextCall()).thenReturn(1337);

    bidManager = spy(bidManager);
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    verify(bidManager).setTimeToNextCall(1337);
    verify(logger).log(BiddingLogMessage.onGlobalSilentModeEnabled(1337));
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenFirstCdbCallWithoutUserLevelSilenceAndASecondFetchJustAfter_SecondFetchIsNotSilenced()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);

    // Given first CDB call without user-level silence
    CdbResponse cdbResponse = givenMockedCdbResponse();
    when(cdbResponse.getTimeToNextCall()).thenReturn(0);
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    // Count calls from this point
    clearInvocations(cache);
    clearInvocations(api);
    clearInvocations(bidLifecycleListener);
    clearInvocations(metricSendingQueueConsumer);
    clearInvocations(remoteLogSendingQueueConsumer);

    // Given a second fetch, without any clock change
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();
    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldCallCdbAndPopulateCacheOnlyOnce(singletonList(cacheAdUnit), slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenCdbGivingAnImmediateBid_ShouldPopulateCacheWithTtlSetToDefaultOne()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);

    // Immediate bid means CPM > 0, TTL = 0
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();
    when(slot.getCpmAsNumber()).thenReturn(1.);
    when(slot.getTtlInSeconds()).thenReturn(0);

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    InOrder inOrder = inOrder(cache, slot);
    inOrder.verify(slot).setTtlInSeconds(DEFAULT_TTL_IN_SECONDS);
    inOrder.verify(cache).add(slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenCdbGivingInvalidSlots_IgnoreThem() throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();

    when(slot.isValid()).thenReturn(false);

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    verify(cache, never()).add(slot);
  }

  @Test
  public void getBidForAdUnitAndPrefetch_GivenKillSwitchIsEnabledAndNoSilentMode_ShouldNotCallCdbAndNotPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenKillSwitchIs(true);

    bidManager.getBidForAdUnitAndPrefetch(adUnit, contextData);
    waitForIdleState();

    assertShouldNotCallCdbAndNotPopulateCache();
  }

  @Test
  public void fetchForLiveBidRequest_GivenKillSwitchIsEnabledAndNoSilentMode_ShouldReturnNoBid()
      throws Exception {
    givenKillSwitchIs(true);
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenMockedCdbRespondingSlot();

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    verify(metricSendingQueueConsumer, never()).sendMetricBatch();
    verify(remoteLogSendingQueueConsumer, never()).sendRemoteLogBatch();
  }

  @Test
  public void fetchForLiveBidRequest_GivenKillSwitchNotEnabledAndNoSilentModeAndInvalidAdUnit_ShouldReturnNoBid()
      throws Exception {
    givenKillSwitchIs(false);
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenMockedCdbRespondingSlot();

    bidManager = spy(bidManager);
    doReturn(null).when(bidManager).mapToCacheAdUnit(adUnit);

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    verify(metricSendingQueueConsumer, never()).sendMetricBatch();
    verify(remoteLogSendingQueueConsumer, never()).sendRemoteLogBatch();
  }

  @Test
  public void fetchForLiveBidRequest_GivenGlobalSilentModeOn_AndCacheEmpty_ShouldReturnNoBid()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    BidManager bidManagerSpy = givenGlobalSilenceMode(true);
    bidManagerSpy.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertNoLiveBidIsConsumedFromCache();
    assertNoLiveBidIsCached();
    assertShouldNotCallCdbAndNotPopulateCache();
    verify(metricSendingQueueConsumer).sendMetricBatch();
    verify(remoteLogSendingQueueConsumer).sendRemoteLogBatch();
  }

  @Test
  public void fetchForLiveBidRequest_GivenGlobalSilentModeOn_AndValidCacheEntry_ShouldReturnCachedBid()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot cdbResponseSlot = givenNotExpiredValidCachedBid(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    BidManager bidManagerSpy = givenGlobalSilenceMode(true);
    bidManagerSpy.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onBidResponse(cdbResponseSlot);
    assertLiveBidIsConsumedFromCache(cacheAdUnit, cdbResponseSlot);
    assertNoLiveBidIsCached();
    assertShouldNotCallCdbAndNotPopulateCache();
    verify(metricSendingQueueConsumer).sendMetricBatch();
    verify(remoteLogSendingQueueConsumer).sendRemoteLogBatch();
  }

  @Test
  public void fetchForLiveBidRequest_GivenGlobalSilentModeOn_AndExpiredCacheEntry_ShouldReturnNoBid()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot cdbResponseSlot = givenExpiredValidCachedBid(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    BidManager bidManagerSpy = givenGlobalSilenceMode(true);
    bidManagerSpy.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertLiveBidIsConsumedFromCache(cacheAdUnit, cdbResponseSlot);
    assertNoLiveBidIsCached();
    assertShouldNotCallCdbAndNotPopulateCache();
    verify(metricSendingQueueConsumer).sendMetricBatch();
    verify(remoteLogSendingQueueConsumer).sendRemoteLogBatch();
  }

  @Test
  public void fetchForLiveBidRequest_GivenGlobalSilentModeOn_AndSilencedCacheEntry_ShouldReturnNoBid()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenNotExpiredSilentModeBidCached(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    BidManager bidManagerSpy = givenGlobalSilenceMode(true);
    bidManagerSpy.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertNoLiveBidIsConsumedFromCache();
    assertNoLiveBidIsCached();
    assertShouldNotCallCdbAndNotPopulateCache();
    verify(metricSendingQueueConsumer).sendMetricBatch();
    verify(remoteLogSendingQueueConsumer).sendRemoteLogBatch();
  }

  @Test
  public void fetchForLiveBidRequest_GivenGlobalSilentModeOff_ShouldFetchLiveBid() throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenMockedCdbResponse();
    BidListener bidListener = mock(BidListener.class);

    BidManager bidManagerSpy = givenGlobalSilenceMode(false);
    bidManagerSpy.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    assertShouldCallCdb(singletonList(cacheAdUnit));
    verify(metricSendingQueueConsumer).sendMetricBatch();
    verify(remoteLogSendingQueueConsumer).sendRemoteLogBatch();
  }

  @Test
  public void fetchForLiveBidRequest_ReceivedTimeToNextCall_TimeBudgetRespected_ShouldUpdateGlobalSilentMode()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    CdbResponse cdbResponse = givenMockedCdbResponse();
    when(cdbResponse.getTimeToNextCall()).thenReturn(10);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    assertThat(bidManager.isGlobalSilenceEnabled()).isTrue();
  }

  @Test
  public void fetchForLiveBidRequest_ReceivedTimeToNextCall_TimeBudgetExceeded_ShouldUpdateGlobalSilentMode()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    CdbResponse cdbResponse = givenMockedCdbResponse();
    when(cdbResponse.getTimeToNextCall()).thenReturn(10);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    assertThat(bidManager.isGlobalSilenceEnabled()).isTrue();
    verify(logger).log(BiddingLogMessage.onGlobalSilentModeEnabled(10));
  }

  @Test
  public void fetchForLiveBidRequest_ReceivedNoTimeToNextCall_TimeBudgetRespected_ShouldNotUpdateGlobalSilentMode()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    CdbResponse cdbResponse = givenMockedCdbResponse();
    when(cdbResponse.getTimeToNextCall()).thenReturn(0);

    givenDuringLiveBidCall(() -> bidManager.setTimeToNextCall(42));

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    assertThat(bidManager.isGlobalSilenceEnabled()).isTrue();
  }

  @Test
  public void fetchForLiveBidRequest_ReceivedNoTimeToNextCall_TimeBudgetExpected_ShouldNotUpdateGlobalSilentMode()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    CdbResponse cdbResponse = givenMockedCdbResponse();
    when(cdbResponse.getTimeToNextCall()).thenReturn(0);

    givenDuringLiveBidCall(() -> bidManager.setTimeToNextCall(42));

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    assertThat(bidManager.isGlobalSilenceEnabled()).isTrue();
  }

  @Test
  public void fetchForLiveBidRequest_ValidBidFetched_TimeBudgetRespected_ShouldNotifyForBidAndNotPopulateCache()
      throws Exception {
    givenMockedClockSetTo(42);
    givenTimeBudgetRespectedWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);

    // Use immediate bid (ttl = 0, cpm > 0) to prove that live bidding support it
    CdbResponseSlot slot = givenMockedCdbRespondingSlot();
    when(slot.getCpmAsNumber()).thenReturn(1.);
    when(slot.getTtlInSeconds()).thenReturn(0);

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    InOrder inOrder = inOrder(bidListener, slot);
    inOrder.verify(slot).setTimeOfDownload(42);
    inOrder.verify(bidListener).onBidResponse(slot);
    assertLiveBidIsConsumedDirectly(cacheAdUnit, slot);
    assertNoLiveBidIsCached();
  }

  @Test
  public void fetchForLiveBidRequest_ValidBidCached_TimeBudgetExceeded_ShouldNotifyForConsumedBid()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenNotExpiredValidCachedBid(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onBidResponse(slot);
    assertLiveBidIsConsumedFromCache(cacheAdUnit, slot);
  }

  @Test
  public void fetchForLiveBidRequest_ValidBidFetched_ValidBidCached_TimeBudgetExceeded_ShouldNotifyForConsumedBidAndPopulateCache()
      throws Exception {
    givenMockedClockSetTo(42);
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot cachedSlot = givenNotExpiredValidCachedBid(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();
    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onBidResponse(cachedSlot);
    assertLiveBidIsCached(newSlot);
    assertLiveBidIsConsumedFromCache(cacheAdUnit, cachedSlot);

    InOrder inOrder = inOrder(cache, newSlot);
    inOrder.verify(cache).remove(cacheAdUnit);
    inOrder.verify(newSlot).setTimeOfDownload(42);
    inOrder.verify(cache).add(newSlot);
  }

  @Test
  public void fetchForLiveBidRequest_NothingCached_TimeBudgetExceeded_ShouldNotifyForNoBid()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertNoLiveBidIsCached();
    assertNoLiveBidIsConsumedFromCache();
  }

  @Test
  public void fetchForLiveBidRequest_ValidBidFetched_NothingCached_TimeBudgetExceeded_ShouldNotifyForNoBid()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();
    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertLiveBidIsCached(newSlot);
    assertNoLiveBidIsConsumedFromCache();
  }

  @Test
  public void fetchForLiveBidRequest_ExpiredBidCached_TimeBudgetExceeded_ShouldNotifyForNoBid()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot cachedSlot = givenExpiredValidCachedBid(cacheAdUnit);
    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertNoLiveBidIsCached();
    assertLiveBidIsConsumedFromCache(cacheAdUnit, cachedSlot);
  }

  @Test
  public void fetchForLiveBidRequest_ValidBidFetched_ExpiredBidCached_TimeBudgetExceeded_ShouldNotifyForNoBidAndPopulateCache()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot cachedSlot = givenExpiredValidCachedBid(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();
    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertLiveBidIsCached(newSlot);
    assertLiveBidIsConsumedFromCache(cacheAdUnit, cachedSlot);
  }

  @Test
  public void fetchForLiveBidRequest_NoBidFetched_ValidBidCached_TimeBudgetRespected_ShouldNotifyForNoBidAndNotPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenNotExpiredValidCachedBid(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();
    when(newSlot.getCpmAsNumber()).thenReturn(0.);
    when(newSlot.getTtlInSeconds()).thenReturn(0);

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertNoLiveBidIsCached();
    assertNoLiveBidIsConsumedFromCache();
  }

  @Test
  public void fetchForLiveBidRequest_NoBidFetched_ValidBidCached_TimeBudgetExceeded_ShouldNotifyForBidAndNotPopulateCache()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot cachedSlot = givenNotExpiredValidCachedBid(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();
    when(newSlot.getCpmAsNumber()).thenReturn(0.);
    when(newSlot.getTtlInSeconds()).thenReturn(0);

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onBidResponse(cachedSlot);
    assertNoLiveBidIsCached();
    assertLiveBidIsConsumedFromCache(cacheAdUnit, cachedSlot);
  }

  @Test
  public void fetchForLiveBidRequest_SilentBidCached_ShouldNotifyForNoBidAndNotFetch()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    givenNotExpiredSilentModeBidCached(cacheAdUnit);

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertNoLiveBidIsCached();
    assertNoLiveBidIsConsumedFromCache();
    assertShouldNotCallCdbAndNotPopulateCache();
  }

  @Test
  public void fetchForLiveBidRequest_ExpiredSilentBidCached_ShouldFetch() throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot slot = givenExpiredSilentModeBidCached(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onBidResponse(newSlot);
    assertNoLiveBidIsCached();
    assertShouldCallCdb(singletonList(cacheAdUnit));
    assertLiveBidIsConsumedFromCache(cacheAdUnit, slot);
  }

  @Test
  public void fetchForLiveBidRequest_SilentBidFetched_TimeBudgetRespected_NotifyForNoBidAndPopulateCache()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot newSlot = givenSilentBidFetched();

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertLiveBidIsCached(newSlot);
    assertNoLiveBidIsConsumedFromCache();
  }

  @Test
  public void fetchForLiveBidRequest_ValidBidFetched_SilentBidCachedDuringFetch_TimeBudgetRespected_NotifyForBid()
      throws Exception {
    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();
    givenDuringLiveBidCall(() -> givenNotExpiredSilentModeBidCached(cacheAdUnit));

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onBidResponse(newSlot);
    assertNoLiveBidIsCached();
    assertLiveBidIsConsumedDirectly(cacheAdUnit, newSlot);
  }

  @Test
  public void fetchForLiveBidRequest_ValidBidFetched_SilentBidCachedDuringFetch_TimeBudgetExceeded_ShouldNotifyForNoBidAndNotPopulateCache()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();
    givenDuringLiveBidCall(() -> {
      givenNotExpiredSilentModeBidCached(cacheAdUnit);
      doReturn(cacheAdUnit).when(cache).detectCacheAdUnit(newSlot);
    });

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertNoLiveBidIsCached();
    assertNoLiveBidIsConsumedFromCache();
  }

  @Test
  public void fetchForLiveBidRequest_ValidBidFetched_ExpiredSilentBidCachedDuringFetch_TimeBudgetExceeded_ShouldNotifyForNoBidAndPopulateCache()
      throws Exception {
    givenTimeBudgetExceededWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot newSlot = givenMockedCdbRespondingSlot();
    AtomicReference<CdbResponseSlot> cachedSlotRef = new AtomicReference<>();
    givenDuringLiveBidCall(() -> {
      cachedSlotRef.set(givenExpiredSilentModeBidCached(cacheAdUnit));
      doReturn(cacheAdUnit).when(cache).detectCacheAdUnit(newSlot);
    });

    BidListener bidListener = mock(BidListener.class);

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    verify(bidListener).onNoBid();
    assertLiveBidIsCached(newSlot);
    assertLiveBidIsConsumedFromCache(cacheAdUnit, cachedSlotRef.get());
  }

  @Test
  public void fetchForLiveBidRequest_GivenAnOngoingFetch_AndASecondFetchIsMade_BothFetchAreExecuteConcurrently()
      throws Exception {
    givenTimeBudgetRespectedWhenFetchingLiveBids();

    CacheAdUnit cacheAdUnit = sampleAdUnit();
    AdUnit adUnit = givenMockedAdUnitMappingTo(cacheAdUnit);
    CdbResponseSlot newSlot1 = givenMockedCdbRespondingSlot();
    CdbResponseSlot newSlot2 = givenMockedCdbRespondingSlot();

    CdbResponse response1 = givenMockedCdbResponse();
    CdbResponse response2 = givenMockedCdbResponse();
    when(response1.getSlots()).thenReturn(singletonList(newSlot1));
    when(response2.getSlots()).thenReturn(singletonList(newSlot2));

    CountDownLatch secondSlotIsReceived = new CountDownLatch(1);

    BidListener bidListener = mock(BidListener.class);
    doAnswer(answerVoid(ignored -> {
      secondSlotIsReceived.countDown();
    })).when(bidListener).onBidResponse(newSlot2);

    doAnswer(invocation -> {
      // Fetch a second bid and wait until it is received by listener
      // Situation is unblocked only if multiple concurrent calls are possible
      bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
      secondSlotIsReceived.await();

      return response1;
    }).doReturn(response2).when(api).loadCdb(any(), any());

    bidManager.getLiveBidForAdUnit(adUnit, contextData, bidListener);
    waitForIdleState();

    InOrder inOrder = inOrder(bidListener);
    inOrder.verify(bidListener).onBidResponse(newSlot2);
    inOrder.verify(bidListener).onBidResponse(newSlot1);
    inOrder.verifyNoMoreInteractions();
    assertNoLiveBidIsCached();
    assertLiveBidIsConsumedDirectly(cacheAdUnit, newSlot1);
    assertLiveBidIsConsumedDirectly(cacheAdUnit, newSlot2);
  }

  @Test
  public void setCacheAdUnits_GivenValidCdbResponseSlot_ShouldTriggerBidCached() {
    CdbResponseSlot cdbResponseSlot = givenValidCdbResponseSlot();

    bidManager.setCacheAdUnits(singletonList(cdbResponseSlot));

    verify(bidLifecycleListener).onBidCached(cdbResponseSlot);
  }

  @Test
  public void setCacheAdUnits_GivenOneValid_AndOneInvalidCdbResponseSlot_ShouldOnlyTriggerBidCachedForValid() {
    CdbResponseSlot validCdbResponseSlot = givenValidCdbResponseSlot();
    CdbResponseSlot invalidCdbResponseSlot = givenInvalidCdbResponseSlot();

    List<CdbResponseSlot> cdbResponseSlots = Arrays.asList(
        validCdbResponseSlot,
        invalidCdbResponseSlot
    );

    bidManager.setCacheAdUnits(cdbResponseSlots);

    verify(bidLifecycleListener).onBidCached(validCdbResponseSlot);
  }

  @Test
  public void setCacheAdUnits_GivenInvalidCdbResponseSlot_ShouldNotTriggerBidCached()
      throws Exception {
    CdbResponseSlot invalidCdbResponseSlot = givenInvalidCdbResponseSlot();

    List<CdbResponseSlot> cdbResponseSlots = singletonList(
        invalidCdbResponseSlot
    );

    bidManager.setCacheAdUnits(cdbResponseSlots);

    verify(bidLifecycleListener, never()).onBidCached(any());
  }

  private BidManager givenGlobalSilenceMode(boolean enabled) {
    BidManager bidManagerSpy = spy(bidManager);
    doReturn(enabled).when(bidManagerSpy).isGlobalSilenceEnabled();
    return bidManagerSpy;
  }

  private void assertShouldCallCdbAndPopulateCacheOnlyOnce(
      List<CacheAdUnit> requestedAdUnits,
      CdbResponseSlot slot
  ) throws Exception {
    verify(cache).add(slot);
    assertShouldCallCdb(requestedAdUnits);
  }

  private void assertShouldCallCdb(List<CacheAdUnit> requestedAdUnits) throws Exception {
    verify(api).loadCdb(argThat(cdb -> {
      assertEquals(requestedAdUnits, getRequestedAdUnits(cdb));
      return true;
    }), any());
    verify(bidLifecycleListener).onCdbCallStarted(any());
    verify(bidLifecycleListener).onCdbCallFinished(any(), any());
    verify(metricSendingQueueConsumer).sendMetricBatch();
    verify(remoteLogSendingQueueConsumer).sendRemoteLogBatch();
  }

  private void assertShouldNotCallCdbAndNotPopulateCache() throws Exception {
    verify(cache, never()).add(any());
    verify(api, never()).loadCdb(any(), any());
    verify(bidLifecycleListener, never()).onCdbCallStarted(any());
    verify(bidLifecycleListener, never()).onCdbCallFinished(any(), any());
    verify(bidLifecycleListener, never()).onCdbCallFailed(any(), any());
  }

  private void assertListenerIsNotifyForBidConsumed(CacheAdUnit cacheAdUnit, CdbResponseSlot bid) {
    verify(bidLifecycleListener).onBidConsumed(cacheAdUnit, bid);
  }

  private void assertListenerIsNotNotifyForBidConsumed() {
    verify(bidLifecycleListener, never()).onBidConsumed(any(), any());
  }

  private void assertLiveBidIsCached(@NonNull CdbResponseSlot cachedSlot) {
    verify(cachedSlot).setTimeOfDownload(anyLong());
    verify(cache).add(cachedSlot);
    verify(bidLifecycleListener).onBidCached(cachedSlot);
  }

  private void assertNoLiveBidIsCached() {
    verify(cache, never()).add(any());
    verify(bidLifecycleListener, never()).onBidCached(any());
  }

  private void assertLiveBidIsConsumedFromCache(@NonNull CacheAdUnit cacheAdUnit, @NonNull CdbResponseSlot cachedSlot) {
    verify(cache).remove(cacheAdUnit);
    verify(bidLifecycleListener).onBidConsumed(cacheAdUnit, cachedSlot);
  }

  private void assertNoLiveBidIsConsumedFromCache() {
    verify(cache, never()).remove(any());
    verify(bidLifecycleListener, never()).onBidConsumed(any(), any());
  }

  private void assertLiveBidIsConsumedDirectly(@NonNull CacheAdUnit cacheAdUnit, @NonNull CdbResponseSlot directSlot) {
    verify(directSlot).setTimeOfDownload(anyLong());
    verify(cache, never()).remove(any());
    verify(bidLifecycleListener).onBidConsumed(cacheAdUnit, directSlot);
  }

  private void waitForIdleState() {
    mockedDependenciesRule.waitForIdleState();
  }

  @NonNull
  private CdbResponseSlot givenNotExpiredValidCachedBid(CacheAdUnit cacheAdUnit) {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpmAsNumber()).thenReturn(1.);
    when(slot.isExpired(clock)).thenReturn(false);
    when(slot.getTtlInSeconds()).thenReturn(60);

    cache.put(cacheAdUnit, slot);
    return slot;
  }

  @NonNull
  private CdbResponseSlot givenExpiredValidCachedBid(CacheAdUnit cacheAdUnit) {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpmAsNumber()).thenReturn(1.);
    when(slot.getTtlInSeconds()).thenReturn(60);
    when(slot.isExpired(clock)).thenReturn(true);

    cache.put(cacheAdUnit, slot);
    return slot;
  }

  private void givenNoLastBid(CacheAdUnit cacheAdUnit) {
    cache.put(cacheAdUnit, null);
  }

  private void givenNotExpiredSilentModeBidCached(CacheAdUnit cacheAdUnit) {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpmAsNumber()).thenReturn(0.);
    when(slot.getTtlInSeconds()).thenReturn(60);
    when(slot.isExpired(clock)).thenReturn(false);

    cache.put(cacheAdUnit, slot);
  }

  @NonNull
  private CdbResponseSlot givenExpiredSilentModeBidCached(CacheAdUnit cacheAdUnit) {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpmAsNumber()).thenReturn(0.);
    when(slot.getTtlInSeconds()).thenReturn(60);
    when(slot.isExpired(clock)).thenReturn(true);

    cache.put(cacheAdUnit, slot);
    return slot;
  }

  @NonNull
  private CacheAdUnit sampleAdUnit() {
    return new CacheAdUnit(new AdSize(1, 1), "adUnit" + adUnitId++, CRITEO_BANNER);
  }

  private void givenKillSwitchIs(boolean isEnabled) {
    when(config.isKillSwitchEnabled()).thenReturn(isEnabled);
  }

  private void givenRemoteConfigWithKillSwitchEnabled() throws IOException {
    RemoteConfigResponse response = mock(RemoteConfigResponse.class);
    when(response.getKillSwitch()).thenReturn(true);
    when(api.loadConfig(any())).thenReturn(response);
  }

  @NonNull
  private CountDownLatch givenExecutorWaitingOn() {
    CountDownLatch waitingLatch = new CountDownLatch(1);

    doAnswer(answerVoid((Runnable command) -> {
      executor.execute(new WaitingOnRunnable(command, waitingLatch));
    })).when(executor).execute(argThat(command -> !(command instanceof WaitingOnRunnable)));

    return waitingLatch;
  }

  private static class WaitingOnRunnable implements Runnable {
    private final Runnable command;
    private final CountDownLatch waitingLatch;

    WaitingOnRunnable(Runnable command, CountDownLatch waitingLatch) {
      this.command = command;
      this.waitingLatch = waitingLatch;
    }

    @Override
    public void run() {
      try {
        waitingLatch.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      command.run();
    }
  }

  private void givenDuringLiveBidCall(@NonNull Runnable action) {
    doAnswer(invocation -> {
      action.run();
      return invocation.callRealMethod();
    }).when(liveBidRequestSender).sendLiveBidRequest(any(), any(), any());
  }

  private CdbResponseSlot givenMockedCdbRespondingSlot() throws Exception {
    CdbResponseSlot slot = spy(CdbResponseSlot.class);
    when(slot.getCpmAsNumber()).thenReturn(1337.);
    when(slot.getTtlInSeconds()).thenReturn(42);
    when(slot.getDisplayUrl()).thenReturn("http://foo.bar");
    CdbResponse response = givenMockedCdbResponse();
    when(response.getSlots()).thenReturn(singletonList(slot));
    return slot;
  }

  private CdbResponseSlot givenSilentBidFetched() throws Exception {
    CdbResponseSlot slot = spy(CdbResponseSlot.class);
    when(slot.getCpmAsNumber()).thenReturn(0.);
    when(slot.getTtlInSeconds()).thenReturn(42);
    CdbResponse response = givenMockedCdbResponse();
    when(response.getSlots()).thenReturn(singletonList(slot));
    return slot;
  }

  private CdbResponse givenMockedCdbResponse() throws Exception {
    CdbResponse response = mock(CdbResponse.class);
    when(api.loadCdb(any(), any())).thenReturn(response);
    return response;
  }

  private AdUnit givenMockedAdUnitMappingTo(CacheAdUnit toAdUnit) {
    AdUnit fromAdUnit = mock(AdUnit.class);

    doReturn(toAdUnit).when(adUnitMapper).map(fromAdUnit);
    doReturn(singletonList(singletonList(toAdUnit))).when(adUnitMapper).mapToChunks(singletonList(fromAdUnit));

    return fromAdUnit;
  }

  private void givenMockedClockSetTo(long instant) {
    when(clock.getCurrentTimeInMillis()).thenReturn(instant);
  }

  private CdbResponseSlot givenValidCdbResponseSlot() {
    CdbResponseSlot cdbResponseSlot = mock(CdbResponseSlot.class);
    when(cdbResponseSlot.isValid()).thenReturn(true);
    return cdbResponseSlot;
  }

  private CdbResponseSlot givenInvalidCdbResponseSlot() {
    CdbResponseSlot cdbResponseSlot = mock(CdbResponseSlot.class);
    when(cdbResponseSlot.isValid()).thenReturn(false);
    return cdbResponseSlot;
  }

  @NonNull
  private List<CacheAdUnit> getRequestedAdUnits(CdbRequest cdbRequest) {
    List<CacheAdUnit> cacheAdUnits = new ArrayList<>();
    cdbRequest.getSlots().forEach(slot -> cacheAdUnits.add(toAdUnit(slot)));
    return cacheAdUnits;
  }

  @NonNull
  private CacheAdUnit toAdUnit(CdbRequestSlot slot) {
    String formattedSize = slot.getSizes().iterator().next();
    String[] parts = formattedSize.split("x");
    AdSize size = new AdSize(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));

    AdUnitType adUnitType = AdUnitType.CRITEO_BANNER;
    if (slot.isInterstitial() == Boolean.TRUE) {
      adUnitType = AdUnitType.CRITEO_INTERSTITIAL;
    } else if (slot.isNativeAd() == Boolean.TRUE) {
      adUnitType = AdUnitType.CRITEO_CUSTOM_NATIVE;
    } else if (slot.isRewarded() == Boolean.TRUE) {
      adUnitType = AdUnitType.CRITEO_REWARDED;
    }

    return new CacheAdUnit(size, slot.getPlacementId(), adUnitType);
  }

  private CacheAdUnit toCacheAdUnit(RewardedAdUnit adUnit) {
    return new CacheAdUnit(
        deviceUtil.getCurrentScreenSize(),
        adUnit.getAdUnitId(),
        adUnit.getAdUnitType()
    );
  }

  private void givenTimeBudgetRespectedWhenFetchingLiveBids() {
    doNothing().when(liveBidRequestSender).scheduleTimeBudgetExceeded$publisher_sdk_debug(any());
  }

  private void givenTimeBudgetExceededWhenFetchingLiveBids() {
    doAnswer(invocation -> {
      invocation.getArgument(0, LiveCdbCallListener.class).onTimeBudgetExceeded();
      return null;
    }).when(liveBidRequestSender).scheduleTimeBudgetExceeded$publisher_sdk_debug(any());
  }
}

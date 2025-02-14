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

package com.criteo.publisher.integration;

import static com.criteo.publisher.CriteoUtil.TEST_CP_ID;
import static com.criteo.publisher.CriteoUtil.givenInitializedCriteo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import androidx.test.rule.ActivityTestRule;
import com.criteo.publisher.BidManager;
import com.criteo.publisher.Criteo;
import com.criteo.publisher.TestAdUnits;
import com.criteo.publisher.context.UserData;
import com.criteo.publisher.context.UserDataHolder;
import com.criteo.publisher.logging.Logger;
import com.criteo.publisher.mock.MockedDependenciesRule;
import com.criteo.publisher.mock.SpyBean;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.BannerAdUnit;
import com.criteo.publisher.model.CdbResponse;
import com.criteo.publisher.model.InterstitialAdUnit;
import com.criteo.publisher.network.PubSdkApi;
import com.criteo.publisher.test.activity.DummyActivity;
import com.criteo.publisher.util.BuildConfigWrapper;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CriteoFunctionalTest {

  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule().withSpiedLogger();

  @Rule
  public ActivityTestRule<DummyActivity> activityRule = new ActivityTestRule<>(
      DummyActivity.class,
      false,
      false
  );

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final BannerAdUnit validBannerAdUnit = TestAdUnits.BANNER_320_50;
  private final InterstitialAdUnit validInterstitialAdUnit = TestAdUnits.INTERSTITIAL;

  @Inject
  private Application application;

  @SpyBean
  private PubSdkApi api;

  @SpyBean
  private BuildConfigWrapper buildConfigWrapper;

  @SpyBean
  private BidManager bidManager;

  @Inject
  private UserDataHolder userDataHolder;

  @SpyBean
  private Logger logger;

  @Test
  public void getVersion_GivenNotInitializedSdk_ReturnVersion() throws Exception {
    when(buildConfigWrapper.getSdkVersion()).thenReturn("1.2.3");

    String version = Criteo.getVersion();

    assertThat(version).isEqualTo("1.2.3");
  }

  @Test
  public void getVersion_GivenInitializedSdk_ReturnVersion() throws Exception {
    when(buildConfigWrapper.getSdkVersion()).thenReturn("1.2.3");

    givenInitializedCriteo();
    String version = Criteo.getVersion();

    assertThat(version).isEqualTo("1.2.3");
  }

  @Test
  public void getVersion_GivenExceptionWhileGettingVersion_DoNotThrowAndLog() throws Exception {
    RuntimeException exception = new RuntimeException();
    when(buildConfigWrapper.getSdkVersion()).thenThrow(exception);

    String version = Criteo.getVersion();

    assertThat(version).isEmpty();
    verify(logger).log(argThat(logMessage -> logMessage.getThrowable() == exception));
  }

  @Test
  public void init_GivenPrefetchCachedBannerAndReInitWithSameBanner_CdbIsNotCallTheSecondTime()
      throws Exception {
    init_GivenPrefetchCachedAdAndReInitWithSameAdUnit_CdbIsNotCallTheSecondTime(validBannerAdUnit);
  }

  @Test
  public void init_GivenPrefetchCachedInterstitialAndReInitWithSameInterstitial_CdbIsNotCallTheSecondTime()
      throws Exception {
    init_GivenPrefetchCachedAdAndReInitWithSameAdUnit_CdbIsNotCallTheSecondTime(
        validInterstitialAdUnit);
  }

  private void init_GivenPrefetchCachedAdAndReInitWithSameAdUnit_CdbIsNotCallTheSecondTime(
      AdUnit adUnit)
      throws Exception {
    int dayTtl = 3600 * 24;

    doAnswer(invocation -> {
      Object response = invocation.callRealMethod();
      CdbResponse cdbResponse = (CdbResponse) response;
      cdbResponse.getSlots().forEach(slot -> {
        slot.setTtlInSeconds(dayTtl);
      });
      return cdbResponse;
    }).when(api).loadCdb(any(), any());

    givenInitializedCriteo(adUnit);
    waitForBids();

    new Criteo.Builder(application, TEST_CP_ID)
        .adUnits(Collections.singletonList(adUnit))
        .init();
    waitForBids();

    verify(api, times(1)).loadCdb(any(), any());
  }

  @Test
  public void init_GivenPrefetchAdUnitAndLaunchedActivity_CallConfigAndCdbAndBearcat()
      throws Exception {
    givenInitializedCriteo(validBannerAdUnit);

    activityRule.launchActivity(new Intent());

    waitForBids();

    verify(api).loadCdb(any(), any());
    verify(api).loadConfig(any());
    verify(api).postAppEvent(anyInt(), any(), any(), eq("Launch"), anyInt(), any(), any());
    verify(api).postAppEvent(anyInt(), any(), any(), eq("Active"), anyInt(), any(), any());
  }

  @Test
  public void init_WaitingForIdleState_BidManagerIsPrefetchOnMainThread() throws Exception {
    doAnswer(answerVoid((List<AdUnit> adUnits) -> {
      assertTrue(adUnits.isEmpty());
      assertSame(Thread.currentThread(), Looper.getMainLooper().getThread());
    })).when(bidManager).prefetch(any());

    givenInitializedCriteo();
    waitForBids();

    verify(bidManager).prefetch(any());
  }

  @Test
  public void init_GivenCpIdAppIdAndVersion_CallConfigWithThose() throws Exception {
    when(buildConfigWrapper.getSdkVersion()).thenReturn("1.2.3");

    givenInitializedCriteo();
    waitForBids();

    verify(api).loadConfig(argThat(request -> {
      assertEquals(TEST_CP_ID, request.getCriteoPublisherId());
      assertEquals("com.criteo.publisher.test", request.getBundleId());
      assertEquals("1.2.3", request.getSdkVersion());

      return true;
    }));
  }

  @Test
  public void setUserData_GivenUserData_StoreItForLaterUse() throws Exception {
    UserData userData = mock(UserData.class);

    givenInitializedCriteo();

    Criteo.getInstance().setUserData(userData);

    assertThat(userDataHolder.get()).isSameAs(userData);
  }

  private void waitForBids() {
    mockedDependenciesRule.waitForIdleState();
  }

}

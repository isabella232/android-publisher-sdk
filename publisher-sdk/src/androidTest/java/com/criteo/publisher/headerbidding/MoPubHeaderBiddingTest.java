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

package com.criteo.publisher.headerbidding;

import static com.criteo.publisher.concurrent.ThreadingUtil.callOnMainThreadAndWait;
import static com.criteo.publisher.util.AdUnitType.CRITEO_BANNER;
import static com.criteo.publisher.util.AdUnitType.CRITEO_INTERSTITIAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import androidx.annotation.Nullable;
import androidx.test.rule.ActivityTestRule;
import com.criteo.publisher.integration.Integration;
import com.criteo.publisher.model.CdbResponseSlot;
import com.criteo.publisher.test.activity.DummyActivity;
import com.mopub.mobileads.MoPubInterstitial;
import com.mopub.mobileads.MoPubRewardedAdManager;
import com.mopub.mobileads.MoPubView;
import org.junit.Rule;
import org.junit.Test;

public class MoPubHeaderBiddingTest {

  @Rule
  public ActivityTestRule<DummyActivity> activityRule = new ActivityTestRule<>(DummyActivity.class);

  private final MoPubHeaderBidding headerBidding = new MoPubHeaderBidding();

  @Test
  public void getIntegration_ReturnMoPubAppBidding() throws Exception {
    Integration integration = headerBidding.getIntegration();

    assertThat(integration).isEqualTo(Integration.MOPUB_APP_BIDDING);
  }

  @Test
  public void canHandle_GivenSimpleObject_ReturnFalse() throws Exception {
    boolean handling = headerBidding.canHandle(mock(Object.class));

    assertFalse(handling);
  }

  @Test
  public void canHandle_GivenMoPubView_ReturnTrue() throws Exception {
    MoPubView moPub = givenMoPubView();

    boolean handling = headerBidding.canHandle(moPub);

    assertTrue(handling);
  }

  @Test
  public void canHandle_GivenSubClassOfMoPubView_ReturnTrue() throws Exception {
    MoPubView moPub = callOnMainThreadAndWait(() -> new MoPubView(activityRule.getActivity()) {
    });

    boolean handling = headerBidding.canHandle(moPub);

    assertTrue(handling);
  }

  @Test
  public void canHandle_GivenMoPubInterstitial_ReturnTrue() throws Exception {
    MoPubInterstitial moPub = givenMoPubInterstitial();

    boolean handling = headerBidding.canHandle(moPub);

    assertTrue(handling);
  }

  @Test
  public void canHandle_GivenSubClassOfMoPubInterstitial_ReturnTrue() throws Exception {
    MoPubInterstitial moPub = callOnMainThreadAndWait(() ->
        new MoPubInterstitial(activityRule.getActivity(), "adUnit") {
        });

    boolean handling = headerBidding.canHandle(moPub);

    assertTrue(handling);
  }

  @Test
  public void canHandle_GivenMoPubRewarded_ReturnTrue() throws Exception {
    MoPubRewardedAdManager.RequestParameters moPub = givenMoPubRewarded(null);

    boolean handling = headerBidding.canHandle(moPub);

    assertTrue(handling);
  }

  @Test
  public void cleanPreviousBid_GivenNotHandledObject_DoNothing() throws Exception {
    Object builder = mock(Object.class);

    headerBidding.cleanPreviousBid(builder);

    verifyNoMoreInteractions(builder);
  }

  @Test
  public void cleanPreviousBid_GivenMoPubViewWithPreviousNonCriteoData_DoNothing()
      throws Exception {
    MoPubView moPub = givenMoPubView();
    moPub.setKeywords("previousData,that:\"shouldn't be cleaned\"");

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords).isEqualTo("previousData,that:\"shouldn't be cleaned\"");
  }

  @Test
  public void cleanPreviousBid_GivenMoPubInterstitialWithPreviousNonCriteoData_DoNothing()
      throws Exception {
    MoPubInterstitial moPub = givenMoPubInterstitial();
    moPub.setKeywords("previousData,that:\"shouldn't be cleaned\"");

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords).isEqualTo("previousData,that:\"shouldn't be cleaned\"");
  }

  @Test
  public void cleanPreviousBid_GivenMoPubViewWithOnlyCriteoData_RemoveAll() throws Exception {
    MoPubView moPub = givenMoPubView();
    moPub.setKeywords("crt_cpm:0.10,crt_displayUrl:http://url,crt_size:42x1337");

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords).isEmpty();
  }

  @Test
  public void cleanPreviousBid_GivenMoPubInterstitialWithOnlyCriteoData_RemoveAll()
      throws Exception {
    MoPubInterstitial moPub = givenMoPubInterstitial();
    moPub.setKeywords("crt_cpm:0.10,crt_displayUrl:http://url,crt_size:42x1337");

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords).isEmpty();
  }

  @Test
  public void cleanPreviousBid_GivenMoPubViewWithoutKeywords_DoNothing() throws Exception {
    MoPubView moPub = givenMoPubView();

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords).isNull();
  }

  @Test
  public void cleanPreviousBid_GivenMoPubInterstitialWithoutKeywords_DoNothing() throws Exception {
    MoPubInterstitial moPub = givenMoPubInterstitial();

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords).isNull();
  }

  @Test
  public void cleanPreviousBid_GivenMoPubViewWithPreviousCriteoData_RemoveOnlyCriteoData()
      throws Exception {
    MoPubView moPub = givenMoPubView();
    moPub.setKeywords(
        "previousData,that:\"shouldn't be cleaned\",crt_cpm:0.10,crt_displayUrl:http://url,crt_size:42x1337,crt_cpm_notcriteo,this:\"one neither\"");

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords)
        .isEqualTo(
            "previousData,that:\"shouldn't be cleaned\",crt_cpm_notcriteo,this:\"one neither\"");
  }

  @Test
  public void cleanPreviousBid_GivenMoPubInterstitialWithPreviousCriteoData_RemoveOnlyCriteoData()
      throws Exception {
    MoPubInterstitial moPub = givenMoPubInterstitial();
    moPub.setKeywords(
        "previousData,that:\"shouldn't be cleaned\",crt_cpm:0.10,crt_displayUrl:http://url,crt_size:42x1337,crt_cpm_notcriteo,this:\"one neither\"");

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords)
        .isEqualTo(
            "previousData,that:\"shouldn't be cleaned\",crt_cpm_notcriteo,this:\"one neither\"");
  }

  @Test
  public void cleanPreviousBid_GivenMoPubVideoWithPreviousCriteoData_RemoveOnlyCriteoData()
      throws Exception {
    MoPubInterstitial moPub = givenMoPubInterstitial();
    moPub.setKeywords(
        "previousData,that:\"shouldn't be cleaned\",crt_cpm:0.10,crt_displayUrl:http%3A%2F%2Furl,crt_size:42x1337,crt_format:video,crt_cpm_notcriteo,this:\"one neither\"");

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.getKeywords();

    assertThat(keywords)
        .isEqualTo(
            "previousData,that:\"shouldn't be cleaned\",crt_cpm_notcriteo,this:\"one neither\"");
  }

  @Test
  public void cleanPreviousBid_GivenMoPubRewardedWithPreviousCriteoData_RemoveOnlyCriteoData()
      throws Exception {
    MoPubRewardedAdManager.RequestParameters moPub = givenMoPubRewarded("previousData,that:\"shouldn't be cleaned\",crt_cpm:0.10,crt_displayUrl:http://url,crt_size:42x1337,crt_cpm_notcriteo,this:\"one neither\"");

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.mKeywords;

    assertThat(keywords)
        .isEqualTo(
            "previousData,that:\"shouldn't be cleaned\",crt_cpm_notcriteo,this:\"one neither\"");
  }

  @Test
  public void cleanPreviousBid_GivenMoPubRewardedWithoutKeyword_LeftItUnchanged()
      throws Exception {
    MoPubRewardedAdManager.RequestParameters moPub = givenMoPubRewarded(null);

    headerBidding.cleanPreviousBid(moPub);
    String keywords = moPub.mKeywords;

    assertThat(keywords).isNull();
  }

  @Test
  public void enrichBid_GivenNotHandledObject_DoNothing() throws Exception {
    Object builder = mock(Object.class);

    headerBidding.enrichBid(builder, CRITEO_BANNER, mock(CdbResponseSlot.class));

    verifyNoMoreInteractions(builder);
  }

  @Test
  public void enrichBid_GivenMoPubViewAndBannerBidAvailable_EnrichBuilder() throws Exception {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpm()).thenReturn("0.10");
    when(slot.getDisplayUrl()).thenReturn("http://display.url");
    when(slot.getWidth()).thenReturn(42);
    when(slot.getHeight()).thenReturn(1337);

    MoPubView moPub = givenMoPubView();
    moPub.setKeywords("previousData");
    headerBidding.enrichBid(moPub, CRITEO_BANNER, slot);
    String keywords = moPub.getKeywords();

    assertEquals("previousData,crt_cpm:0.10,crt_displayUrl:http://display.url,crt_size:42x1337", keywords);
  }

  @Test
  public void enrichBid_GivenMoPubInterstitialAndInterstitialBidAvailable_EnrichBuilder() throws Exception {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpm()).thenReturn("0.10");
    when(slot.getDisplayUrl()).thenReturn("http://display.url");

    MoPubInterstitial moPub = givenMoPubInterstitial();
    moPub.setKeywords("previousData");
    headerBidding.enrichBid(moPub, CRITEO_INTERSTITIAL, slot);
    String keywords = moPub.getKeywords();

    assertEquals("previousData,crt_cpm:0.10,crt_displayUrl:http://display.url", keywords);
  }

  @Test
  public void enrichBid_GivenMoPubVideoInterstitialAndInterstitialBidAvailable_EnrichBuilder() throws Exception {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpm()).thenReturn("0.10");
    when(slot.getDisplayUrl()).thenReturn("http://display.url");
    when(slot.isVideo()).thenReturn(true);

    MoPubInterstitial moPub = givenMoPubInterstitial();
    moPub.setKeywords("previousData");
    headerBidding.enrichBid(moPub, CRITEO_INTERSTITIAL, slot);
    String keywords = moPub.getKeywords();

    assertEquals("previousData,crt_cpm:0.10,crt_displayUrl:http%3A%2F%2Fdisplay.url,crt_format:video", keywords);
  }

  @Test
  public void enrichBid_GivenMoPubRewardedAndBidAvailable_EnrichBuilder() throws Exception {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpm()).thenReturn("0.10");
    when(slot.getDisplayUrl()).thenReturn("http://display.url");

    MoPubRewardedAdManager.RequestParameters moPub = givenMoPubRewarded("previousData");
    headerBidding.enrichBid(moPub, CRITEO_INTERSTITIAL, slot);
    String keywords = moPub.mKeywords;

    assertEquals("previousData,crt_cpm:0.10,crt_displayUrl:http://display.url", keywords);
  }

  @Test
  public void enrichBid_GivenMoPubRewardedAndVideoBidAvailable_EnrichBuilder() throws Exception {
    CdbResponseSlot slot = mock(CdbResponseSlot.class);
    when(slot.getCpm()).thenReturn("0.10");
    when(slot.getDisplayUrl()).thenReturn("http://display.url");
    when(slot.isVideo()).thenReturn(true);

    MoPubRewardedAdManager.RequestParameters moPub = givenMoPubRewarded("previousData");
    headerBidding.enrichBid(moPub, CRITEO_INTERSTITIAL, slot);
    String keywords = moPub.mKeywords;

    assertEquals("previousData,crt_cpm:0.10,crt_displayUrl:http%3A%2F%2Fdisplay.url,crt_format:video", keywords);
  }

  private MoPubView givenMoPubView() {
    return callOnMainThreadAndWait(() -> new MoPubView(activityRule.getActivity()));
  }

  private MoPubInterstitial givenMoPubInterstitial() {
    return callOnMainThreadAndWait(() ->
        new MoPubInterstitial(activityRule.getActivity(), "adUnit"));
  }

  private MoPubRewardedAdManager.RequestParameters givenMoPubRewarded(@Nullable String keywords) {
    return new MoPubRewardedAdManager.RequestParameters(keywords);
  }

}
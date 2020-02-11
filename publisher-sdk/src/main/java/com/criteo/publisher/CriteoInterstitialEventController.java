package com.criteo.publisher;

import static com.criteo.publisher.CriteoListenerCode.INVALID;
import static com.criteo.publisher.CriteoListenerCode.VALID;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.criteo.publisher.Util.AdUnitType;
import com.criteo.publisher.Util.RunOnUiThreadExecutor;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.DeviceInfo;
import com.criteo.publisher.model.Slot;
import com.criteo.publisher.model.TokenValue;
import com.criteo.publisher.model.WebViewData;
import com.criteo.publisher.tasks.CriteoInterstitialListenerCallTask;


public class CriteoInterstitialEventController {

  @Nullable
  private final CriteoInterstitialAdListener criteoInterstitialAdListener;

  @Nullable
  private final CriteoInterstitialAdDisplayListener criteoInterstitialAdDisplayListener;

  @NonNull
  private final WebViewData webViewData;

  @NonNull
  private final DeviceInfo deviceInfo;

  @NonNull
  private final Criteo criteo;

  private final RunOnUiThreadExecutor executor;

  public CriteoInterstitialEventController(
      @Nullable CriteoInterstitialAdListener listener,
      @Nullable CriteoInterstitialAdDisplayListener adDisplayListener,
      @NonNull WebViewData webViewData,
      @NonNull Criteo criteo) {
    this.criteoInterstitialAdListener = listener;
    this.criteoInterstitialAdDisplayListener = adDisplayListener;
    this.webViewData = webViewData;
    this.criteo = criteo;
    this.deviceInfo = criteo.getDeviceInfo();
    this.executor = DependencyProvider.getInstance().provideRunOnUiThreadExecutor();
  }

  public boolean isAdLoaded() {
    return webViewData.isLoaded();
  }

  public void refresh() {
    webViewData.refresh();
  }

  public String getWebViewDataContent() {
    return webViewData.getContent();
  }

  public void fetchAdAsync(@Nullable AdUnit adUnit) {
    if (webViewData.isLoading()) {
      return;
    }

    webViewData.downloadLoading();

    Slot slot = criteo.getBidForAdUnit(adUnit);

    if (slot == null) {
      notifyFor(INVALID);
      webViewData.downloadFailed();
    } else {
      notifyFor(VALID);
      fetchCreativeAsync(slot.getDisplayUrl());
    }
  }

  public void fetchAdAsync(@Nullable BidToken bidToken) {
    TokenValue tokenValue = criteo.getTokenValue(bidToken, AdUnitType.CRITEO_INTERSTITIAL);

    if (tokenValue == null) {
      notifyFor(INVALID);
    } else {
      notifyFor(VALID);
      fetchCreativeAsync(tokenValue.getDisplayUrl());
    }
  }

  private void notifyFor(@NonNull CriteoListenerCode code) {
    executor
        .executeAsync(new CriteoInterstitialListenerCallTask(criteoInterstitialAdListener, code));
  }

  @VisibleForTesting
  void fetchCreativeAsync(@NonNull String displayUrl) {
    webViewData.fillWebViewHtmlContent(
        displayUrl,
        deviceInfo,
        criteoInterstitialAdDisplayListener);
  }
}

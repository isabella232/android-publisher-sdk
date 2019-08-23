package com.criteo.pubsdk_android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import com.criteo.publisher.CriteoBannerAdListener;
import com.criteo.publisher.CriteoBannerView;
import com.criteo.publisher.CriteoErrorCode;
import com.criteo.publisher.CriteoInterstitial;
import com.criteo.publisher.CriteoInterstitialAdDisplayListener;
import com.criteo.publisher.CriteoInterstitialAdListener;
import com.criteo.publisher.model.AdSize;
import com.criteo.publisher.model.BannerAdUnit;
import com.criteo.publisher.model.InterstitialAdUnit;


public class StandaloneActivity extends AppCompatActivity {

    private static final String TAG = StandaloneActivity.class.getSimpleName();

    private Context context;
    private CriteoBannerAdListener criteoBannerAdListener;
    private CriteoInterstitialAdListener criteoInterstitialAdListener;
    private CriteoInterstitialAdDisplayListener criteoInterstitialAdDisplayListener;
    private LinearLayout adLayout;
    private CriteoBannerView criteoBannerView;
    private CriteoInterstitial criteoInterstitial;
    private Button buttonStandAloneInterstitial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stand_alone);

        adLayout = findViewById(R.id.AdLayout);
        buttonStandAloneInterstitial = findViewById(R.id.buttonStandAloneInterstitial);

        context = getApplicationContext();

        createAdListener();

        findViewById(R.id.buttonStandAloneBanner).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                BannerAdUnit bannerAdUnit = new BannerAdUnit("/140800857/Endeavour_320x50",
                        new AdSize(320, 50));
                if (criteoBannerView != null) {
                    criteoBannerView.destroy();
                }
                criteoBannerView = new CriteoBannerView(context, bannerAdUnit);
                criteoBannerView.setCriteoBannerAdListener(criteoBannerAdListener);
                Log.d(TAG, "Banner Requested");
                Bannerasync bannerasync = new Bannerasync(criteoBannerView);
                bannerasync.execute();

            }
        });

        buttonStandAloneInterstitial.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (criteoInterstitial.isAdLoaded()) {
                    criteoInterstitial.show();
                }
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (criteoBannerView != null) {
            criteoBannerView.destroy();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        buttonStandAloneInterstitial.setEnabled(false);
        interstitialAdLoad();
    }

    private void interstitialAdLoad() {
        InterstitialAdUnit interstitialAdUnit = new InterstitialAdUnit("/140800857/Endeavour_Interstitial_320x480");
        criteoInterstitial = new CriteoInterstitial(context, interstitialAdUnit);
        criteoInterstitial.setCriteoInterstitialAdListener(criteoInterstitialAdListener);
        criteoInterstitial.setCriteoInterstitialAdDisplayListener(criteoInterstitialAdDisplayListener);
        criteoInterstitial.loadAd();
    }

    private void createAdListener() {
        criteoBannerAdListener = new CriteoBannerAdListener() {
            @Override
            public void onAdLeftApplication() {
                Log.d(TAG, "Standalone - Banner onAdLeftApplication");
            }

            @Override
            public void onAdClicked() {
                Log.d(TAG, "Standalone - Banner onAdClicked");
            }

            @Override
            public void onAdOpened() {
                Log.d(TAG, "Standalone - Banner onAdOpened");
            }

            @Override
            public void onAdClosed() {
                Log.d(TAG, "Standalone - Banner onAdClosed");
            }

            @Override
            public void onAdFailedToReceive(CriteoErrorCode code) {
                Log.d(TAG, "Standalone - Banner onAdClicked, reason : " + code.toString());
            }

            @Override
            public void onAdReceived(View view) {
                Log.d(TAG, "Standalone - Banner onAdReceived");
            }

        };

        criteoInterstitialAdListener = new CriteoInterstitialAdListener() {
            @Override
            public void onAdReceived() {
                buttonStandAloneInterstitial.setEnabled(true);
                Log.d(TAG, "Standalone - Interstitial onAdReceived");
            }

            @Override
            public void onAdFailedToReceive(CriteoErrorCode code) {
                Log.d(TAG, "Standalone - Interstitial onAdFailedToReceive");
            }

            @Override
            public void onAdLeftApplication() {
                Log.d(TAG, "Standalone - Interstitial onAdLeftApplication");
            }

            @Override
            public void onAdClicked() {
                Log.d(TAG, "Standalone - Interstitial onAdClicked");
            }

            @Override
            public void onAdOpened() {
                Log.d(TAG, "Standalone - Interstitial onAdOpened");
            }

            @Override
            public void onAdClosed() {
                Log.d(TAG, "Standalone - Interstitial onAdClosed");
            }
        };

        criteoInterstitialAdDisplayListener = new CriteoInterstitialAdDisplayListener() {
            @Override
            public void onAdReadyToDisplay() {
                Log.d(TAG, "Interstitial ad called onAdReadyToDisplay");
            }

            @Override
            public void onAdFailedToDisplay(CriteoErrorCode code) {
                Log.d(TAG, "Interstitial ad called onAdFailedToDisplay");
            }
        };

    }

    private class Bannerasync extends AsyncTask<Void, Void, Void> {

        private CriteoBannerView bannerView;

        Bannerasync(CriteoBannerView bannerView) {
            this.bannerView = bannerView;
        }

        @SuppressLint("WrongThread")
        @Override
        protected Void doInBackground(Void... voids) {
            bannerView.loadAd();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            adLayout.removeAllViews();
            adLayout.addView(bannerView);
        }
    }
}

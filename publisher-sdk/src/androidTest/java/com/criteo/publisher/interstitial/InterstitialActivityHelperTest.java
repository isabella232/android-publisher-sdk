package com.criteo.publisher.interstitial;

import static com.criteo.publisher.interstitial.InterstitialActivityHelper.RESULT_RECEIVER;
import static com.criteo.publisher.interstitial.InterstitialActivityHelper.WEB_VIEW_DATA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.test.rule.ActivityTestRule;
import com.criteo.publisher.CriteoInterstitialActivity;
import com.criteo.publisher.CriteoInterstitialAdListener;
import com.criteo.publisher.util.CriteoResultReceiver;
import com.criteo.publisher.mock.MockedDependenciesRule;
import com.criteo.publisher.view.WebViewLookup;
import com.criteo.publisher.test.activity.DummyActivity;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InterstitialActivityHelperTest {

  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule();

  @Rule
  public ActivityTestRule<DummyActivity> activityRule = new ActivityTestRule<>(DummyActivity.class);

  private Context context;

  @Mock
  private CriteoInterstitialAdListener listener;

  private InterstitialActivityHelper helper;

  private WebViewLookup webViewLookup;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    context = activityRule.getActivity().getApplicationContext();
    helper = createHelper();
    webViewLookup = new WebViewLookup();
  }

  @Test
  public void isAvailable_GivenNormalIntegration_ReturnTrue() throws Exception {
    // given nothing special

    boolean isAvailable = helper.isAvailable();

    assertTrue(isAvailable);
  }

  @Test
  public void openActivity_GivenListenerAndContent_StartActivityWithThem() throws Exception {
    context = mock(Context.class);
    helper = spy(createHelper());

    doReturn(true).when(helper).isAvailable();

    when(context.getPackageName()).thenReturn("myPackage");
    ComponentName expectedComponent = new ComponentName(context, CriteoInterstitialActivity.class);

    CriteoResultReceiver expectedReceiver = mock(CriteoResultReceiver.class);
    doReturn(expectedReceiver).when(helper).createReceiver(listener);doReturn(true).when(helper).isAvailable();

    helper.openActivity("myContent", listener);

    verify(context).startActivity(argThat(intent -> {
      assertEquals(expectedComponent, intent.getComponent());
      assertEquals("myContent", intent.getStringExtra(WEB_VIEW_DATA));
      assertEquals(expectedReceiver, intent.getParcelableExtra(RESULT_RECEIVER));
      return true;
    }));
  }

  @Test
  public void openActivity_GivenTwoOpening_OpenItTwice() throws Exception {
    String html1 = openInterstitialAndGetHtml("myContent1");
    String html2 = openInterstitialAndGetHtml("myContent2");

    assertTrue(html1.contains("myContent1"));
    assertTrue(html2.contains("myContent2"));
  }

  private String openInterstitialAndGetHtml(String content) throws Exception {
    Activity activity = webViewLookup.lookForResumedActivity(() -> {
      helper.openActivity(content, listener);
    }).get();

    waitForWebViewToBeReady();

    return webViewLookup.lookForHtmlContent(activity.getWindow().getDecorView()).get();
  }

  private void waitForWebViewToBeReady() throws InterruptedException {
    Thread.sleep(500);
  }

  @NonNull
  private InterstitialActivityHelper createHelper() {
    return new InterstitialActivityHelper(context);
  }

}
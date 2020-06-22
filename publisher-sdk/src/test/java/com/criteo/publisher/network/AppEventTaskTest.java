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

package com.criteo.publisher.network;

import static com.criteo.publisher.network.AppEventTask.THROTTLE;
import static com.criteo.publisher.util.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import com.criteo.publisher.model.DeviceInfo;
import com.criteo.publisher.privacy.UserPrivacyUtil;
import com.criteo.publisher.privacy.gdpr.GdprData;
import com.criteo.publisher.util.AppEventResponseListener;
import com.criteo.publisher.util.DeviceUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AppEventTaskTest {

  private AppEventTask appEventTask;

  private JSONObject json;

  @Mock
  private AppEventResponseListener responseListener;

  @Mock
  private Context context;

  @Mock
  private DeviceUtil deviceUtil;

  @Mock
  private PubSdkApi api;

  @Mock
  private DeviceInfo deviceInfo;

  @Mock
  private UserPrivacyUtil userPrivacyUtil;

  @Mock
  private GdprData gdprData;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    appEventTask = new AppEventTask(context, responseListener, deviceUtil, api, deviceInfo, userPrivacyUtil);
    json = new JSONObject();
  }


  @Test
  public void testWithThrottleOnPostExecute() throws JSONException {
    json.put(THROTTLE, 5);
    appEventTask.onPostExecute(json);
    Mockito.verify(responseListener, Mockito.times(1)).setThrottle(json.optInt(THROTTLE, 0));
  }

  @Test
  public void testWithNullThrottleOnPostExecute() {
    appEventTask.onPostExecute(json);
    Mockito.verify(responseListener, Mockito.times(1)).setThrottle(0);
  }

  @Test
  public void testWithNullJsonOnPostExecute() {
    json = null;
    appEventTask.onPostExecute(json);
    Mockito.verify(responseListener, Mockito.times(1)).setThrottle(0);
  }

  @Test
  public void backgroundTask_GivenUserAgent_CallApiWithIt() throws Exception {
    when(deviceInfo.getUserAgent()).thenReturn(completedFuture("myUserAgent"));
    when(userPrivacyUtil.getGdprData()).thenReturn(gdprData);

    appEventTask.doInBackground("eventType");

    verify(api).postAppEvent(anyInt(), any(), any(), any(), anyInt(), eq("myUserAgent"), eq(gdprData));
  }

}
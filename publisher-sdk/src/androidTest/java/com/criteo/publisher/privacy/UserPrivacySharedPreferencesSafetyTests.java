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

package com.criteo.publisher.privacy;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import com.criteo.publisher.mock.MockBean;
import com.criteo.publisher.mock.MockedDependenciesRule;
import com.criteo.publisher.util.BuildConfigWrapper;
import com.criteo.publisher.util.SharedPreferencesFactory;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UserPrivacySharedPreferencesSafetyTests {
  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule();

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @MockBean
  private BuildConfigWrapper buildConfigWrapper;

  @Inject
  private UserPrivacyUtil userPrivacyUtil;

  @Inject
  private SharedPreferencesFactory sharedPreferencesFactory;

  @Before
  public void setUp() {
    when(buildConfigWrapper.preconditionThrowsOnException()).thenReturn(false);
  }

  @Test
  public void testRobustnessWhenAllKeysHaveBadType() {
    SharedPreferences.Editor editor = sharedPreferencesFactory.getApplication().edit();
    editor.putInt(UserPrivacyUtil.IAB_USPRIVACY_SHARED_PREFS_KEY, 1);
    editor.putInt(UserPrivacyUtil.MOPUB_CONSENT_SHARED_PREFS_KEY, 1);
    editor.putInt(UserPrivacyUtil.OPTOUT_USPRIVACY_SHARED_PREFS_KEY, 1);
    editor.apply();

    assertEquals("", userPrivacyUtil.getIabUsPrivacyString());
    assertEquals("", userPrivacyUtil.getMopubConsent());
    assertEquals("", userPrivacyUtil.getUsPrivacyOptout());
  }
}

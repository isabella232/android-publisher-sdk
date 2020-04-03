package com.criteo.publisher.degraded;

import static junit.framework.Assert.assertFalse;

import com.criteo.publisher.util.DeviceUtil;
import com.criteo.publisher.mock.MockedDependenciesRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeviceUtilDegradedTest {

  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule();

  @Before
  public void setUp() throws Exception {
    DegradedUtil.assumeIsDegraded();
  }

  @Test
  public void isVersionSupported_GivenDegradedFunctionality_ReturnsFalse() throws Exception {
    DeviceUtil deviceUtil = mockedDependenciesRule.getDependencyProvider().provideDeviceUtil();
    boolean versionSupported = deviceUtil.isVersionSupported();
    assertFalse(versionSupported);
  }
}


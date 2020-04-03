package com.criteo.publisher.csm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import com.criteo.publisher.util.BuildConfigWrapper;
import com.criteo.publisher.mock.MockedDependenciesRule;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MetricRepositoryFactoryTest {

  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule();

  private Context context;

  @Inject
  private MetricParser parser;

  private BuildConfigWrapper buildConfigWrapper;

  private MetricRepositoryFactory factory;

  @Before
  public void setUp() throws Exception {
    context = InstrumentationRegistry.getContext().getApplicationContext();

    buildConfigWrapper = spy(mockedDependenciesRule.getDependencyProvider().provideBuildConfigWrapper());
    doReturn(buildConfigWrapper).when(mockedDependenciesRule.getDependencyProvider()).provideBuildConfigWrapper();
    when(buildConfigWrapper.getCsmDirectoryName()).thenReturn("directory");

    factory = new MetricRepositoryFactory(
        context,
        parser,
        buildConfigWrapper
    );
  }

  @After
  public void tearDown() throws Exception {
    MetricDirectoryHelper.clear(new MetricDirectory(context, buildConfigWrapper, parser));
  }

  @Test
  public void create_GivenNewDirectory_RepositoryIsWorking() throws Exception {
    MetricRepository repository = factory.create();

    repository.addOrUpdateById("id", builder -> {});
    assertTrue(repository.contains("id"));
    repository.moveById("id", metric -> false);
    assertTrue(repository.contains("id"));
    repository.moveById("id", metric -> true);
    assertFalse(repository.contains("id"));
  }

  @Test
  public void addOrUpdateById_GivenTonsOfMetrics_SizeStaysAroundMemoryLimit() throws Exception {
    int smallSizeEstimationPerMetrics = 150;
    int maxSize = buildConfigWrapper.getMaxSizeOfCsmMetricsFolder();
    int requiredMetricsForOverflow = maxSize / smallSizeEstimationPerMetrics;
    int requiredMetricsForOverflowWithMargin = (int) (requiredMetricsForOverflow * 1.20);

    MetricRepository repository = factory.create();

    for (int i = 0; i < requiredMetricsForOverflowWithMargin; i++) {
      repository.addOrUpdateById("id" + i, builder -> builder
          .setCdbCallStartTimestamp(0L)
          .setCdbCallEndTimestamp(1L)
          .setElapsedTimestamp(2L));
    }

    // The last element can overflow the limit, so we are lenient (up to 1%) on the below condition.
    assertTrue(repository.getTotalSize() * 0.99 <= maxSize);
  }

}
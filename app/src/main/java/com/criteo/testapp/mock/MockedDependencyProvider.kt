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

package com.criteo.testapp.mock

import android.app.Application
import com.criteo.publisher.Criteo
import com.criteo.publisher.CriteoUtil
import com.criteo.publisher.DependencyProvider
import com.criteo.publisher.MockableDependencyProvider
import com.criteo.publisher.integration.IntegrationDetector
import com.criteo.publisher.integration.IntegrationRegistry
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

internal object MockedDependencyProvider {

  @JvmStatic
  fun prepareMock(application: Application) {
    DependencyProvider.getInstance().setApplication(application)
  }

  /**
   * Setup a new mocked dependency provider.
   *
   * This method can be called many times. Old mocks are erased and a new fresh dependency provider
   * is setup.
   *
   * The Criteo SDK is not reinitialized (without any prefetched bids) so, you should reset it if already setup to not
   * have issues in the dependency graph.
   */
  @JvmStatic
  fun startMocking(injections: MockInjection.() -> Unit) {
    // Mockito needs this property to know where to store generated classes
    val oldInstance = DependencyProvider.getInstance()
    val application = oldInstance.provideApplication()
    System.setProperty("org.mockito.android.target", application.cacheDir.path)

    MockableDependencyProvider.setInstance(null)
    val newInstance = DependencyProvider.getInstance()
    val dependencyProvider = spy(newInstance)
    MockableDependencyProvider.setInstance(dependencyProvider)

    injections(MockInjection(oldInstance, dependencyProvider))
  }

  @JvmStatic
  fun resetCriteo(action: () -> Unit) {
    val oldInstance = DependencyProvider.getInstance()
    val application = oldInstance.provideApplication()
    val criteoPublisherId = oldInstance.provideCriteoPublisherId()

    action()

    CriteoUtil.clearCriteo()
    Criteo.Builder(application, criteoPublisherId).init()
  }

  internal class MockInjection(
      val oldDependencyProvider: DependencyProvider,
      private val dependencyProvider: DependencyProvider
  ) {
    fun <T> inject(bean: T) {
      // When mocking using the doReturn API of mockito, the method call return a null. But all
      // provide* methods of the dependency provider are @NonNull so Kotlin compiler automatically
      // inject a null check, which is trigger by Mockito returning null. The stubbing call is then
      // nullify to avoid triggering this and getting an NPE.
      val stubCall: DependencyProvider? = doReturn(bean).whenever(dependencyProvider)

      when (bean) {
        is IntegrationRegistry -> stubCall?.provideIntegrationRegistry()
        is IntegrationDetector -> stubCall?.provideIntegrationDetector()
        else -> throw UnsupportedOperationException("Not supported dependency")
      }
    }
  }
}

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

object Deps {

  object Android {
    private const val agpVersion = "4.2.0"
    const val GradlePlugin = "com.android.tools.build:gradle:$agpVersion"
  }

  object AndroidX {
    const val AppCompat = "androidx.appcompat:appcompat:1.2.0"
    const val Annotations = "androidx.annotation:annotation:1.0.0"
    const val MaterialComponents = "com.google.android.material:material:1.3.0"
    const val MultiDex = "androidx.multidex:multidex:2.0.1"
    const val Preferences = "androidx.preference:preference:1.1.1"
    const val RecyclerView = "androidx.recyclerview:recyclerview:1.2.0"
    const val SupportCoreUtils = "androidx.legacy:legacy-support-core-utils:1.0.0"

    object Constraint {
      const val ConstraintLayout = "androidx.constraintlayout:constraintlayout:2.0.4"
    }

    object Test {
      const val Core = "androidx.test:core:1.4.0"
      const val Monitor = "androidx.test:monitor:1.5.0"
      const val Runner = "androidx.test:runner:1.3.0"
      const val Rules = "androidx.test:rules:1.3.0"
    }
  }

  object AssertJ {
    private const val version = "3.19.0"

    const val AssertJ = "org.assertj:assertj-core:$version"
  }

  object AutoValue {
    private const val googleVersion = "1.7.2"
    private const val gsonVersion = "1.3.0"

    const val Annotation = "com.google.auto.value:auto-value-annotations:$googleVersion"
    const val AutoValue = "com.google.auto.value:auto-value:$googleVersion"
    const val GsonRuntime = "com.ryanharter.auto.value:auto-value-gson-runtime:$gsonVersion"
    const val GsonExtension = "com.ryanharter.auto.value:auto-value-gson-extension:$gsonVersion"
    const val GsonFactory = "com.ryanharter.auto.value:auto-value-gson-factory:$gsonVersion"
  }

  object Criteo {
    object PublisherSdk {
      const val group = "com.criteo.publisher"
    }

    object Mediation {
      fun MoPub(version: String) = "com.criteo.mediation.mopub:criteo-adapter-development:$version"
      fun AdMob(version: String) = "com.criteo.mediation.google:criteo-adapter-development:$version"
    }
  }

  object Detekt {
    const val version = "1.19.0"
    const val DetektFormatting = "io.gitlab.arturbosch.detekt:detekt-formatting:$version"
  }

  object EqualsVerifier {
    private const val version = "3.6"

    const val EqualsVerifier = "nl.jqno.equalsverifier:equalsverifier:$version"
  }

  object Google {
    const val AdMob = "com.google.android.gms:play-services-ads:20.5.0"
    const val AdMob19 = "com.google.android.gms:play-services-ads:19.0.1"
    const val AdsIdentifier = "com.google.android.gms:play-services-ads-identifier:17.1.0"
  }

  object Jacoco {
    const val version = "0.8.7"
    const val Core = "org.jacoco:org.jacoco.core:$version"
  }

  object Javax {
    object Inject {
      private const val version = "1"

      const val Inject = "javax.inject:javax.inject:$version"
    }
  }

  object JUnit {
    private const val version = "4.13.2"

    const val JUnit = "junit:junit:$version"
  }

  object Json {
    private const val version = "20200518"

    const val Json = "org.json:json:$version"
  }

  object Kotlin {
    private const val version = "1.5.31"

    const val GradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:$version"
    const val AllOpenPlugin = "org.jetbrains.kotlin:kotlin-allopen:$version"
    const val JUnit = "org.jetbrains.kotlin:kotlin-test-junit:$version"
    const val Stdlib = "org.jetbrains.kotlin:kotlin-stdlib:$version"
  }

  object Mockito {
    private const val version = "3.9.0"

    const val Android = "org.mockito:mockito-android:$version"
    const val Core = "org.mockito:mockito-core:$version"
    const val Kotlin = "org.mockito.kotlin:mockito-kotlin:3.2.0"
  }

  object MoPub {
    private const val version = "5.17.0"

    const val Banner = "com.mopub:mopub-sdk-banner:$version@aar"
    const val Interstitial = "com.mopub:mopub-sdk-fullscreen:$version@aar"
    const val Native = "com.mopub:mopub-sdk-native-static:$version@aar"
  }

  object Square {
    object LeakCanary {
      private const val version = "2.7"

      const val LeakCanary = "com.squareup.leakcanary:leakcanary-android:$version"
    }

    object OkHttp {
      private const val version = "4.9.1"

      const val MockWebServer = "com.squareup.okhttp3:mockwebserver:$version"
      const val OkHttp = "com.squareup.okhttp3:okhttp:$version"
      const val OkHttpTls = "com.squareup.okhttp3:okhttp-tls:$version"
    }

    object Picasso {
      private const val version = "2.8"

      const val Picasso = "com.squareup.picasso:picasso:$version"
    }

    object Tape {
      private const val version = "1.2.3"

      const val Tape = "com.squareup:tape:$version"
    }
  }
}

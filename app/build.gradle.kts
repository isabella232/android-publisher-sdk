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

plugins {
    id("com.android.application")
    `maven-publish`
    kotlin("android")
}

androidAppModule("com.criteo.pubsdk_android")

android {
    flavorDimensions("mode")
    productFlavors {
        create("memoryLeaksHunt") {
            dimension = "mode"
            versionNameSuffix = "-memoryLeaksHunt"
        }
    }

    defaultConfig {
        multiDexEnabled = true
    }
}

// Export APK for all build types (release, staging, debug)
addPublication("Apk") {
    groupId = "com.criteo.pubsdk_android"
    artifactId = "publisher-app"
    pom.packaging = "apk"

    android.applicationVariants.all {
        outputs.all {
            artifact(outputFile) {
                classifier = buildType.name
                builtBy(assembleProvider)
            }
        }
    }
}

dependencies {
    implementation(project(":publisher-sdk"))

    implementation(Deps.Square.Picasso.Picasso)

    // FIXME EE-1097 Adapters need SDK vX, but test app needs adapters vX, so when bumping version,
    //  one dependency should be cut off.
    implementation(Deps.Criteo.Mediation.MoPub("${sdkVersion()}+")) {
        exclude(group = Deps.Criteo.PublisherSdk.group)
        isChanging = true
    }

    implementation(Deps.Criteo.Mediation.AdMob("${sdkVersion()}+")) {
        exclude(group = Deps.Criteo.PublisherSdk.group)
        isChanging = true
    }

    implementation(Deps.Kotlin.Stdlib)
    implementation(Deps.AndroidX.MultiDex)
    implementation(Deps.AndroidX.AppCompat)
    implementation(Deps.AndroidX.Constraint.ConstraintLayout)
    implementation(Deps.AndroidX.MaterialComponents)

    implementation(Deps.Google.AdMob)

    implementation(Deps.MoPub.Banner) {
        isTransitive = true
    }

    implementation(Deps.MoPub.Interstitial) {
        isTransitive = true
    }

    implementation(Deps.MoPub.Native) {
        isTransitive = true
    }

    "memoryLeaksHuntImplementation"(Deps.Square.LeakCanary.LeakCanary)
}

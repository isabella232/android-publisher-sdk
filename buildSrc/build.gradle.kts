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
  `kotlin-dsl`
}

repositories {
  google()
  mavenCentral()
  gradlePluginPortal()
  maven("https://www.jitpack.io")
}

dependencies {
  implementation(gradleApi())
  implementation("com.android.tools.build:gradle:4.2.0")
  implementation("gradle.plugin.fr.pturpin.slackpublish:slack-publish:0.2.0")
  implementation("com.banno.gordon:gordon-plugin:1.7.0")
  implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.19.0")
  implementation("io.github.gradle-nexus:publish-plugin:1.0.0")
}

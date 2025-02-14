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

import org.gradle.api.Project
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val sdkBaseVersion = "4.6.0"

private val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd.HHmm"))

fun sdkVersion(): String {
  return sdkBaseVersion
}

fun Project.isSnapshot(): Boolean {
  return properties["isRelease"] != "true"
}

fun Project.sdkPublicationVersion(): String {
  val sdkVersion = sdkVersion()
  return if (isSnapshot()) {
    "$sdkVersion-$timestamp"
  } else {
    sdkVersion
  }

}
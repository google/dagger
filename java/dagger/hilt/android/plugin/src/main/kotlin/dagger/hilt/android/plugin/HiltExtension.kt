/*
 * Copyright (C) 2020 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.hilt.android.plugin

/**
 * Configuration options for Hilt Gradle Plugin
 */
interface HiltExtension {
  /**
   * If set to `true`, Hilt will register a transform task that will rewrite `@AndroidEntryPoint` annotated classes
   * before the unit tests run. You should enable this option if you are running Robolectric UI tests as part of your
   * JUnit tests.
   */
  var enableForUnitTests: Boolean
}

open class HiltExtensionImpl : HiltExtension {
  override var enableForUnitTests: Boolean = false
}

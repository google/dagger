/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android;

/**
 * Utility methods for tests that verify which generated component is used to inject the test class.
 */
public abstract class UsesComponentHelper {

  public static String defaultComponentName() {
    return "dagger.hilt.android.internal.testing.root."
        + "DaggerDefault_HiltComponents_SingletonC$SingletonCImpl";
  }

  /**
   * Returns the name of a component that cannot use the default component. Does not handle deduping
   * if test class names clash.
   */
  public static String perTestComponentName(Object testInstance) {
    return "dagger.hilt.android.internal.testing.root.Dagger"
        + testInstance.getClass().getSimpleName()
        + "_HiltComponents_SingletonC$SingletonCImpl";
  }

  /**
   * Returns the name of a component that cannot use the default component, including the expected
   * prefix applied by Hilt to dedupe clashing class names.
   */
  public static String perTestComponentNameWithDedupePrefix(
      String expectedPrefix, Object testInstance) {
    return "dagger.hilt.android.internal.testing.root.Dagger"
        + expectedPrefix
        + testInstance.getClass().getSimpleName()
        + "_HiltComponents_SingletonC$SingletonCImpl";
  }

  private UsesComponentHelper() {}
}

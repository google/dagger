/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.nullables;

import javax.inject.Inject;
import javax.inject.Provider;

class NullFoo {
  final String string;
  final Provider<String> stringProvider;
  final Number number;
  final Provider<Number> numberProvider;

  @Inject
  NullFoo(@Nullable String string,
      Provider<String> stringProvider,
      Number number,
      Provider<Number> numberProvider) {
    this.string = string;
    this.stringProvider = stringProvider;
    this.number = number;
    this.numberProvider = numberProvider;
  }

  String methodInjectedString;
  Provider<String> methodInjectedStringProvider;
  Number methodInjectedNumber;
  Provider<Number> methodInjectedNumberProvider;
  @Inject void inject(@Nullable String string,
      Provider<String> stringProvider,
      Number number,
      Provider<Number> numberProvider) {
    this.methodInjectedString = string;
    this.methodInjectedStringProvider = stringProvider;
    this.methodInjectedNumber = number;
    this.methodInjectedNumberProvider = numberProvider;
  }

  @Nullable @Inject String fieldInjectedString;
  @Inject Provider<String> fieldInjectedStringProvider;
  @Inject Number fieldInjectedNumber;
  @Inject Provider<Number> fieldInjectedNumberProvider;
}

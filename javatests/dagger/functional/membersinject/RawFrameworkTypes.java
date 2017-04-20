/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional.membersinject;

import dagger.Lazy;
import dagger.MembersInjector;
import javax.inject.Provider;

// https://github.com/google/dagger/issues/419
@SuppressWarnings({"rawtypes", "unused"})
class RawFrameworkTypes {
  void nonInjectMethodWithARawProvider(Provider rawProvider) {}
  void nonInjectMethodWithARawLazy(Lazy rawLazy) {}
  void nonInjectMethodWithARawMembersInjector(MembersInjector rawMembersInjector) {}
}

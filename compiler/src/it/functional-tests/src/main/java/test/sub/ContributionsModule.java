/*
 * Copyright (C) 2015 Google, Inc.
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
package test.sub;

import dagger2.Module;
import dagger2.Provides;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static dagger2.Provides.Type.SET;
import static dagger2.Provides.Type.SET_VALUES;

@Module
public final class ContributionsModule {
  @Provides(type = SET) int contributeAnInt(double doubleDependency) {
    return 1742;
  }

  @Provides(type = SET) int contributeAnotherInt() {
    return 832;
  }

  @Provides(type = SET_VALUES) Set<Integer> contributeSomeInts() {
    return Collections.unmodifiableSet(new LinkedHashSet<Integer>(Arrays.asList(-1, -90, -17)));
  }
}

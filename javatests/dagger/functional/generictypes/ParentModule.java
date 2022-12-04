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

package dagger.functional.generictypes;

import dagger.Module;
import dagger.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Module
abstract class ParentModule<T1 extends Number & Comparable<T1>, T2, T3 extends Iterable<T1>> {
  @Provides Iterable<T1> provideIterableOfAWithC(T1 t1, T3 c) {
    List<T1> list = new ArrayList<>();
    list.add(t1);
    for (T1 elt : c) {
      list.add(elt);
    }
    return list;
  }

  @Provides static char provideNonGenericBindingInParameterizedModule() {
    return 'c';
  }

  @Provides
  static List<Set<String>> provideStaticGenericTypeWithNoTypeParametersInParameterizedModule() {
    return new ArrayList<>();
  }
}

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

import java.util.List;
import javax.inject.Inject;


class BoundedGenerics<T1 extends Number & Comparable<? super T1>,
      T2 extends List<? extends CharSequence>,
      T3 extends List<? super String>,
      T4 extends T1,
      T5 extends Iterable<T4>> {

  final T1 t1;
  final T2 t2;
  final T3 t3;
  final T4 t4;
  final T5 t5;

  @Inject BoundedGenerics(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
    this.t1 = t1;
    this.t2 = t2;
    this.t3 = t3;
    this.t4 = t4;
    this.t5 = t5;
  }
}

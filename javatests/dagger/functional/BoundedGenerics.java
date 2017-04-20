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

package dagger.functional;

import java.util.List;
import javax.inject.Inject;

class BoundedGenerics<A extends Number & Comparable<? super A>, 
      B extends List<? extends CharSequence>,
      C extends List<? super String>,
      D extends A,
      E extends Iterable<D>> {
  
  final A a;
  final B b;
  final C c;
  final D d;
  final E e;
  
  @Inject BoundedGenerics(A a, B b, C c, D d, E e) {
    this.a = a;
    this.b = b;
    this.c = c;
    this.d = d;
    this.e = e;
  }

}

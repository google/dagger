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

import dagger.Lazy;
import javax.inject.Inject;
import javax.inject.Provider;

class ComplexGenerics {
  
  final Generic2<Generic<A>> g2ga;
  final Lazy<Generic2<Generic<A>>> g2gaLazy;
  final Provider<Generic2<Generic<A>>> g2gaProvider;
  final Generic2<Generic<B>> g2gb;
  final Lazy<Generic2<Generic<B>>> g2gbLazy;
  final Provider<Generic2<Generic<B>>> g2gbProvider;
  final Generic2<A> g2a;
  final Generic<Generic2<A>> gg2a;
  final Generic<Generic2<B>> gg2b;
  
  @Inject ComplexGenerics(
      Generic2<Generic<A>> g2ga,
      Lazy<Generic2<Generic<A>>> g2gaLazy,
      Provider<Generic2<Generic<A>>> g2gaProvider,
      Generic2<Generic<B>> g2gb,
      Lazy<Generic2<Generic<B>>> g2gbLazy,
      Provider<Generic2<Generic<B>>> g2gbProvider,
      Generic2<A> g2a,
      Generic<Generic2<A>> gg2a,
      Generic<Generic2<B>> gg2b) {
    this.g2ga = g2ga;
    this.g2gaLazy = g2gaLazy;
    this.g2gaProvider = g2gaProvider;
    this.g2gb = g2gb;
    this.g2gbLazy = g2gbLazy;
    this.g2gbProvider = g2gbProvider;
    this.g2a = g2a;
    this.gg2a = gg2a;
    this.gg2b = gg2b;
  }
}

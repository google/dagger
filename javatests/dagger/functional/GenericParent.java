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

import javax.inject.Inject;
import javax.inject.Provider;

class GenericParent<X, Y> {
  
  Provider<X> registeredX;
  Y registeredY;
  B registeredB;
  Parameterized<Y> registerParameterizedOfY;
  
  @Inject GenericParent() {}
  
  @Inject Provider<X> x;
  @Inject Y y;
  @Inject B b;
  @Inject Parameterized<X> parameterizedOfX;

  @Inject
  void registerX(Provider<X> x) {
    this.registeredX = x;
  }
  @Inject void registerY(Y y) { this.registeredY = y; }
  @Inject void registerB(B b) { this.registeredB = b; }
  @Inject void registerParameterizedOfY(Parameterized<Y> parameterizedOfY) {
    this.registerParameterizedOfY = parameterizedOfY;
  }

  static class Parameterized<P> {
    @Inject P p;

    @Inject Parameterized() {}
  }
}

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

package library1;

import static library2.MyTransitiveAnnotation.VALUE;

import javax.inject.Inject;
import library2.MyTransitiveAnnotation;

/** A baseclass for {@link Foo}. */
@MyTransitiveAnnotation(VALUE)
public class FooBase {
  @MyTransitiveAnnotation(VALUE)
  int baseNonDaggerField;

  // @MyTransitiveAnnotation(VALUE): Not supported on inject-method parameters yet.
  @Inject int baseDaggerField;

  @MyTransitiveAnnotation(VALUE)
  FooBase(@MyTransitiveAnnotation(VALUE) String str) {}

  @MyTransitiveAnnotation(VALUE)
  @Inject
  FooBase(@MyTransitiveAnnotation(VALUE) int i) {}

  @MyTransitiveAnnotation(VALUE)
  void baseNonDaggerMethod(@MyTransitiveAnnotation(VALUE) int i) {}

  @MyTransitiveAnnotation(VALUE)
  @Inject
  void baseDaggerMethod(
      // @MyTransitiveAnnotation(VALUE): Not supported on inject-method parameters yet.
      int i) {}
}

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

import static library2.MyTransitiveBaseAnnotation.VALUE;

import javax.inject.Inject;
import library2.MySimpleTransitiveBaseAnnotation;
import library2.MyTransitiveBaseAnnotation;

/** A baseclass for {@link Foo}. */
@MySimpleTransitiveBaseAnnotation
@MyTransitiveBaseAnnotation(VALUE)
public class FooBase {
  @MySimpleTransitiveBaseAnnotation
  @MyTransitiveBaseAnnotation(VALUE)
  int baseNonDaggerField;

  @MySimpleTransitiveBaseAnnotation
  @MyTransitiveBaseAnnotation(VALUE)
  @Inject int baseDaggerField;

  @MySimpleTransitiveBaseAnnotation
  @MyTransitiveBaseAnnotation(VALUE)
  FooBase(@MySimpleTransitiveBaseAnnotation @MyTransitiveBaseAnnotation(VALUE) String str) {}

  @MySimpleTransitiveBaseAnnotation
  @MyTransitiveBaseAnnotation(VALUE)
  @Inject
  FooBase(@MySimpleTransitiveBaseAnnotation @MyTransitiveBaseAnnotation(VALUE) int i) {}

  @MySimpleTransitiveBaseAnnotation
  @MyTransitiveBaseAnnotation(VALUE)
  void baseNonDaggerMethod(
      @MySimpleTransitiveBaseAnnotation @MyTransitiveBaseAnnotation(VALUE) int i) {}

  @MySimpleTransitiveBaseAnnotation
  @MyTransitiveBaseAnnotation(VALUE)
  @Inject
  void baseDaggerMethod(
      @MySimpleTransitiveBaseAnnotation @MyTransitiveBaseAnnotation(VALUE) int i) {}
}

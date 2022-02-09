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

import javax.inject.Inject;
import library2.MyTransitiveBaseAnnotation;
import library2.MyTransitiveType;

/** A baseclass for {@link Foo}. */
@MyTransitiveBaseAnnotation
@MyAnnotation(MyTransitiveType.VALUE)
public class FooBase {
  @MyTransitiveBaseAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  int baseNonDaggerField;

  @MyTransitiveBaseAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @Inject
  @MyQualifier
  Dep baseDaggerField;

  @MyTransitiveBaseAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  FooBase(@MyTransitiveBaseAnnotation @MyAnnotation(MyTransitiveType.VALUE) String str) {}

  @MyTransitiveBaseAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @Inject
  FooBase(
      @MyTransitiveBaseAnnotation
      @MyAnnotation(MyTransitiveType.VALUE)
      @MyQualifier
      Dep dep) {}

  @MyTransitiveBaseAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  void baseNonDaggerMethod(
      @MyTransitiveBaseAnnotation @MyAnnotation(MyTransitiveType.VALUE) int i) {}

  @MyTransitiveBaseAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @Inject
  void baseDaggerMethod(
      @MyTransitiveBaseAnnotation @MyAnnotation(MyTransitiveType.VALUE) @MyQualifier Dep dep) {}
}

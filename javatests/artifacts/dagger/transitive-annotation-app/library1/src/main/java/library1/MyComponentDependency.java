/*
 * Copyright (C) 2022 The Dagger Authors.
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

import library2.MyTransitiveAnnotation;
import library2.MyTransitiveType;

/**
 * A class used to test that Dagger won't fail on unresolvable transitive types used in non-dagger
 * related elements and annotations.
 */
// TODO(b/219587431): Support @MyTransitiveAnnotation (We shouldn't need scope/qualifier here).
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType.class)
public final class MyComponentDependency {
  private final MyComponentDependencyBinding qualifiedMyComponentDependencyBinding =
      new MyComponentDependencyBinding();
  private final MyComponentDependencyBinding unqualifiedMyComponentDependencyBinding =
      new MyComponentDependencyBinding();

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public MyComponentDependency() {}

  // @MyTransitiveAnnotation cannot be used here.
  @MyQualifier
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public MyComponentDependencyBinding qualifiedMyComponentDependencyBinding() {
    return qualifiedMyComponentDependencyBinding;
  }

  // @MyTransitiveAnnotation cannot be used here.
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public MyComponentDependencyBinding unqualifiedMyComponentDependencyBinding() {
    return unqualifiedMyComponentDependencyBinding;
  }

  // Non-dagger code

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public MyTransitiveType nonDaggerField = null;

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public static MyTransitiveType nonDaggerStaticField = null;

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public MyTransitiveType nonDaggerMethod(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          MyTransitiveType nonDaggerParameter) {
    return nonDaggerParameter;
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public static MyTransitiveType nonDaggerStaticMethod(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          MyTransitiveType nonDaggerParameter) {
    return nonDaggerParameter;
  }
}

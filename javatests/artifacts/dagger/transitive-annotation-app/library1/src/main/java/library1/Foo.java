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
import javax.inject.Singleton;
import library2.MyTransitiveAnnotation;
import library2.MyTransitiveType;

/**
 * A class used to test that Dagger won't fail when non-dagger related annotations cannot be
 * resolved.
 *
 * <p>During the compilation of {@code :app}, {@link MyTransitiveAnnotation} will no longer be on
 * the classpath. In most cases, Dagger shouldn't care that the annotation isn't on the classpath
 */
@Singleton
@MyTransitiveAnnotation
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType.class)
public final class Foo extends FooBase {
  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  int nonDaggerField;

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Inject
  @MyQualifier
  Dep daggerField;

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  Foo(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          String str) {
    super(str);
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Inject
  Foo(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          @MyQualifier
          Dep dep) {
    super(dep);
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  void nonDaggerMethod(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          int i) {}

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Inject
  void daggerMethod(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          @MyQualifier
          Dep dep) {}
}

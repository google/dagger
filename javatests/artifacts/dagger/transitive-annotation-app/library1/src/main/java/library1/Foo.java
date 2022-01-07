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
import javax.inject.Singleton;
import library2.MyTransitiveAnnotation;

/**
 * A class used to test that Dagger won't fail when non-dagger related annotations cannot be
 * resolved.
 *
 * <p>During the compilation of {@code :app}, {@link MyTransitiveAnnotation} will no longer be on
 * the classpath. In most cases, Dagger shouldn't care that the annotation isn't on the classpath
 */
@Singleton
@MyTransitiveAnnotation(VALUE)
public final class Foo extends FooBase {
  @MyTransitiveAnnotation(VALUE)
  int nonDaggerField;

  // @MyTransitiveAnnotation(VALUE): Not supported on inject-fields yet.
  @Inject int daggerField;

  @MyTransitiveAnnotation(VALUE)
  Foo(@MyTransitiveAnnotation(VALUE) String str) {
    super(str);
  }

  @MyTransitiveAnnotation(VALUE)
  @Inject
  Foo(
      // @MyTransitiveAnnotation(VALUE): Not supported on inject-constructor parameters yet.
      int i) {
    super(i);
  }

  @MyTransitiveAnnotation(VALUE)
  void nonDaggerMethod(@MyTransitiveAnnotation(VALUE) int i) {}

  @MyTransitiveAnnotation(VALUE)
  @Inject
  void daggerMethod(
      // @MyTransitiveAnnotation(VALUE): Not supported on inject-method parameters yet.
      int i) {}
}

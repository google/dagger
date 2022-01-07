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
import library2.MySimpleTransitiveAnnotation;
import library2.MyTransitiveAnnotation;

/**
 * A class used to test that Dagger won't fail when non-dagger related annotations cannot be
 * resolved.
 *
 * <p>During the compilation of {@code :app}, {@link MyTransitiveAnnotation} will no longer be on
 * the classpath. In most cases, Dagger shouldn't care that the annotation isn't on the classpath
 */
@Singleton
@MySimpleTransitiveAnnotation
@MyTransitiveAnnotation(VALUE)
public final class Foo extends FooBase {
  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  int nonDaggerField;

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Inject int daggerField;

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  Foo(@MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) String str) {
    super(str);
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Inject
  Foo(@MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) int i) {
    super(i);
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  void nonDaggerMethod(@MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) int i) {}

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Inject
  void daggerMethod(@MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) int i) {}
}

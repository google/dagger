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

import static library2.MyTransitiveAnnotation.VALUE;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import javax.inject.Inject;
import library2.MySimpleTransitiveAnnotation;
import library2.MyTransitiveAnnotation;

/**
 * A class used to test that Dagger won't fail when non-dagger related annotations cannot be
 * resolved.
 *
 * <p>During the compilation of {@code :app}, {@link MyTransitiveAnnotation} will no longer be on
 * the classpath. In most cases, Dagger shouldn't care that the annotation isn't on the classpath
 */
@MySimpleTransitiveAnnotation
@MyTransitiveAnnotation(VALUE)
public final class AssistedFoo extends FooBase {
  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  int nonDaggerField;

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Inject
  @MyQualifier
  Dep daggerField;

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  AssistedFoo(@MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) String str) {
    super(str);
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @AssistedInject
  AssistedFoo(
      @MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) @Assisted int i,
      @MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) @MyQualifier Dep dep) {
    super(dep);
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  void nonDaggerMethod(@MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) int i) {}

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Inject
  void daggerMethod(
      @MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) @MyQualifier Dep dep) {}

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @AssistedFactory
  public interface Factory {
    @MySimpleTransitiveAnnotation
    @MyTransitiveAnnotation(VALUE)
    AssistedFoo create(@MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) int i);
  }
}

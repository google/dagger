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

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import javax.inject.Inject;
import library2.MyTransitiveAnnotation;
import library2.MyTransitiveType;

/**
 * A class used to test that Dagger won't fail when non-dagger related annotations cannot be
 * resolved.
 *
 * <p>During the compilation of {@code :app}, {@link MyTransitiveAnnotation} will no longer be on
 * the classpath. In most cases, Dagger shouldn't care that the annotation isn't on the classpath
 */
@MyTransitiveAnnotation
@MyAnnotation(MyTransitiveType.VALUE)
public final class AssistedFoo extends FooBase {
  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  int nonDaggerField;

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @Inject
  @MyQualifier
  Dep daggerField;

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  AssistedFoo(@MyTransitiveAnnotation @MyAnnotation(MyTransitiveType.VALUE) String str) {
    super(str);
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @AssistedInject
  AssistedFoo(
      @MyTransitiveAnnotation @MyAnnotation(MyTransitiveType.VALUE) @Assisted int i,
      @MyTransitiveAnnotation @MyAnnotation(MyTransitiveType.VALUE) @MyQualifier Dep dep) {
    super(dep);
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  void nonDaggerMethod(@MyTransitiveAnnotation @MyAnnotation(MyTransitiveType.VALUE) int i) {}

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @Inject
  void daggerMethod(
      @MyTransitiveAnnotation @MyAnnotation(MyTransitiveType.VALUE) @MyQualifier Dep dep) {}

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @AssistedFactory
  public interface Factory {
    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    AssistedFoo create(@MyTransitiveAnnotation @MyAnnotation(MyTransitiveType.VALUE) int i);
  }
}

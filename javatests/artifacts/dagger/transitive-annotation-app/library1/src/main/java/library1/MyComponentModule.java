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

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
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
@MySimpleTransitiveAnnotation
@MyTransitiveAnnotation(VALUE)
@Module(includes = {
  MyComponentModule.MyAbstractModule.class
})
public final class MyComponentModule {
  // Define bindings for each configuration: Scoped/Unscoped, Qualified/UnQualified, Provides/Binds
  public static class ScopedQualifiedBindsType {}
  public static final class ScopedQualifiedProvidesType extends ScopedQualifiedBindsType {}
  public static class ScopedUnqualifiedBindsType {}
  public static final class ScopedUnqualifiedProvidesType extends ScopedUnqualifiedBindsType {}
  public static class UnscopedQualifiedBindsType {}
  public static final class UnscopedQualifiedProvidesType extends UnscopedQualifiedBindsType {}
  public static class UnscopedUnqualifiedBindsType {}
  public static final class UnscopedUnqualifiedProvidesType extends UnscopedUnqualifiedBindsType {}

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Provides
  @Singleton
  @MyQualifier
  ScopedQualifiedProvidesType scopedQualifiedProvidesType(
      @MyQualifier @MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) Dep dep) {
    return new ScopedQualifiedProvidesType();
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Provides
  @Singleton
  ScopedUnqualifiedProvidesType scopedUnqualifiedProvidesType(
      @MyQualifier @MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) Dep dep) {
    return new ScopedUnqualifiedProvidesType();
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Provides
  @MyQualifier
  UnscopedQualifiedProvidesType unscopedQualifiedProvidesType(
      @MyQualifier @MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) Dep dep) {
    return new UnscopedQualifiedProvidesType();
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Provides
  UnscopedUnqualifiedProvidesType unscopedUnqualifiedProvidesType(
      @MyQualifier @MySimpleTransitiveAnnotation @MyTransitiveAnnotation(VALUE) Dep dep) {
    return new UnscopedUnqualifiedProvidesType();
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Module
  interface MyAbstractModule {
    // @MySimpleTransitiveAnnotation: Not yet supported
    // @MyTransitiveAnnotation(VALUE): Not yet supported
    @Binds
    @Singleton
    @MyQualifier
    ScopedQualifiedBindsType scopedQualifiedBindsType(
        @MyQualifier
        // @MySimpleTransitiveAnnotation: Not yet supported
        // @MyTransitiveAnnotation(VALUE): Not yet supported
        ScopedQualifiedProvidesType scopedQualifiedProvidesType);

    // @MySimpleTransitiveAnnotation: Not yet supported
    // @MyTransitiveAnnotation(VALUE): Not yet supported
    @Binds
    @Singleton
    ScopedUnqualifiedBindsType scopedUnqualifiedBindsType(
        // @MySimpleTransitiveAnnotation: Not yet supported
        // @MyTransitiveAnnotation(VALUE): Not yet supported
        ScopedUnqualifiedProvidesType scopedUnqualifiedProvidesType);

    // @MySimpleTransitiveAnnotation: Not yet supported
    // @MyTransitiveAnnotation(VALUE): Not yet supported
    @Binds
    @MyQualifier
    UnscopedQualifiedBindsType unscopedQualifiedBindsType(
        @MyQualifier
        // @MySimpleTransitiveAnnotation: Not yet supported
        // @MyTransitiveAnnotation(VALUE): Not yet supported
        UnscopedQualifiedProvidesType unscopedQualifiedProvidesType);

    // @MySimpleTransitiveAnnotation: Not yet supported
    // @MyTransitiveAnnotation(VALUE): Not yet supported
    @Binds
    UnscopedUnqualifiedBindsType unscopedUnqualifiedBindsType(
        // @MySimpleTransitiveAnnotation: Not yet supported
        // @MyTransitiveAnnotation(VALUE): Not yet supported
        UnscopedUnqualifiedProvidesType unscopedUnqualifiedProvidesType);
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Provides
  @MyQualifier
  Dep provideQualifiedDep() {
    return dep;
  }

  // Provide an unqualified Dep to ensure that if we accidentally drop the qualifier
  // we'll get a runtime exception.
  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Provides
  Dep provideDep() {
    throw new UnsupportedOperationException();
  }

  // Non-Dagger elements

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  private Dep dep;

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  public MyComponentModule(
      @MySimpleTransitiveAnnotation
      @MyTransitiveAnnotation(VALUE)
      Dep dep) {
    this.dep = dep;
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  String nonDaggerMethod(
      @MySimpleTransitiveAnnotation
      @MyTransitiveAnnotation(VALUE)
      String str) {
    return str;
  }


  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  static String nonDaggerStaticMethod(
      @MySimpleTransitiveAnnotation
      @MyTransitiveAnnotation(VALUE)
      String str) {
    return str;
  }
}

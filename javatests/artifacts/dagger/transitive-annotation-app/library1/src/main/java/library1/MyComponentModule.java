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

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
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
@MyTransitiveAnnotation
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType.class)
@Module(includes = {MyComponentModule.MyAbstractModule.class})
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

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Provides
  @Singleton
  @MyQualifier
  ScopedQualifiedProvidesType scopedQualifiedProvidesType(
      @MyQualifier
          @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          Dep dep) {
    return new ScopedQualifiedProvidesType();
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Provides
  @Singleton
  ScopedUnqualifiedProvidesType scopedUnqualifiedProvidesType(
      @MyQualifier
          @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          Dep dep) {
    return new ScopedUnqualifiedProvidesType();
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Provides
  @MyQualifier
  UnscopedQualifiedProvidesType unscopedQualifiedProvidesType(
      @MyQualifier
          @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          Dep dep) {
    return new UnscopedQualifiedProvidesType();
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Provides
  UnscopedUnqualifiedProvidesType unscopedUnqualifiedProvidesType(
      @MyQualifier
          @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          Dep dep) {
    return new UnscopedUnqualifiedProvidesType();
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Module
  interface MyAbstractModule {
    // @MyTransitiveAnnotation: Not yet supported
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    @Binds
    @Singleton
    @MyQualifier
    ScopedQualifiedBindsType scopedQualifiedBindsType(
        // @MyTransitiveAnnotation: Not yet supported
        @MyQualifier
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType.class)
            ScopedQualifiedProvidesType scopedQualifiedProvidesType);

    // @MyTransitiveAnnotation: Not yet supported
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    @Binds
    @Singleton
    ScopedUnqualifiedBindsType scopedUnqualifiedBindsType(
        // @MyTransitiveAnnotation: Not yet supported
        @MyAnnotation(MyTransitiveType.VALUE) @MyOtherAnnotation(MyTransitiveType.class)
            ScopedUnqualifiedProvidesType scopedUnqualifiedProvidesType);

    // @MyTransitiveAnnotation: Not yet supported
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    @Binds
    @MyQualifier
    UnscopedQualifiedBindsType unscopedQualifiedBindsType(
        // @MyTransitiveAnnotation: Not yet supported
        @MyQualifier
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType.class)
            UnscopedQualifiedProvidesType unscopedQualifiedProvidesType);

    // @MyTransitiveAnnotation: Not yet supported
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    @Binds
    UnscopedUnqualifiedBindsType unscopedUnqualifiedBindsType(
        // @MyTransitiveAnnotation: Not yet supported
        @MyAnnotation(MyTransitiveType.VALUE) @MyOtherAnnotation(MyTransitiveType.class)
            UnscopedUnqualifiedProvidesType unscopedUnqualifiedProvidesType);
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Provides
  @MyQualifier
  Dep provideQualifiedDep() {
    return dep;
  }

  // Provide an unqualified Dep to ensure that if we accidentally drop the qualifier
  // we'll get a runtime exception.
  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Provides
  Dep provideDep() {
    throw new UnsupportedOperationException();
  }

  // Non-Dagger elements

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  private Dep dep;

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public MyComponentModule(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          Dep dep) {
    this.dep = dep;
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  String nonDaggerMethod(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          String str) {
    return str;
  }

  @MyTransitiveAnnotation
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  static String nonDaggerStaticMethod(
      @MyTransitiveAnnotation
          @MyAnnotation(MyTransitiveType.VALUE)
          @MyOtherAnnotation(MyTransitiveType.class)
          String str) {
    return str;
  }
}

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

import dagger.BindsInstance;
import dagger.Subcomponent;
import library2.MyTransitiveAnnotation;
import library2.MyTransitiveType;

/**
 * A class used to test that Dagger won't fail when non-dagger related annotations cannot be
 * resolved.
 *
 * <p>During the compilation of {@code :app}, {@link MyTransitiveAnnotation} will no longer be on
 * the classpath. In most cases, Dagger shouldn't care that the annotation isn't on the classpath
 */
// TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
@MyAnnotation(MyTransitiveType.VALUE)
@MyOtherAnnotation(MyTransitiveType.class)
@MySubcomponentScope
@Subcomponent(modules = MySubcomponentModule.class)
public abstract class MySubcomponentWithBuilder {
  @MyQualifier
  // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public abstract MySubcomponentBinding qualifiedMySubcomponentBinding();

  // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public abstract MySubcomponentBinding unqualifiedMySubcomponentBinding();

  // TODO(b/219587431): Support @MyTransitiveAnnotation (We shouldn't need scope/qualifier here).
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  public abstract void injectFoo(
      // TODO(b/219587431): Support @MyTransitiveAnnotation (We shouldn't need scope/qualifier here)
      @MyAnnotation(MyTransitiveType.VALUE) @MyOtherAnnotation(MyTransitiveType.class) Foo foo);

  // TODO(b/219587431): Support @MyTransitiveAnnotation (We shouldn't need scope/qualifier here).
  @MyAnnotation(MyTransitiveType.VALUE)
  @MyOtherAnnotation(MyTransitiveType.class)
  @Subcomponent.Builder
  public abstract static class Builder {
    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    public abstract Builder mySubcomponentModule(
        @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType.class)
            MySubcomponentModule mySubcomponentModule);

    @BindsInstance
    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    public abstract Builder qualifiedMySubcomponentBinding(
        @MyQualifier
            // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType.class)
            MySubcomponentBinding subcomponentBinding);

    @BindsInstance
    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    public abstract Builder unqualifiedMySubcomponentBinding(
        // TODO(b/219587431): Support @MyTransitiveAnnotation (Requires generating metadata).
        @MyAnnotation(MyTransitiveType.VALUE) @MyOtherAnnotation(MyTransitiveType.class)
            MySubcomponentBinding subcomponentBinding);

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    public abstract MySubcomponentWithBuilder build();

    // Non-dagger code

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    public String nonDaggerField = "";

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    public static String nonDaggerStaticField = "";

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    public void nonDaggerMethod(
        @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType.class)
            String str) {}

    @MyTransitiveAnnotation
    @MyAnnotation(MyTransitiveType.VALUE)
    @MyOtherAnnotation(MyTransitiveType.class)
    public static void nonDaggerStaticMethod(
        @MyTransitiveAnnotation
            @MyAnnotation(MyTransitiveType.VALUE)
            @MyOtherAnnotation(MyTransitiveType.class)
            String str) {}
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

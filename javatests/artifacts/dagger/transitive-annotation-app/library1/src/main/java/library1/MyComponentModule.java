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
@Module(includes = {
  MyComponentModule.MyAbstractModule.class
})
public final class MyComponentModule {
  public static final class Dep {
    @Inject Dep() {}
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  @Module
  interface MyAbstractModule {
    @MySimpleTransitiveAnnotation
    // @MyTransitiveAnnotation(VALUE): Not yet supported
    @Binds
    Number bindNumber(
        @MySimpleTransitiveAnnotation
        // @MyTransitiveAnnotation(VALUE): Not yet supported
        int i);
  }

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  private final String nonDaggerField = "";

  @MySimpleTransitiveAnnotation
  @MyTransitiveAnnotation(VALUE)
  MyComponentModule(
      @MySimpleTransitiveAnnotation
      @MyTransitiveAnnotation(VALUE)
      Dep dep) {}

  @MySimpleTransitiveAnnotation
  // @MyTransitiveAnnotation(VALUE): Not yet supported
  @Provides
  int provideInt(
      @MySimpleTransitiveAnnotation
      // @MyTransitiveAnnotation(VALUE): Not yet supported
      Dep dep) {
    return 1;
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

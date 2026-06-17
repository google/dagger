/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.functional.jdk8;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Inject;

/**
 * This class is compiled separately from the test in order to test nullability across compilation
 * boundaries.
 */
final class TypeUseNullabilityClasses {

  static final String EXPECTED_STRING = "foo";
  static final Integer EXPECTED_INTEGER = 123;

  static final class NullFoo {
    final Integer nullableInteger;

    @Inject
    NullFoo(@TypeUse.Nullable Integer nullableInteger) {
      this.nullableInteger = nullableInteger;
    }

    @Inject @TypeUse.Nullable Integer nullableIntegerField;

    Integer nullableMethodInjectedField;

    @Inject
    void inject(@TypeUse.Nullable Integer nullableMethodInjectedField) {
      this.nullableMethodInjectedField = nullableMethodInjectedField;
    }
  }

  static final class GenericFoo<T> {
    T t;

    GenericFoo(T t) {
      this.t = t;
    }
  }

  static final class GenericBar<T> {
    @Inject @TypeUse.Nullable T t;
  }

  static class TypeUse {
    @Target(TYPE_USE)
    @Retention(RUNTIME)
    @interface Nullable {}

    private TypeUse() {}
  }

  private TypeUseNullabilityClasses() {}
}

/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional.guava;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import dagger.BindsOptionalOf;
import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import java.lang.annotation.Retention;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;

/** Classes to support testing {@code BindsOptionalOf} functionality. */
public final class OptionalBindingComponents {

  /** A qualifier. */
  @Qualifier
  @Retention(RUNTIME)
  public @interface SomeQualifier {}

  /** A value object that contains various optionally-bound objects. */
  @AutoValue
  public abstract static class Values {
    abstract Optional<Value> optionalInstance();

    abstract Optional<Provider<Value>> optionalProvider();

    abstract Optional<Lazy<Value>> optionalLazy();

    abstract Optional<Provider<Lazy<Value>>> optionalLazyProvider();
  }

  // Default access so that it's inaccessible to OptionalBindingComponentsWithInaccessibleTypes.
  enum Value {
    VALUE,
    QUALIFIED_VALUE
  }

  static final class InjectedThing {
    @Inject
    InjectedThing() {}
  }

  /** Binds optionals and {@link Values}. */
  @Module
  public abstract static class OptionalBindingModule {
    @BindsOptionalOf
    abstract Value value();

    @BindsOptionalOf
    @SomeQualifier abstract Value qualifiedValue();

    // Valid because it's qualified.
    @BindsOptionalOf
    @SomeQualifier abstract InjectedThing qualifiedInjectedThing();

    @BindsOptionalOf
    abstract Object nullableObject();

    @Provides
    static Values values(
        Optional<Value> optionalInstance,
        Optional<Provider<Value>> optionalProvider,
        Optional<Lazy<Value>> optionalLazy,
        Optional<Provider<Lazy<Value>>> optionalLazyProvider) {
      return new AutoValue_OptionalBindingComponents_Values(
          optionalInstance, optionalProvider, optionalLazy, optionalLazyProvider);
    }

    @Provides
    @SomeQualifier
    static Values qualifiedValues(
        @SomeQualifier Optional<Value> optionalInstance,
        @SomeQualifier Optional<Provider<Value>> optionalProvider,
        @SomeQualifier Optional<Lazy<Value>> optionalLazy,
        @SomeQualifier Optional<Provider<Lazy<Value>>> optionalLazyProvider) {
      return new AutoValue_OptionalBindingComponents_Values(
          optionalInstance, optionalProvider, optionalLazy, optionalLazyProvider);
    }
  }

  /** Binds {@link Value}. */
  @Module
  public abstract static class ConcreteBindingModule {
    /** @param cycle to demonstrate that optional {@link Provider} injection can break cycles */
    @Provides
    static Value value(Optional<Provider<Value>> cycle) {
      return Value.VALUE;
    }

    @Provides
    @SomeQualifier static Value qualifiedValue() {
      return Value.QUALIFIED_VALUE;
    }

    @Provides
    @Nullable
    static Object nullableObject() {
      return null;
    }
  }

  /** Interface for components used to test optional bindings. */
  public interface OptionalBindingComponent {
    Values values();

    @SomeQualifier
    Values qualifiedValues();

    // Nullable bindings can satisfy optional bindings except for Optional<Foo>.

    Optional<Provider<Object>> optionalNullableProvider();

    Optional<Lazy<Object>> optionalNullableLazy();

    Optional<Provider<Lazy<Object>>> optionalNullableLazyProvider();
  }

  @Component(modules = OptionalBindingModule.class)
  interface AbsentOptionalBindingComponent extends OptionalBindingComponent {
    PresentOptionalBindingSubcomponent presentChild();
  }

  @Component(modules = {OptionalBindingModule.class, ConcreteBindingModule.class})
  interface PresentOptionalBindingComponent extends OptionalBindingComponent {}

  @Subcomponent(modules = ConcreteBindingModule.class)
  interface PresentOptionalBindingSubcomponent extends OptionalBindingComponent {}
}

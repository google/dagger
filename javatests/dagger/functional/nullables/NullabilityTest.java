/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.nullables;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NullabilityTest {
  @interface Nullable {}

  @Component(dependencies = NullComponent.class)
  interface NullComponentWithDependency {
    @Nullable String string();
    Number number();
    Provider<String> stringProvider();
    Provider<Number> numberProvider();
  }

  @Component(modules = NullModule.class)
  interface NullComponent {
    @Nullable String string();
    @Nullable Integer integer();
    NullFoo nullFoo();
    Number number();
    Provider<String> stringProvider();
    Provider<Number> numberProvider();
  }

  @Module
  static class NullModule {
    Number numberValue = null;
    Integer integerCallCount = 0;

    @Nullable
    @Provides
    String provideNullableString() {
      return null;
    }

    @Provides
    Number provideNumber() {
      return numberValue;
    }

    @Nullable
    @Provides
    @Reusable
    Integer provideNullReusableInteger() {
      integerCallCount++;
      return null;
    }
  }

  @SuppressWarnings("BadInject") // This is just for testing purposes.
  static class NullFoo {
    final String string;
    final Number number;
    final Provider<String> stringProvider;
    final Provider<Number> numberProvider;

    @Inject
    NullFoo(
        @Nullable String string,
        Number number,
        Provider<String> stringProvider,
        Provider<Number> numberProvider) {
      this.string = string;
      this.number = number;
      this.stringProvider = stringProvider;
      this.numberProvider = numberProvider;
    }

    String methodInjectedString;
    Number methodInjectedNumber;
    Provider<String> methodInjectedStringProvider;
    Provider<Number> methodInjectedNumberProvider;
    @Inject void inject(
        @Nullable String string,
        Number number,
        Provider<String> stringProvider,
        Provider<Number> numberProvider) {
      this.methodInjectedString = string;
      this.methodInjectedNumber = number;
      this.methodInjectedStringProvider = stringProvider;
      this.methodInjectedNumberProvider = numberProvider;
    }

    @Nullable @Inject String fieldInjectedString;
    @Inject Number fieldInjectedNumber;
    @Inject Provider<String> fieldInjectedStringProvider;
    @Inject Provider<Number> fieldInjectedNumberProvider;
  }

  @Test
  public void testNullability_provides() {
    NullModule module = new NullModule();
    NullComponent component =
        DaggerNullabilityTest_NullComponent.builder().nullModule(module).build();

    // Can't construct NullFoo because it depends on Number, and Number was null.
    try {
      component.nullFoo();
      fail();
    } catch (NullPointerException npe) {
      assertThat(npe)
          .hasMessageThat()
          .isEqualTo("Cannot return null from a non-@Nullable @Provides method");
    }

    // set number to non-null so we can create
    module.numberValue = 1;
    NullFoo nullFoo = component.nullFoo();

    // Then set it back to null so we can test its providers.
    module.numberValue = null;
    validate(true, nullFoo.string, nullFoo.stringProvider, nullFoo.numberProvider);
    validate(true, nullFoo.methodInjectedString, nullFoo.methodInjectedStringProvider,
        nullFoo.methodInjectedNumberProvider);
    validate(true, nullFoo.fieldInjectedString, nullFoo.fieldInjectedStringProvider,
        nullFoo.fieldInjectedNumberProvider);
  }

  @Test
  public void testNullability_reusuable() {
    NullModule module = new NullModule();
    NullComponent component =
        DaggerNullabilityTest_NullComponent.builder().nullModule(module).build();

    // Test that the @Nullable @Reusuable binding is cached properly even when the value is null.
    assertThat(module.integerCallCount).isEqualTo(0);
    assertThat(component.integer()).isNull();
    assertThat(module.integerCallCount).isEqualTo(1);
    assertThat(component.integer()).isNull();
    assertThat(module.integerCallCount).isEqualTo(1);
  }

  @Test
  public void testNullability_components() {
    NullComponent nullComponent = new NullComponent() {
      @Override public Provider<String> stringProvider() {
        return new Provider<String>() {
          @Override public String get() {
            return null;
          }
        };
      }

      @Override public String string() {
        return null;
      }

      @Override public Provider<Number> numberProvider() {
        return new Provider<Number>() {
          @Override public Number get() {
            return null;
          }
        };
      }

      @Override public Number number() {
        return null;
      }

      @Override public NullFoo nullFoo() {
        return null;
      }

      @Override public Integer integer() {
        return null;
      }
    };
    NullComponentWithDependency component =
        DaggerNullabilityTest_NullComponentWithDependency.builder()
            .nullComponent(nullComponent)
            .build();
    validate(false, component.string(), component.stringProvider(), component.numberProvider());

    // Also validate that the component's number() method fails
    try {
      component.number();
      fail();
    } catch (NullPointerException npe) {
      assertThat(npe)
          .hasMessageThat()
          .isEqualTo("Cannot return null from a non-@Nullable component method");
    }
  }

  private void validate(boolean fromProvides,
      String string,
      Provider<String> stringProvider,
      Provider<Number> numberProvider) {
    assertThat(string).isNull();
    assertThat(numberProvider).isNotNull();
    try {
      numberProvider.get();
      fail();
    } catch (NullPointerException npe) {
      assertThat(npe)
          .hasMessageThat()
          .isEqualTo(
              "Cannot return null from a non-@Nullable "
                  + (fromProvides ? "@Provides" : "component")
                  + " method");
    }
    assertThat(stringProvider.get()).isNull();
  }
}

/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.functional.factory;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import dagger.Component;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for factories for components with modules that do not require an instance to be passed to
 * the factory. Includes both tests where the module does not have a corresponding parameter in the
 * factory method and where it does have a parameter, for cases where that's allowed.
 */
@RunWith(JUnit4.class)
public final class FactoryImplicitModulesTest {

  @Component(modules = AbstractModule.class)
  interface AbstractModuleComponent {
    String string();

    @Component.Factory
    interface Factory {
      AbstractModuleComponent create();
    }
  }

  @Test
  public void abstractModule() {
    AbstractModuleComponent component =
        DaggerFactoryImplicitModulesTest_AbstractModuleComponent.factory().create();
    assertThat(component.string()).isEqualTo("foo");
  }

  @Component(modules = InstantiableConcreteModule.class)
  interface InstantiableConcreteModuleComponent {
    int getInt();

    @Component.Factory
    interface Factory {
      InstantiableConcreteModuleComponent create();
    }
  }

  @Test
  public void instantiableConcreteModule() {
    InstantiableConcreteModuleComponent component =
        DaggerFactoryImplicitModulesTest_InstantiableConcreteModuleComponent.factory().create();
    assertThat(component.getInt()).isEqualTo(42);
  }

  @Component(modules = InstantiableConcreteModule.class)
  interface InstantiableConcreteModuleWithFactoryParameterComponent {
    int getInt();

    @Component.Factory
    interface Factory {
      InstantiableConcreteModuleWithFactoryParameterComponent create(
          InstantiableConcreteModule module);
    }
  }

  @Test
  public void instantiableConcreteModule_withFactoryParameter() {
    InstantiableConcreteModuleWithFactoryParameterComponent component =
        DaggerFactoryImplicitModulesTest_InstantiableConcreteModuleWithFactoryParameterComponent
            .factory()
            .create(new InstantiableConcreteModule());
    assertThat(component.getInt()).isEqualTo(42);
  }

  @Test
  public void instantiableConcreteModule_withFactoryParameter_failsOnNull() {
    try {
      DaggerFactoryImplicitModulesTest_InstantiableConcreteModuleWithFactoryParameterComponent
          .factory()
          .create(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Component(modules = ConcreteModuleThatCouldBeAbstract.class)
  interface ConcreteModuleThatCouldBeAbstractComponent {
    double getDouble();

    @Component.Factory
    interface Factory {
      ConcreteModuleThatCouldBeAbstractComponent create();
    }
  }

  @Test
  public void concreteModuleThatCouldBeAbstract() {
    ConcreteModuleThatCouldBeAbstractComponent component =
        DaggerFactoryImplicitModulesTest_ConcreteModuleThatCouldBeAbstractComponent.factory()
            .create();
    assertThat(component.getDouble()).isEqualTo(42.0);
  }

  @Component(modules = ConcreteModuleThatCouldBeAbstract.class)
  interface ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent {
    double getDouble();

    @Component.Factory
    interface Factory {
      ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent create(
          ConcreteModuleThatCouldBeAbstract module);
    }
  }

  @Test
  public void concreteModuleThatCouldBeAbstract_withFactoryParameter() {
    ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent component =
        DaggerFactoryImplicitModulesTest_ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent
            .factory()
            .create(new ConcreteModuleThatCouldBeAbstract());
    assertThat(component.getDouble()).isEqualTo(42.0);
  }

  @Test
  public void concreteModuleThatCouldBeAbstract_withFactoryParameter_failsOnNull() {
    // This matches what builders do when there's a setter for such a module; the setter checks that
    // the argument is not null but otherwise ignores it.
    // It's possible that we shouldn't even allow such a parameter for a factory, since unlike a
    // builder, where the setter can just not be called, a factory doesn't give the option of not
    // passing *something* for the unused parameter.
    try {
      ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent component =
          DaggerFactoryImplicitModulesTest_ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent
              .factory()
              .create(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }
}

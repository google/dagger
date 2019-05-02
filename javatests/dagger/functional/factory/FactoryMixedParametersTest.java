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

import dagger.BindsInstance;
import dagger.Component;
import java.util.Random;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for component factories with multiple parameters. */
@RunWith(JUnit4.class)
public final class FactoryMixedParametersTest {

  @Component(
      modules = {
        AbstractModule.class,
        UninstantiableConcreteModule.class,
        InstantiableConcreteModule.class
      },
      dependencies = Dependency.class)
  interface MixedArgComponent {
    String string();
    int getInt();
    long getLong();
    Object object();
    double getDouble();
    Provider<Random> randomProvider();

    @Component.Factory
    interface Factory {
      MixedArgComponent create(
          @BindsInstance double d,
          Dependency dependency,
          UninstantiableConcreteModule module,
          @BindsInstance Random random);
    }
  }

  @Test
  public void mixedArgComponent() {
    Random random = new Random();
    MixedArgComponent component =
        DaggerFactoryMixedParametersTest_MixedArgComponent.factory()
            .create(3.0, new Dependency(), new UninstantiableConcreteModule(2L), random);
    assertThat(component.string()).isEqualTo("foo");
    assertThat(component.getInt()).isEqualTo(42);
    assertThat(component.getDouble()).isEqualTo(3.0);
    assertThat(component.object()).isEqualTo("bar");
    assertThat(component.getLong()).isEqualTo(2L);
    assertThat(component.randomProvider().get()).isSameInstanceAs(random);
    assertThat(component.randomProvider().get()).isSameInstanceAs(random);
  }
}

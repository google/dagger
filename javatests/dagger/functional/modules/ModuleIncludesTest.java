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

package dagger.functional.modules;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.functional.modules.subpackage.PublicModule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModuleIncludesTest {

  @Component(modules = PublicModule.class)
  interface TestComponent {
    Object object();
  }

  @Test
  public void publicModuleIncludingPackagePrivateModuleThatDoesNotRequireInstance() {
    TestComponent component = DaggerModuleIncludesTest_TestComponent.create();
    assertThat(component.object()).isEqualTo("foo42");
  }
}

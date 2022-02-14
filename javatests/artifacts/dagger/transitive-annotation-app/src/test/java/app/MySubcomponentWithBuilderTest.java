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

package app;

import static com.google.common.truth.Truth.assertThat;

import library1.Dep;
import library1.MyComponentDependency;
import library1.MyComponentModule;
import library1.MySubcomponentBinding;
import library1.MySubcomponentModule;
import library1.MySubcomponentWithBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MySubcomponentWithBuilderTest {
  private MySubcomponentWithBuilder subcomponentWithBuilder;

  @Before
  public void setup() {
    subcomponentWithBuilder =
        DaggerMyComponent.factory()
            .create(new MyComponentModule(new Dep()), new MyComponentDependency())
            .mySubcomponentWithBuilder()
            .mySubcomponentModule(new MySubcomponentModule(3))
            .qualifiedMySubcomponentBinding(new MySubcomponentBinding())
            .unqualifiedMySubcomponentBinding(new MySubcomponentBinding())
            .build();
  }

  // Test that the qualified and unqualified bindings are two separate objects
  @Test
  public void testMySubcomponentBinding() {
    assertThat(subcomponentWithBuilder.qualifiedMySubcomponentBinding())
        .isNotEqualTo(subcomponentWithBuilder.unqualifiedMySubcomponentBinding());
  }
}

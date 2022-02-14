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

package app;

import static com.google.common.truth.Truth.assertThat;

import library1.Dep;
import library1.MyComponentDependency;
import library1.MyComponentModule;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MyComponentTest {
  private MyComponent component;

  @Before
  public void setup() {
    component = DaggerMyComponent.factory()
        .create(new MyComponentModule(new Dep()), new MyComponentDependency());
  }

  @Test
  public void testFooIsScoped() {
    assertThat(component.foo()).isEqualTo(component.foo());
  }

  @Test
  public void testAssistedFoo() {
    assertThat(component.assistedFooFactory().create(5)).isNotNull();
  }

  @Test
  public void testScopedQualifiedBindsTypeIsScoped() {
    assertThat(component.scopedQualifiedBindsType())
        .isEqualTo(component.scopedQualifiedBindsType());
  }

  @Test
  public void testScopedUnqualifiedBindsTypeIsScoped() {
    assertThat(component.scopedUnqualifiedBindsType())
        .isEqualTo(component.scopedUnqualifiedBindsType());
  }

  @Test
  public void testUnscopedQualifiedBindsTypeIsNotScoped() {
    assertThat(component.unscopedQualifiedBindsType())
        .isNotEqualTo(component.unscopedQualifiedBindsType());
  }

  @Test
  public void testUnscopedUnqualifiedBindsTypeIsNotScoped() {
    assertThat(component.unscopedUnqualifiedBindsType())
        .isNotEqualTo(component.unscopedUnqualifiedBindsType());
  }

  @Test
  public void testScopedQualifiedProvidesTypeIsScoped() {
    assertThat(component.scopedQualifiedProvidesType())
        .isEqualTo(component.scopedQualifiedProvidesType());
  }

  @Test
  public void testScopedUnqualifiedProvidesTypeIsScoped() {
    assertThat(component.scopedUnqualifiedProvidesType())
        .isEqualTo(component.scopedUnqualifiedProvidesType());
  }

  @Test
  public void testUnscopedQualifiedProvidesTypeIsNotScoped() {
    assertThat(component.unscopedQualifiedProvidesType())
        .isNotEqualTo(component.unscopedQualifiedProvidesType());
  }

  @Test
  public void testUnscopedUnqualifiedProvidesTypeIsNotScoped() {
    assertThat(component.unscopedUnqualifiedProvidesType())
        .isNotEqualTo(component.unscopedUnqualifiedProvidesType());
  }

  @Test
  public void testMyComponentDependencyBinding() {
    assertThat(component.qualifiedMyComponentDependencyBinding())
        .isNotEqualTo(component.unqualifiedMyComponentDependencyBinding());
  }
}

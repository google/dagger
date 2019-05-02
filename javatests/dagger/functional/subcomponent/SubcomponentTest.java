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

package dagger.functional.subcomponent;

import static com.google.common.collect.Sets.intersection;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SubcomponentTest {
  private static final ParentComponent parentComponent = DaggerParentComponent.create();
  private static final ParentOfGenericComponent parentOfGenericComponent =
      DaggerParentOfGenericComponent.create();

  @Parameters
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][] {
        { parentComponent, parentComponent.newChildComponent() },
        { parentComponent, parentComponent.newChildAbstractClassComponent() },
        { parentOfGenericComponent, parentOfGenericComponent.subcomponent() }});
  }

  private final ParentGetters parentGetters;
  private final ChildComponent childComponent;

  public SubcomponentTest(ParentGetters parentGetters, ChildComponent childComponent) {
    this.parentGetters = parentGetters;
    this.childComponent = childComponent;
  }

  @Test
  public void scopePropagatesUpward_class() {
    assertThat(childComponent.requiresSingleton().singletonType())
        .isSameInstanceAs(childComponent.requiresSingleton().singletonType());
    assertThat(childComponent.requiresSingleton().singletonType())
        .isSameInstanceAs(
            childComponent.newGrandchildComponent().requiresSingleton().singletonType());
  }

  @Test
  public void scopePropagatesUpward_provides() {
    assertThat(childComponent.requiresSingleton().unscopedTypeBoundAsSingleton())
        .isSameInstanceAs(childComponent.requiresSingleton().unscopedTypeBoundAsSingleton());
    assertThat(childComponent.requiresSingleton().unscopedTypeBoundAsSingleton())
        .isSameInstanceAs(
            childComponent
                .newGrandchildComponent()
                .requiresSingleton()
                .unscopedTypeBoundAsSingleton());
  }

  @Test
  public void multibindingContributions() {
    Set<Object> parentObjectSet = parentGetters.objectSet();
    assertThat(parentObjectSet).hasSize(2);
    Set<Object> childObjectSet = childComponent.objectSet();
    assertThat(childObjectSet).hasSize(3);
    Set<Object> grandchildObjectSet =
        childComponent.newGrandchildComponent().objectSet();
    assertThat(grandchildObjectSet).hasSize(4);
    assertThat(intersection(parentObjectSet, childObjectSet)).hasSize(1);
    assertThat(intersection(parentObjectSet, grandchildObjectSet)).hasSize(1);
    assertThat(intersection(childObjectSet, grandchildObjectSet)).hasSize(1);
  }

  @Test
  public void unscopedProviders() {
    assume().that(System.getProperty("dagger.mode")).doesNotContain("FastInit");
    assertThat(parentGetters.getUnscopedTypeProvider())
        .isSameInstanceAs(childComponent.getUnscopedTypeProvider());
    assertThat(parentGetters.getUnscopedTypeProvider())
        .isSameInstanceAs(childComponent.newGrandchildComponent().getUnscopedTypeProvider());
  }

  @Test
  public void passedModules() {
    ChildModuleWithState childModuleWithState = new ChildModuleWithState();
    ChildComponentRequiringModules childComponent1 =
        parentComponent.newChildComponentRequiringModules(
            new ChildModuleWithParameters(new Object()),
            childModuleWithState);
    ChildComponentRequiringModules childComponent2 =
        parentComponent.newChildComponentRequiringModules(
            new ChildModuleWithParameters(new Object()),
            childModuleWithState);
    assertThat(childComponent1.getInt()).isEqualTo(0);
    assertThat(childComponent2.getInt()).isEqualTo(1);
  }

  @Test
  public void dependenceisInASubcomponent() {
    assertThat(childComponent.newGrandchildComponent().needsAnInterface()).isNotNull();
  }

  @Test
  public void qualifiedSubcomponentIsBound() {
    assertThat(parentComponent.unresolvableChildComponentBuilder().build().unboundString())
        .isEqualTo("unbound");
  }
}

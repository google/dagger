/*
 * Copyright (C) 2015 Google, Inc.
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
package test.subcomponent;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import test.subcomponent.MultibindingSubcomponents.BoundInChild;
import test.subcomponent.MultibindingSubcomponents.BoundInParent;
import test.subcomponent.MultibindingSubcomponents.BoundInParentAndChild;
import test.subcomponent.MultibindingSubcomponents.ParentWithProvisionHasChildWithProvision;
import test.subcomponent.MultibindingSubcomponents.ParentWithProvisionHasChildWithoutProvision;
import test.subcomponent.MultibindingSubcomponents.ParentWithoutProvisionHasChildWithProvision;
import test.subcomponent.MultibindingSubcomponents.ParentWithoutProvisionHasChildWithoutProvision;
import test.subcomponent.MultibindingSubcomponents.RequiresMultibindings;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class SubcomponentMultibindingsTest {

  private static final RequiresMultibindings<BoundInParent> BOUND_IN_PARENT =
      new RequiresMultibindings<>(
          ImmutableSet.of(BoundInParent.INSTANCE),
          ImmutableMap.of("parent key", BoundInParent.INSTANCE));

  private static final RequiresMultibindings<BoundInChild> BOUND_IN_CHILD =
      new RequiresMultibindings<>(
          ImmutableSet.of(BoundInChild.INSTANCE),
          ImmutableMap.of("child key", BoundInChild.INSTANCE));

  private static final RequiresMultibindings<BoundInParentAndChild> BOUND_IN_PARENT_AND_CHILD =
      new RequiresMultibindings<>(
          ImmutableSet.of(BoundInParentAndChild.IN_PARENT, BoundInParentAndChild.IN_CHILD),
          ImmutableMap.of(
              "parent key", BoundInParentAndChild.IN_PARENT,
              "child key", BoundInParentAndChild.IN_CHILD));

  private static final RequiresMultibindings<BoundInParentAndChild>
      BOUND_IN_PARENT_AND_CHILD_PROVIDED_BY_PARENT =
          new RequiresMultibindings<>(
              ImmutableSet.of(BoundInParentAndChild.IN_PARENT),
              ImmutableMap.of("parent key", BoundInParentAndChild.IN_PARENT));

  private ParentWithoutProvisionHasChildWithoutProvision
      parentWithoutProvisionHasChildWithoutProvision;
  private ParentWithoutProvisionHasChildWithProvision parentWithoutProvisionHasChildWithProvision;
  private ParentWithProvisionHasChildWithoutProvision parentWithProvisionHasChildWithoutProvision;
  private ParentWithProvisionHasChildWithProvision parentWithProvisionHasChildWithProvision;

  @Before
  public void setUp() {
    parentWithoutProvisionHasChildWithoutProvision =
        DaggerMultibindingSubcomponents_ParentWithoutProvisionHasChildWithoutProvision.create();
    parentWithoutProvisionHasChildWithProvision =
        DaggerMultibindingSubcomponents_ParentWithoutProvisionHasChildWithProvision.create();
    parentWithProvisionHasChildWithoutProvision =
        DaggerMultibindingSubcomponents_ParentWithProvisionHasChildWithoutProvision.create();
    parentWithProvisionHasChildWithProvision =
        DaggerMultibindingSubcomponents_ParentWithProvisionHasChildWithProvision.create();
  }

  @Test
  public void testParentWithoutProvisionHasChildWithoutProvision() {
    // Child
    assertThat(
            parentWithoutProvisionHasChildWithoutProvision
                .childWithoutProvision()
                .grandchild()
                .requiresMultibindingsBoundInParent())
        .isEqualTo(BOUND_IN_PARENT);

    // Grandchild
    assertThat(
            parentWithoutProvisionHasChildWithoutProvision
                .childWithoutProvision()
                .grandchild()
                .requiresMultibindingsBoundInParentAndChild())
        .isEqualTo(BOUND_IN_PARENT_AND_CHILD);
    assertThat(
            parentWithoutProvisionHasChildWithoutProvision
                .childWithoutProvision()
                .grandchild()
                .requiresMultibindingsBoundInChild())
        .isEqualTo(BOUND_IN_CHILD);

    assertThat(
            parentWithoutProvisionHasChildWithoutProvision
                .childWithoutProvision()
                .grandchild()
                .setOfRequiresMultibindingsInParentAndChild())
        .containsExactly(BOUND_IN_PARENT_AND_CHILD);
  }

  @Test
  public void testParentWithoutProvisionHasChildWithProvision() {
    // Child
    assertThat(
            parentWithoutProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .requiresMultibindingsBoundInParent())
        .isEqualTo(BOUND_IN_PARENT);

    // Grandchild
    assertThat(
            parentWithoutProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .requiresMultibindingsBoundInParentAndChild())
        .isEqualTo(BOUND_IN_PARENT_AND_CHILD);
    assertThat(
            parentWithoutProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .requiresMultibindingsBoundInChild())
        .isEqualTo(BOUND_IN_CHILD);

    assertThat(
            parentWithoutProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .setOfRequiresMultibindingsInParentAndChild())
        .containsExactly(BOUND_IN_PARENT_AND_CHILD);
  }

  @Test
  public void testParentWithProvisionHasChildWithoutProvision() {
    // Parent
    assertThat(parentWithProvisionHasChildWithoutProvision.requiresMultibindingsBoundInParent())
        .isEqualTo(BOUND_IN_PARENT);

    assertThat(
            parentWithProvisionHasChildWithoutProvision
                .requiresMultibindingsBoundInParentAndChild())
        .isEqualTo(BOUND_IN_PARENT_AND_CHILD_PROVIDED_BY_PARENT);

    // Grandchild
    assertThat(
            parentWithProvisionHasChildWithoutProvision
                .childWithoutProvision()
                .grandchild()
                .requiresMultibindingsBoundInParent())
        .isEqualTo(BOUND_IN_PARENT);
    assertThat(
            parentWithProvisionHasChildWithoutProvision
                .childWithoutProvision()
                .grandchild()
                .requiresMultibindingsBoundInChild())
        .isEqualTo(BOUND_IN_CHILD);

    assertThat(
            parentWithProvisionHasChildWithoutProvision
                .childWithoutProvision()
                .grandchild()
                .requiresMultibindingsBoundInParentAndChild())
        .isEqualTo(BOUND_IN_PARENT_AND_CHILD);

    assertThat(
            parentWithProvisionHasChildWithoutProvision
                .childWithoutProvision()
                .grandchild()
                .setOfRequiresMultibindingsInParentAndChild())
        .containsExactly(BOUND_IN_PARENT_AND_CHILD);
  }

  @Test
  public void testParentWithProvisionHasChildWithProvision() {
    // Parent
    assertThat(parentWithProvisionHasChildWithProvision.requiresMultibindingsBoundInParent())
        .isEqualTo(BOUND_IN_PARENT);

    // Child
    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .requiresMultibindingsBoundInParent())
        .isEqualTo(BOUND_IN_PARENT);
    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .requiresMultibindingsBoundInChild())
        .isEqualTo(BOUND_IN_CHILD);
    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .requiresMultibindingsBoundInParentAndChild())
        .isEqualTo(BOUND_IN_PARENT_AND_CHILD);

    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .setOfRequiresMultibindingsInParentAndChild())
        .containsExactly(BOUND_IN_PARENT_AND_CHILD);

    // Grandchild
    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .requiresMultibindingsBoundInParent())
        .isEqualTo(BOUND_IN_PARENT);
    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .requiresMultibindingsBoundInChild())
        .isEqualTo(BOUND_IN_CHILD);
    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .requiresMultibindingsBoundInParentAndChild())
        .isEqualTo(BOUND_IN_PARENT_AND_CHILD);

    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .setOfRequiresMultibindingsInParentAndChild())
        .containsExactly(BOUND_IN_PARENT_AND_CHILD);
  }
}

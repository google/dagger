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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.functional.subcomponent.MultibindingSubcomponents.BoundInChild;
import dagger.functional.subcomponent.MultibindingSubcomponents.BoundInParent;
import dagger.functional.subcomponent.MultibindingSubcomponents.BoundInParentAndChild;
import dagger.functional.subcomponent.MultibindingSubcomponents.ParentWithProvisionHasChildWithProvision;
import dagger.functional.subcomponent.MultibindingSubcomponents.ParentWithProvisionHasChildWithoutProvision;
import dagger.functional.subcomponent.MultibindingSubcomponents.ParentWithoutProvisionHasChildWithProvision;
import dagger.functional.subcomponent.MultibindingSubcomponents.ParentWithoutProvisionHasChildWithoutProvision;
import dagger.functional.subcomponent.MultibindingSubcomponents.RequiresMultibindings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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

    /*
     * Even though the multibinding for Set<RequiresMultiboundObjects> does not itself have a
     * contribution from the child, it must be pushed down to (not duplicated in) the child because
     * its contribution depends on multibindings that have one contribution from the parent and one
     * from the child.
     *
     */
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

    /*
     * Even though the multibinding for Set<RequiresMultiboundObjects> does not itself have a
     * contribution from the child, it must be pushed down to (not duplicated in) the child because
     * its contribution depends on multibindings that have one contribution from the parent and one
     * from the child.
     *
     */
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

    /*
     * Even though the multibinding for Set<RequiresMultiboundObjects> does not itself have a
     * contribution from the child, it must be pushed down to (not duplicated in) the child because
     * its contribution depends on multibindings that have one contribution from the parent and one
     * from the child.
     *
     */
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

    // https://github.com/google/dagger/issues/401
    assertThat(
        DaggerMultibindingSubcomponents_ParentWithProvisionHasChildWithBinds.create()
            .childWithBinds()
            .requiresMultibindingsBoundInParentAndChild())
        .isEqualTo(BOUND_IN_PARENT_AND_CHILD);

    /*
     * Even though the multibinding for Set<RequiresMultiboundObjects> does not itself have a
     * contribution from the child, it must be pushed down to (not duplicated in) the child because
     * its contribution depends on multibindings that have one contribution from the parent and one
     * from the child.
     *
     */
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

    /*
     * Even though the multibinding for Set<RequiresMultiboundObjects> does not itself have a
     * contribution from the child, it must be pushed down to (not duplicated in) the child because
     * its contribution depends on multibindings that have one contribution from the parent and one
     * from the child.
     *
     */
    assertThat(
            parentWithProvisionHasChildWithProvision
                .childWithProvision()
                .grandchild()
                .setOfRequiresMultibindingsInParentAndChild())
        .containsExactly(BOUND_IN_PARENT_AND_CHILD);
  }
}

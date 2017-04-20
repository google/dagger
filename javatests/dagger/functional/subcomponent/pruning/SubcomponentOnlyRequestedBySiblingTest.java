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

package dagger.functional.subcomponent.pruning;

import static com.google.common.truth.Truth.assertThat;

import dagger.Module;
import dagger.Subcomponent;
import dagger.functional.subcomponent.pruning.ParentDoesntUseSubcomponent.ChildA;
import dagger.functional.subcomponent.pruning.ParentDoesntUseSubcomponent.ChildB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Subcomponent}s which are included with {@link Module#subcomponents()} but not
 * used directly within the component which adds them.
 *
 * <p>This tests to make sure that while resolving one subcomponent (A), another subcomponent (B)
 * can be requested if they have a shared ancestor component. If that shared ancestor did not
 * resolve B directly via any of its entry points, B will still be generated since it is requested
 * by a descendant.
 */
@RunWith(JUnit4.class)
public class SubcomponentOnlyRequestedBySiblingTest {
  @Test
  public void subcomponentAddedInParent_onlyUsedInSibling() {
    ParentDoesntUseSubcomponent parent = DaggerParentDoesntUseSubcomponent.create();
    ChildB childB = parent.childBBuilder().build();
    assertThat(childB.componentHierarchy())
        .containsExactly(ParentDoesntUseSubcomponent.class, ChildB.class);
    assertThat(childB.componentHierarchyFromChildA())
        .containsExactly(ParentDoesntUseSubcomponent.class, ChildA.class);
  }
}

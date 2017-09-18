/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.producers.binds;

import static com.google.common.truth.Truth.assertThat;

import dagger.functional.producers.binds.BindsProductionScopedOnlyUsedInChild.Child;
import dagger.functional.producers.binds.BindsProductionScopedOnlyUsedInChild.Parent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BindsProductionScopedOnlyUsedInChildTest {
  @Test
  public void unscopedExecutor_effectivelyScoped() {
    AtomicInteger counter = new AtomicInteger();
    Parent parent =
        DaggerBindsProductionScopedOnlyUsedInChild_Parent.builder().counter(counter).build();
    Child child = parent.child();

    child.scopedInParent(); // first time the parent-scoped binding is created
    assertThat(counter.get()).isEqualTo(1);
    child.scopedInParent(); // already scoped
    assertThat(counter.get()).isEqualTo(1);

    child.scopedInChild(); // first time the child scoped binding is created
    assertThat(counter.get()).isEqualTo(2);
    child.scopedInChild(); // already scoped
    assertThat(counter.get()).isEqualTo(2);

    Child secondChild = parent.child();
    secondChild.scopedInParent(); // still scoped from the parent, no expected differences
    assertThat(counter.get()).isEqualTo(2); // second child syndrome?
    secondChild.scopedInChild(); // first time scopedInChild is created from the new child
    assertThat(counter.get()).isEqualTo(3);
    secondChild.scopedInChild();
    assertThat(counter.get()).isEqualTo(3);
  }
}

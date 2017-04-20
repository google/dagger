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

package dagger.functional.producers.subcomponent;

import static com.google.common.truth.Truth.assertThat;

import dagger.functional.producers.subcomponent.MultiPackageSubcomponents.ParentComponent;
import dagger.functional.producers.subcomponent.sub.ChildComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MultiPackageSubcomponentTest {

  @Test
  public void childComponent() throws Exception {
    ParentComponent parent = DaggerMultiPackageSubcomponents_ParentComponent.create();
    ChildComponent child = parent.childComponentBuilder().build();
    assertThat(child.str().get()).isEqualTo("Hello, World 42");
  }
}

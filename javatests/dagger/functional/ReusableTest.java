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

package dagger.functional;

import static com.google.common.truth.Truth.assertThat;

import dagger.functional.ComponentWithReusableBindings.ChildOne;
import dagger.functional.ComponentWithReusableBindings.ChildTwo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReusableTest {
  @Test
  public void testReusable() {
    ComponentWithReusableBindings parent = DaggerComponentWithReusableBindings.create();
    ChildOne childOne = parent.childOne();
    ChildTwo childTwo = parent.childTwo();

    Object reusableInParent = parent.reusableInParent();
    assertThat(parent.reusableInParent()).isSameInstanceAs(reusableInParent);
    assertThat(childOne.reusableInParent()).isSameInstanceAs(reusableInParent);
    assertThat(childTwo.reusableInParent()).isSameInstanceAs(reusableInParent);

    Object reusableFromChildOne = childOne.reusableInChild();
    assertThat(childOne.reusableInChild()).isSameInstanceAs(reusableFromChildOne);

    Object reusableFromChildTwo = childTwo.reusableInChild();
    assertThat(childTwo.reusableInChild()).isSameInstanceAs(reusableFromChildTwo);

    assertThat(reusableFromChildTwo).isNotSameInstanceAs(reusableFromChildOne);
  }
}

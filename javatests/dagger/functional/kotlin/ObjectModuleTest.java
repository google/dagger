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

package dagger.functional.kotlin;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ObjectModuleTest {

  @Test
  public void verifyObjectModule() {
    TestKotlinComponentWithObjectModule component =
        DaggerTestKotlinComponentWithObjectModule.create();
    assertThat(component.getDataA()).isNotNull();
    assertThat(component.getDataAFromNestedModule()).isNotNull();
    assertThat(component.getDataB()).isNotNull();
    assertThat(component.getSetOfDataA()).isNotNull();
    assertThat(component.getSetOfDataA()).hasSize(1);
    assertThat(component.getPrimitiveType()).isTrue();
  }
}

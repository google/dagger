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
public class PropertyQualifierTest {

  @Test
  public void verifyQualifiedBinding() {
    TestMemberInjectedClassWithQualifier injectedClass = new TestMemberInjectedClassWithQualifier();
    DaggerTestKotlinComponentWithQualifier.create().inject(injectedClass);

    assertThat(injectedClass.javaDataA).isNotNull();
    assertThat(injectedClass.javaDataB).isNotNull();
    assertThat(injectedClass.javaWithTargetDataA).isNotNull();
    assertThat(injectedClass.kotlinDataA).isNotNull();
    assertThat(injectedClass.dataWithConstructionInjection).isNotNull();
    assertThat(injectedClass.dataWithConstructionInjection.getData()).isNotNull();
  }

  @Test
  public void verifyQualifiedBinding_acrossCompilation() {
    FooWithInjectedQualifier injectedClass = new FooWithInjectedQualifier();
    DaggerTestKotlinComponentWithQualifier.create().inject(injectedClass);

    assertThat(injectedClass.getQualifiedString()).isEqualTo("qualified string");
    assertThat(injectedClass.getQualifiedStringWithPropertyAnnotaion())
        .isEqualTo("qualified string");
  }
}

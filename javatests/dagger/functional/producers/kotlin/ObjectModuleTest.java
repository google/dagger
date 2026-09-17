/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.functional.producers.kotlin;

import static com.google.common.truth.Truth.assertThat;
import static dagger.functional.producers.kotlin.ObjectModuleClassesKt.CLASS_COMPANION_OBJECT_MODULE_VALUE;
import static dagger.functional.producers.kotlin.ObjectModuleClassesKt.INTERFACE_COMPANION_OBJECT_MODULE_VALUE;
import static dagger.functional.producers.kotlin.ObjectModuleClassesKt.NESTED_INTERFACE_COMPANION_OBJECT_MODULE_VALUE;
import static dagger.functional.producers.kotlin.ObjectModuleClassesKt.NESTED_OBJECT_MODULE_VALUE;
import static dagger.functional.producers.kotlin.ObjectModuleClassesKt.OBJECT_MODULE_SET_VALUE;
import static dagger.functional.producers.kotlin.ObjectModuleClassesKt.OBJECT_MODULE_STATIC_VALUE;
import static dagger.functional.producers.kotlin.ObjectModuleClassesKt.OBJECT_MODULE_VALUE;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ObjectModuleTest {

  @Test
  public void verifyObjectModule() throws Exception {
    TestKotlinComponentWithObjectModule component =
        DaggerTestKotlinComponentWithObjectModule.create();
    assertThat(component.getObjectModuleData().get().getData()).isEqualTo(OBJECT_MODULE_VALUE);
    assertThat(component.getObjectModuleStaticData().get().getData())
        .isEqualTo(OBJECT_MODULE_STATIC_VALUE);
    assertThat(component.getNestedObjectModuleData().get().getData())
        .isEqualTo(NESTED_OBJECT_MODULE_VALUE);
    assertThat(component.getInterfaceCompanionObjectModuleData().get().getData())
        .isEqualTo(INTERFACE_COMPANION_OBJECT_MODULE_VALUE);
    assertThat(component.getNestedInterfaceCompanionObjectModuleData().get().getData())
        .isEqualTo(NESTED_INTERFACE_COMPANION_OBJECT_MODULE_VALUE);
    assertThat(component.getClassCompanionObjectModuleData().get().getData())
        .isEqualTo(CLASS_COMPANION_OBJECT_MODULE_VALUE);
    ListenableFuture<Set<TestData>> setFuture = component.getSetOfData();
    assertThat(setFuture).isNotNull();
    assertThat(setFuture.get()).hasSize(1);
    assertThat(setFuture.get().iterator().next().getData()).isEqualTo(OBJECT_MODULE_SET_VALUE);
  }
}

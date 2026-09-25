/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.cycle

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.squareup.javapoet.ClassName
import java.lang.reflect.Method
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LongCycleTest {
  /**
   * Tests a cycle long enough that the real factory is created in a separate initialize method from
   * the delegate factory.
   */
  @Test
  fun longCycle() {
    val longCycleComponent = DaggerLongCycle_LongCycleComponent.create()
    assertThat(longCycleComponent.class1()).isNotNull()
  }
}

/*
 * Copyright (C) 2021 The Dagger Authors.
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
package dagger.hilt.android.simpleKotlin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies internal Kotlin classes are accessible with classpath aggregation.
 */
@RunWith(AndroidJUnit4::class)
class InternalAccessTest {
  @Test
  fun verifyInternalMembersAreAccessible() {
    assertThat(DefinedStrings.morningSweet).isNotNull()
    assertThat(DefinedStrings.getInternalSweet()).isNotNull()
  }
}

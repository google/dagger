/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.hilt.android.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.MapKey
import javax.inject.Inject
import javax.inject.Named
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class BindValueInCompanionObjectTest {

  @MapKey annotation class MyMapKey(val value: String)

  @get:Rule val rule = HiltAndroidRule(this)

  @Inject lateinit var string: String

  @Inject @Named(TEST_QUALIFIER) lateinit var qualifiedString: String

  @Inject @Named(TEST_GETTER_QUALIFIER) lateinit var getterString: String

  @Inject lateinit var map: Map<String, String>

  @Inject lateinit var set: Set<String>

  @Test
  fun testBindValueFieldsAreProvided() {
    rule.inject()
    assertThat(string).isEqualTo(BIND_VALUE_STRING)
    assertThat(qualifiedString).isEqualTo(BIND_VALUE_QUALIFIED_STRING)
    assertThat(getterString).isEqualTo(BIND_VALUE_GETTER_STRING)
    assertThat(map)
      .containsExactlyEntriesIn(
        mapOf(
          BIND_VALUE_MAP_KEY_1 to BIND_VALUE_MAP_VALUE_1,
          BIND_VALUE_MAP_KEY_2 to BIND_VALUE_MAP_VALUE_2,
        )
      )
    assertThat(set)
      .containsExactly(
        BIND_VALUE_SET_STRING_1,
        BIND_VALUE_SET_STRING_2,
        BIND_ELEMENTS_SET_STRING_1,
        BIND_ELEMENTS_SET_STRING_2,
        BIND_ELEMENTS_SET_STRING_3,
        BIND_ELEMENTS_SET_STRING_4,
      )
  }

  companion object {
    @JvmField @BindValue val bindValueString = BIND_VALUE_STRING

    @JvmField
    @BindValue
    @Named(TEST_QUALIFIER)
    val bindValueQualifiedString = BIND_VALUE_QUALIFIED_STRING

    @JvmField
    @BindValueIntoMap
    @MyMapKey(BIND_VALUE_MAP_KEY_1)
    val mapContribution1 = BIND_VALUE_MAP_VALUE_1

    @JvmField @BindValueIntoSet val setContribution1 = BIND_VALUE_SET_STRING_1

    @JvmField
    @BindElementsIntoSet
    val elementsContribution1 = setOf(BIND_ELEMENTS_SET_STRING_1, BIND_ELEMENTS_SET_STRING_2)

    @BindValue @Named(TEST_GETTER_QUALIFIER) val bindValueGetterString = BIND_VALUE_GETTER_STRING

    @BindValueIntoMap @MyMapKey(BIND_VALUE_MAP_KEY_2) val mapContribution2 = BIND_VALUE_MAP_VALUE_2

    @BindValueIntoSet val setContribution2 = BIND_VALUE_SET_STRING_2

    @BindElementsIntoSet
    val elementsContribution2 = setOf(BIND_ELEMENTS_SET_STRING_3, BIND_ELEMENTS_SET_STRING_4)

    private const val BIND_VALUE_STRING = "BIND_VALUE_STRING"
    private const val BIND_VALUE_GETTER_STRING = "BIND_VALUE_GETTER_STRING"
    private const val BIND_VALUE_QUALIFIED_STRING = "BIND_VALUE_QUALIFIED_STRING"
    private const val BIND_VALUE_MAP_KEY_1 = "BIND_VALUE_MAP_KEY_1"
    private const val BIND_VALUE_MAP_VALUE_1 = "BIND_VALUE_MAP_VALUE_1"
    private const val BIND_VALUE_MAP_KEY_2 = "BIND_VALUE_MAP_KEY_2"
    private const val BIND_VALUE_MAP_VALUE_2 = "BIND_VALUE_MAP_VALUE_2"
    private const val BIND_VALUE_SET_STRING_1 = "BIND_VALUE_SET_STRING_1"
    private const val BIND_VALUE_SET_STRING_2 = "BIND_VALUE_SET_STRING_2"
    private const val BIND_ELEMENTS_SET_STRING_1 = "BIND_ELEMENTS_SET_STRING_1"
    private const val BIND_ELEMENTS_SET_STRING_2 = "BIND_ELEMENTS_SET_STRING_2"
    private const val BIND_ELEMENTS_SET_STRING_3 = "BIND_ELEMENTS_SET_STRING_3"
    private const val BIND_ELEMENTS_SET_STRING_4 = "BIND_ELEMENTS_SET_STRING_4"
    private const val TEST_QUALIFIER = "TEST_QUALIFIER"
    private const val TEST_GETTER_QUALIFIER = "TEST_GETTER_QUALIFIER"
  }
}

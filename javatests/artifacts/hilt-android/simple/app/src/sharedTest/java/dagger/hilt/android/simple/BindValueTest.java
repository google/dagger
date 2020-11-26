/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.simple;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableSet;
import dagger.MapKey;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.testing.BindElementsIntoSet;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.BindValueIntoMap;
import dagger.hilt.android.testing.BindValueIntoSet;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.components.SingletonComponent;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** A simple test using Hilt. */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public final class BindValueTest {
  private static final String BIND_VALUE_STRING = "BIND_VALUE_STRING";
  private static final String TEST_QUALIFIER = "TEST_QUALIFIER";

  private static final String SET_STRING_1 = "SetString1";
  private static final String SET_STRING_2 = "SetString2";
  private static final String SET_STRING_3 = "SetString3";

  private static final String KEY_1 = "Key1";
  private static final String KEY_2 = "Key2";
  private static final String VALUE_1 = "Value1";
  private static final String VALUE_2 = "Value2";
  private static final String VALUE_3 = "Value3";

  private static final Integer SET_INT_1 = 1;
  private static final Integer SET_INT_2 = 2;
  private static final Integer SET_INT_3 = 3;

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface BindValueEntryPoint {
    @Named(TEST_QUALIFIER)
    String bindValueString();

    Set<String> getStringSet();
  }

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @BindValue
  @Named(TEST_QUALIFIER)
  String bindValueString = BIND_VALUE_STRING;

  @BindElementsIntoSet Set<String> bindElementsSet1 = ImmutableSet.of(SET_STRING_1);
  @BindElementsIntoSet Set<String> bindElementsSet2 = ImmutableSet.of(SET_STRING_2);

  @BindValueIntoMap
  @MyMapKey(KEY_1)
  String boundValue1 = VALUE_1;

  @BindValueIntoMap
  @MyMapKey(KEY_2)
  String boundValue2 = VALUE_2;

  @BindValueIntoSet Integer bindValueSetInt1 = SET_INT_1;
  @BindValueIntoSet Integer bindValueSetInt2 = SET_INT_2;

  @Inject Set<String> stringSet;
  @Inject Provider<Set<String>> providedStringSet;
  @Inject Provider<Map<String, String>> mapProvider;
  @Inject Set<Integer> intSet;
  @Inject Provider<Set<Integer>> providedIntSet;

  @Test
  public void testBindValueFieldIsProvided() throws Exception {
    assertThat(bindValueString).isEqualTo(BIND_VALUE_STRING);
    assertThat(getBinding()).isEqualTo(BIND_VALUE_STRING);
  }

  @Test
  public void testBindValueIsMutable() throws Exception {
    bindValueString = "newValue";
    assertThat(getBinding()).isEqualTo("newValue");
  }

  @Test
  public void testElementsIntoSet() throws Exception {
    rule.inject();
    // basic check that initial/default values are properly injected
    assertThat(providedStringSet.get()).containsExactly(SET_STRING_1, SET_STRING_2);
    // Test empty sets (something that cannot be done with @BindValueIntoSet)
    bindElementsSet1 = ImmutableSet.of();
    bindElementsSet2 = ImmutableSet.of();
    assertThat(providedStringSet.get()).isEmpty();
    // Test multiple elements in set.
    bindElementsSet1 = ImmutableSet.of(SET_STRING_1, SET_STRING_2, SET_STRING_3);
    assertThat(providedStringSet.get()).containsExactly(SET_STRING_1, SET_STRING_2, SET_STRING_3);
  }

  @Test
  public void testBindValueIntoMap() throws Exception {
    rule.inject();
    Map<String, String> oldMap = mapProvider.get();
    assertThat(oldMap).containsExactly(KEY_1, VALUE_1, KEY_2, VALUE_2);
    boundValue1 = VALUE_3;
    Map<String, String> newMap = mapProvider.get();
    assertThat(oldMap).containsExactly(KEY_1, VALUE_1, KEY_2, VALUE_2);
    assertThat(newMap).containsExactly(KEY_1, VALUE_3, KEY_2, VALUE_2);
  }

  @Test
  public void testBindValueIntoSet() throws Exception {
    rule.inject();
    // basic check that initial/default values are properly injected
    assertThat(providedIntSet.get()).containsExactly(SET_INT_1, SET_INT_2);
    bindValueSetInt1 = SET_INT_3;
    // change the value for bindValueSetString from 1 to 3
    assertThat(providedIntSet.get()).containsExactly(SET_INT_2, SET_INT_3);
  }

  @MapKey
  @interface MyMapKey {
    String value();
  }

  private static String getBinding() {
    return EntryPoints.get(getApplicationContext(), BindValueEntryPoint.class).bindValueString();
  }
}

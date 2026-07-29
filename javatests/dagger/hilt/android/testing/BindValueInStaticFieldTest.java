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

package dagger.hilt.android.testing;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableSet;
import dagger.MapKey;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public final class BindValueInStaticFieldTest {
  private static final String BIND_VALUE_STRING = "BIND_VALUE_STRING";
  private static final String BIND_VALUE_QUALIFIED_STRING = "BIND_VALUE_QUALIFIED_STRING";
  private static final String BIND_VALUE_MAP_KEY = "BIND_VALUE_MAP_KEY";
  private static final String BIND_VALUE_MAP_VALUE = "BIND_VALUE_MAP_VALUE";
  private static final String BIND_VALUE_SET_STRING = "BIND_VALUE_SET_STRING";
  private static final String BIND_ELEMENTS_SET_STRING_1 = "BIND_ELEMENTS_SET_STRING_1";
  private static final String BIND_ELEMENTS_SET_STRING_2 = "BIND_ELEMENTS_SET_STRING_2";
  private static final String TEST_QUALIFIER = "TEST_QUALIFIER";

  @MapKey
  public @interface MyMapKey {
    String value();
  }

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface BindValueEntryPoint {
    String bindValueString();

    @Named(TEST_QUALIFIER)
    String bindValueQualifiedString();

    Map<String, String> stringMap();

    Set<String> stringSet();
  }

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @BindValue static String bindValueString = BIND_VALUE_STRING;

  @BindValue
  @Named(TEST_QUALIFIER)
  static String bindValueQualifiedString = BIND_VALUE_QUALIFIED_STRING;

  @BindValueIntoMap
  @MyMapKey(BIND_VALUE_MAP_KEY)
  static String mapContribution = BIND_VALUE_MAP_VALUE;

  @BindValueIntoSet static String setContribution = BIND_VALUE_SET_STRING;

  @BindElementsIntoSet
  static Set<String> elementsContribution =
      ImmutableSet.of(BIND_ELEMENTS_SET_STRING_1, BIND_ELEMENTS_SET_STRING_2);

  @Inject String string;

  @Inject
  @Named(TEST_QUALIFIER)
  String qualifiedString;

  @Inject Map<String, String> map;
  @Inject Set<String> set;

  @Test
  public void testBindValueFieldsAreProvided() throws Exception {
    rule.inject();
    assertThat(string).isEqualTo(BIND_VALUE_STRING);
    assertThat(qualifiedString).isEqualTo(BIND_VALUE_QUALIFIED_STRING);
    assertThat(map).containsExactly(BIND_VALUE_MAP_KEY, BIND_VALUE_MAP_VALUE);
    assertThat(set)
        .containsExactly(
            BIND_VALUE_SET_STRING, BIND_ELEMENTS_SET_STRING_1, BIND_ELEMENTS_SET_STRING_2);
  }
}

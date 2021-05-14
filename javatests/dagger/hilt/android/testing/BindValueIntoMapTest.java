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

package dagger.hilt.android.testing;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.MapKey;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public final class BindValueIntoMapTest {
  private static final String KEY1 = "SOME_KEY";
  private static final String KEY2 = "SOME_OTHER_KEY";
  private static final String VALUE1 = "SOME_VALUE";
  private static final String VALUE2 = "SOME_OTHER_VALUE";
  private static final String VALUE3 = "A_THIRD_VALUE";

  @BindValueIntoMap
  @MyMapKey(KEY1)
  String boundValue1 = VALUE1;

  @BindValueIntoMap
  @MyMapKey(KEY2)
  String boundValue2 = VALUE2;

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface BindValuesIntoMapEntryPoint {
    Map<String, String> getStringStringMap();
  }

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Inject Provider<Map<String, String>> mapProvider;

  @Test
  public void testInjectedAndModified() throws Exception {
    rule.inject();
    Map<String, String> oldMap = mapProvider.get();
    assertThat(oldMap).containsExactly(KEY1, VALUE1, KEY2, VALUE2);
    boundValue1 = VALUE3;
    Map<String, String> newMap = mapProvider.get();
    assertThat(oldMap).containsExactly(KEY1, VALUE1, KEY2, VALUE2);
    assertThat(newMap).containsExactly(KEY1, VALUE3, KEY2, VALUE2);
  }

  @MapKey
  @interface MyMapKey {
    String value();
  }
}

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
import com.google.common.collect.ImmutableSet;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public final class BindElementsIntoSetTest {
  private static final String SET_STRING_1 = "SetString1";
  private static final String SET_STRING_2 = "SetString2";
  private static final String SET_STRING_3 = "SetString3";

  @BindElementsIntoSet Set<String> bindElementsSet1 = ImmutableSet.of(SET_STRING_1);

  @BindElementsIntoSet Set<String> bindElementsSet2 = ImmutableSet.of(SET_STRING_2);

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface BindElementsIntoSetEntryPoint {
    Set<String> getStringSet();
  }

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Inject Set<String> stringSet;

  @Inject Provider<Set<String>> providedStringSet;

  @Test
  public void testMutated() throws Exception {
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

}

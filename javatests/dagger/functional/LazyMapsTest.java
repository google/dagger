/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional;

import static com.google.common.truth.Truth.assertThat;

import dagger.Lazy;
import dagger.functional.LazyMaps.TestComponent;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LazyMaps}. */
@RunWith(JUnit4.class)
public class LazyMapsTest {
  @Test
  public void mapOfLazies() {
    TestComponent component = DaggerLazyMaps_TestComponent.create();
    Map<String, Lazy<String>> laziesMap = component.mapOfLazy();

    String firstGet = laziesMap.get("key").get();
    assertThat(firstGet).isEqualTo("value-1");
    assertThat(firstGet).isSameInstanceAs(laziesMap.get("key").get());

    assertThat(component.mapOfLazy().get("key").get()).isEqualTo("value-2");
  }

  @Test
  public void mapOfProviderOfLaziesReturnsDifferentLazy() {
    TestComponent component = DaggerLazyMaps_TestComponent.create();
    Map<String, Provider<Lazy<String>>> providersOfLaziesMap = component.mapOfProviderOfLazy();

    assertThat(providersOfLaziesMap.get("key").get().get())
        .isNotEqualTo(providersOfLaziesMap.get("key").get().get());
  }
}

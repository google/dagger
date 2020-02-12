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

package dagger.functional.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.ProductionComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests component dependencies.
 */
@RunWith(JUnit4.class)
public final class ComponentDependenciesTest {
  public interface One {
    ListenableFuture<String> getString();
  }

  public interface Two {
    ListenableFuture<String> getString();
  }

  public interface Merged extends One, Two {
  }

  @ProductionComponent(dependencies = Merged.class)
  interface TestProductionComponent {
    ListenableFuture<String> getString();

    @ProductionComponent.Builder
    interface Builder {
      Builder dep(Merged dep);

      TestProductionComponent build();
    }
  }

  @Test
  public void testSameMethodTwiceProduction() throws Exception {
    TestProductionComponent component =
        DaggerComponentDependenciesTest_TestProductionComponent.builder().dep(
            () -> Futures.immediateFuture("test")).build();
    assertThat(component.getString().get()).isEqualTo("test");
  }

  public interface OneOverride {
    ListenableFuture<?> getString();
  }

  public interface TwoOverride {
    ListenableFuture<?> getString();
  }

  public interface MergedOverride extends OneOverride, TwoOverride {
    @Override
    ListenableFuture<String> getString();
  }

  @ProductionComponent(dependencies = MergedOverride.class)
  interface TestOverrideComponent {
    ListenableFuture<String> getString();

    @ProductionComponent.Builder
    interface Builder {
      Builder dep(MergedOverride dep);

      TestOverrideComponent build();
    }
  }

  @Test
  public void testPolymorphicOverridesStillCompiles() throws Exception {
    TestOverrideComponent component =
        DaggerComponentDependenciesTest_TestOverrideComponent.builder().dep(
            () -> Futures.immediateFuture("test")).build();
    assertThat(component.getString().get()).isEqualTo("test");
  }
}

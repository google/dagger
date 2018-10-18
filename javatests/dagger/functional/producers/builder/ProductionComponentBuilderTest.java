/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.producers.builder;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link dagger.producers.ProductionComponent.Builder}. */
@RunWith(JUnit4.class)
public final class ProductionComponentBuilderTest {

  @Test
  public void successfulBuild() throws Exception {
    TestComponentWithBuilder component =
        DaggerTestComponentWithBuilder.builder()
            .depComponent(depComponent(15.3))
            .strModule(new StringModule())
            .build();
    assertThat(component.s().get()).isEqualTo("arg: 42");
    assertThat(component.d().get()).isEqualTo(15.3);
  }

  @Test
  public void successfulBuild_withMissingZeroArgModule() throws Exception {
    TestComponentWithBuilder component =
        DaggerTestComponentWithBuilder.builder()
            .depComponent(depComponent(15.3))
            .build();
    assertThat(component.s().get()).isEqualTo("arg: 42");
    assertThat(component.d().get()).isEqualTo(15.3);
  }

  @Test(expected = IllegalStateException.class)
  public void missingDepComponent() {
    DaggerTestComponentWithBuilder.builder()
        .strModule(new StringModule())
        .build();
  }

  private static DepComponent depComponent(final double value) {
    return new DepComponent() {
      @Override
      public ListenableFuture<Double> d() {
        return Futures.immediateFuture(value);
      }
    };
  }
}

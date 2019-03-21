/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.functional.producers.aot;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.BindsOptionalOf;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProducesMethodShadowsInjectConstructorTest {
  static class Multibound {}
  static class Maybe {}

  static class HasInjectConstructor {
    @Inject HasInjectConstructor() {}
  }

  static class DependsOnShadowingProducer {}

  @ProducerModule
  abstract static class LeafModule {
    @Produces
    static DependsOnShadowingProducer dependsOnShadowingProducer(
        // When viewed just within the leaf, this will resolve HasInjectConstructor to the @Inject
        // constructor (and will receive a producerFromProvider), but when viewed within an ancestor
        // that defines a @Produces method for HasInjectConstructor, the binding will be a regular
        // Producer
        HasInjectConstructor hasInjectConstructor,
        Optional<Maybe> maybe) {
      return new DependsOnShadowingProducer();
    }

    @Provides
    @IntoSet
    static Multibound provisionContribution() {
      return new Multibound();
    }

    @BindsOptionalOf
    abstract Maybe maybe();
  }

  @ProductionSubcomponent(modules = LeafModule.class)
  interface Leaf {
    ListenableFuture<DependsOnShadowingProducer> dependsOnShadowingProducer();
    ListenableFuture<Set<Multibound>> shadowedProvisionMultibinding();
    ListenableFuture<Optional<Maybe>> emptyProvisionBindingToPresentProductionBinding();
  }

  @ProducerModule
  static class RootModule {
    @Produces
    static HasInjectConstructor shadowInjectConstructor() {
      return new HasInjectConstructor();
    }

    @Produces
    @IntoSet
    static Multibound productionContribution() {
      return new Multibound();
    }

    @Provides
    @Production
    static Executor executor() {
      return MoreExecutors.directExecutor();
    }

    @Produces
    static Maybe presentMaybeInParent() {
      return new Maybe();
    }
  }

  @ProductionComponent(modules = RootModule.class)
  interface Root {
    Leaf leaf();
  }

  @Test
  public void shadowedInjectConstructorDoesNotCauseClassCast() throws Exception {
    Leaf leaf = DaggerProducesMethodShadowsInjectConstructorTest_Root.create().leaf();
    leaf.dependsOnShadowingProducer().get();
    assertThat(leaf.shadowedProvisionMultibinding().get()).hasSize(2);
    assertThat(leaf.emptyProvisionBindingToPresentProductionBinding().get()).isPresent();
  }
}

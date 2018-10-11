/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.functional.producers.cancellation;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.BindsInstance;
import dagger.producers.CancellationPolicy;
import dagger.producers.CancellationPolicy.Propagation;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.util.concurrent.Executor;
import javax.inject.Named;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for parent production components with a {@code CancellationPolicy} that allows subcomponent
 * cancellation to propagate to them
 */
@RunWith(JUnit4.class)
public final class CancellationPolicyTest {

  @ProducerModule(subcomponents = Child.class)
  static class ParentModule {
    private final ProducerTester tester;

    ParentModule(ProducerTester tester) {
      this.tester = tester;
    }

    @Produces
    @Named("a")
    ListenableFuture<String> produceA() {
      return tester.start("a");
    }
  }

  interface Parent {
    @Named("a")
    ListenableFuture<String> a();

    Child.Builder childBuilder();

    interface Builder<P extends Parent, B extends Builder<P, B>> {
      B module(ParentModule module);

      @BindsInstance
      B executor(@Production Executor executor);

      P build();
    }
  }

  @CancellationPolicy(fromSubcomponents = Propagation.PROPAGATE)
  @ProductionComponent(modules = ParentModule.class)
  interface PropagatingParent extends Parent {
    @ProductionComponent.Builder
    interface Builder extends Parent.Builder<PropagatingParent, Builder> {}
  }

  @CancellationPolicy(fromSubcomponents = Propagation.IGNORE)
  @ProductionComponent(modules = ParentModule.class)
  interface NonPropagatingParent extends Parent {
    @ProductionComponent.Builder
    interface Builder extends Parent.Builder<NonPropagatingParent, Builder> {}
  }

  @ProducerModule
  static class ChildModule {
    private final ProducerTester tester;

    ChildModule(ProducerTester tester) {
      this.tester = tester;
    }

    @Produces
    @Named("b")
    ListenableFuture<String> b(@Named("a") String a) {
      return tester.start("b");
    }
  }

  @ProductionSubcomponent(modules = ChildModule.class)
  interface Child {
    @Named("b")
    ListenableFuture<String> b();

    @ProductionSubcomponent.Builder
    interface Builder {
      Builder module(ChildModule module);

      Child build();
    }
  }

  private final ProducerTester tester = new ProducerTester();

  @Test
  public void propagatingParent_childCancellationPropagatesToParent() {
    PropagatingParent parent =
        DaggerCancellationPolicyTest_PropagatingParent.builder()
            .module(new ParentModule(tester))
            .executor(MoreExecutors.directExecutor())
            .build();
    ListenableFuture<String> a = parent.a();

    Child child = parent.childBuilder().module(new ChildModule(tester)).build();

    ListenableFuture<String> b = child.b();

    tester.assertStarted("a").only();

    assertThat(a.isDone()).isFalse();
    assertThat(b.isDone()).isFalse();

    assertThat(b.cancel(true)).isTrue();
    assertThat(b.isCancelled()).isTrue();

    tester.assertCancelled("a");

    assertThat(a.isCancelled()).isTrue();
  }

  @Test
  public void nonPropagatingParent_childCancellationDoesNotPropagateToParent() throws Exception {
    // This test is basically just checking that when the parent has fromSubcomponents = IGNORE, it
    // behaves the same as having no @CancellationPolicy at all (as tested in
    // ProducerSubcomponentCancellationTester)
    NonPropagatingParent parent =
        DaggerCancellationPolicyTest_NonPropagatingParent.builder()
            .module(new ParentModule(tester))
            .executor(MoreExecutors.directExecutor())
            .build();
    ListenableFuture<String> a = parent.a();

    Child child = parent.childBuilder().module(new ChildModule(tester)).build();

    ListenableFuture<String> b = child.b();

    tester.assertStarted("a").only();

    assertThat(a.isDone()).isFalse();
    assertThat(b.isDone()).isFalse();

    assertThat(b.cancel(true)).isTrue();
    assertThat(b.isCancelled()).isTrue();

    tester.assertNotCancelled("a");

    assertThat(a.isDone()).isFalse();

    tester.complete("a");
    assertThat(a.isDone()).isTrue();
    assertThat(a.get(1, MILLISECONDS)).isEqualTo("completed");

    tester.assertNotStarted("b");
  }
}

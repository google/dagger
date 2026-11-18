/*
 * Copyright (C) 2025 The Dagger Authors.
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
import com.google.common.util.concurrent.MoreExecutors;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// Regression test for b/71595104
@RunWith(JUnit4.class)
public final class GenericComponentTest {
  @ProductionComponent(
      modules = {ExecutorModule.class, NongenericModule.class, ConcreteBoundedModule.class})
  interface GenericComponent {
    ListenableFuture<List<String>> list();

    ListenableFuture<List<PublicFoo>> boundedList();
  }

  public interface PublicBar<T> {
    String value();
  }

  public static final class PublicFoo implements PublicBar<PublicFoo> {
    @Override
    public String value() {
      return "bounded";
    }
  }

  @Module
  static final class ExecutorModule {
    @Provides
    @Production
    Executor executor() {
      return MoreExecutors.directExecutor();
    }
  }

  // Tests a generic base module with an unbounded type parameter (T).
  @ProducerModule
  static final class NongenericModule extends GenericModule<String> {
    @Produces
    static String string() {
      return "string";
    }
  }

  @ProducerModule
  abstract static class GenericModule<T> {
    @Produces
    List<T> list(T t, String string) {
      return Arrays.asList(t);
    }
  }

  // Tests a generic base module with a self-referential upper bound (T extends PublicBar<T>).
  @ProducerModule
  static final class ConcreteBoundedModule extends BoundedGenericModule<PublicFoo> {
    @Produces
    static PublicFoo publicFoo() {
      return new PublicFoo();
    }
  }

  @ProducerModule
  abstract static class BoundedGenericModule<T extends PublicBar<T>> {
    @Produces
    List<T> boundedList(T t) {
      String unused = t.value();
      return Arrays.asList(t);
    }
  }

  @Test
  public void compileTest() throws Exception {
    GenericComponent component = DaggerGenericComponentTest_GenericComponent.create();
    assertThat(component.list().isDone()).isTrue();
    assertThat(Futures.getDone(component.list())).containsExactly("string");
    assertThat(component.boundedList().isDone()).isTrue();
    assertThat(Futures.getDone(component.boundedList())).hasSize(1);
    assertThat(Futures.getDone(component.boundedList()).get(0).value()).isEqualTo("bounded");
  }
}

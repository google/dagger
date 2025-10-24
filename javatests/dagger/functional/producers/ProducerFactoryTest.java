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

package dagger.functional.producers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import dagger.Lazy;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoSet;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.internal.AbstractProducer;
import dagger.producers.internal.CancellableProducer;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import javax.inject.Provider;
import javax.inject.Qualifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class ProducerFactoryTest {
  @ProducerModule
  static final class TestModule {
    @Qualifier @interface Qual {
      int value();
    }

    // Unique bindings.

    @Produces
    @Qual(-2)
    static ListenableFuture<String> throwingProducer() {
      throw new RuntimeException("monkey");
    }

    @Produces
    @Qual(-1)
    static ListenableFuture<String> settableFutureStr(SettableFuture<String> future) {
      return future;
    }

    @Produces
    @Qual(0)
    static String str() {
      return "str";
    }

    @Produces
    @Qual(1)
    static ListenableFuture<String> futureStr() {
      return Futures.immediateFuture("future str");
    }

    @Produces
    @Qual(2)
    static String strWithArg(@SuppressWarnings("unused") int i) {
      return "str with arg";
    }

    @Produces
    @Qual(3)
    static ListenableFuture<String> futureStrWithArg(@SuppressWarnings("unused") int i) {
      return Futures.immediateFuture("future str with arg");
    }

    @Produces
    @Qual(4)
    @SuppressWarnings("unused") // unthrown exception for test
    static String strThrowingException() throws IOException {
      return "str throwing exception";
    }

    @Produces
    @Qual(5)
    @SuppressWarnings("unused") // unthrown exception for test
    static ListenableFuture<String> futureStrThrowingException() throws IOException {
      return Futures.immediateFuture("future str throwing exception");
    }

    @Produces
    @Qual(6)
    @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
    static String strWithArgThrowingException(int i) throws IOException {
      return "str with arg throwing exception";
    }

    @Produces
    @Qual(7)
    @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
    static ListenableFuture<String> futureStrWithArgThrowingException(int i) throws IOException {
      return Futures.immediateFuture("future str with arg throwing exception");
    }

    @Produces
    @Qual(8)
    static String strWithArgs(
        @SuppressWarnings("unused") int i,
        @SuppressWarnings("unused") Produced<Double> b,
        @SuppressWarnings("unused") Producer<Object> c,
        @SuppressWarnings("unused") Provider<Boolean> d) {
      return "str with args";
    }

    @Produces
    @Qual(9)
    @SuppressWarnings("unused") // unthrown exception for test, unused parameters for test
    static String strWithArgsThrowingException(
        int i, Produced<Double> b, Producer<Object> c, Provider<Boolean> d) throws IOException {
      return "str with args throwing exception";
    }

    @Produces
    @Qual(10)
    static ListenableFuture<String> futureStrWithArgs(
        @SuppressWarnings("unused") int i,
        @SuppressWarnings("unused") Produced<Double> b,
        @SuppressWarnings("unused") Producer<Object> c,
        @SuppressWarnings("unused") Provider<Boolean> d) {
      return Futures.immediateFuture("future str with args");
    }

    @Produces
    @Qual(11)
    @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
    static ListenableFuture<String> futureStrWithArgsThrowingException(
        int i, Produced<Double> b, Producer<Object> c, Provider<Boolean> d) throws IOException {
      return Futures.immediateFuture("str with args throwing exception");
    }

    @Produces
    @Qual(12)
    static String strWithFrameworkTypeArgs(
        @SuppressWarnings("unused") @Qual(1) int i,
        @SuppressWarnings("unused") @Qual(1) Provider<Integer> iProvider,
        @SuppressWarnings("unused") @Qual(1) Lazy<Integer> iLazy,
        @SuppressWarnings("unused") @Qual(2) int j,
        @SuppressWarnings("unused") @Qual(2) Produced<Integer> jProduced,
        @SuppressWarnings("unused") @Qual(2) Producer<Integer> jProducer,
        @SuppressWarnings("unused") @Qual(3) Produced<Integer> kProduced,
        @SuppressWarnings("unused") @Qual(3) Producer<Integer> kProducer) {
      return "str with framework type args";
    }

    // Set bindings.

    @Produces
    @IntoSet
    static String setOfStrElement() {
      return "set of str element";
    }

    @Produces
    @IntoSet
    @SuppressWarnings("unused") // unthrown exception for test
    static String setOfStrElementThrowingException() throws IOException {
      return "set of str element throwing exception";
    }

    @Produces
    @IntoSet
    static ListenableFuture<String> setOfStrFutureElement() {
      return Futures.immediateFuture("set of str element");
    }

    @Produces
    @IntoSet
    @SuppressWarnings("unused") // unthrown exception for test
    static ListenableFuture<String> setOfStrFutureElementThrowingException() throws IOException {
      return Futures.immediateFuture("set of str element throwing exception");
    }

    @Produces
    @IntoSet
    static String setOfStrElementWithArg(@SuppressWarnings("unused") int i) {
      return "set of str element with arg";
    }

    @Produces
    @IntoSet
    @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
    static String setOfStrElementWithArgThrowingException(int i) throws IOException {
      return "set of str element with arg throwing exception";
    }

    @Produces
    @IntoSet
    static ListenableFuture<String> setOfStrFutureElementWithArg(
        @SuppressWarnings("unused") int i) {
      return Futures.immediateFuture("set of str element with arg");
    }

    @Produces
    @IntoSet
    @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
    static ListenableFuture<String> setOfStrFutureElementWithArgThrowingException(int i)
        throws IOException {
      return Futures.immediateFuture("set of str element with arg throwing exception");
    }

    @Produces
    @ElementsIntoSet
    static Set<String> setOfStrValues() {
      return ImmutableSet.of("set of str 1", "set of str 2");
    }

    @Produces
    @ElementsIntoSet
    @SuppressWarnings("unused") // unthrown exception for test
    static Set<String> setOfStrValuesThrowingException() throws IOException {
      return ImmutableSet.of("set of str 1", "set of str 2 throwing exception");
    }

    @Produces
    @ElementsIntoSet
    static ListenableFuture<Set<String>> setOfStrFutureValues() {
      return Futures.<Set<String>>immediateFuture(ImmutableSet.of("set of str 1", "set of str 2"));
    }

    @Produces
    @ElementsIntoSet
    @SuppressWarnings("unused") // unthrown exception for test
    static ListenableFuture<Set<String>> setOfStrFutureValuesThrowingException()
        throws IOException {
      return Futures.<Set<String>>immediateFuture(
          ImmutableSet.of("set of str 1", "set of str 2 throwing exception"));
    }

    @Produces
    @ElementsIntoSet
    static Set<String> setOfStrValuesWithArg(@SuppressWarnings("unused") int i) {
      return ImmutableSet.of("set of str with arg 1", "set of str with arg 2");
    }

    @Produces
    @ElementsIntoSet
    @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
    static Set<String> setOfStrValuesWithArgThrowingException(int i) throws IOException {
      return ImmutableSet.of("set of str with arg 1", "set of str with arg 2 throwing exception");
    }

    @Produces
    @ElementsIntoSet
    static ListenableFuture<Set<String>> setOfStrFutureValuesWithArg(
        @SuppressWarnings("unused") int i) {
      return Futures.<Set<String>>immediateFuture(
          ImmutableSet.of("set of str with arg 1", "set of str with arg 2"));
    }

    @Produces
    @ElementsIntoSet
    @SuppressWarnings("unused") // unthrown exception for test, unused parameter for test
    static ListenableFuture<Set<String>> setOfStrFutureValuesWithArgThrowingException(int i)
        throws IOException {
      return Futures.<Set<String>>immediateFuture(
          ImmutableSet.of("set of str with arg 1", "set of str with arg 2 throwing exception"));
    }

    /**
     * A binding method that might result in a generated factory with conflicting field and
     * parameter names.
     */
    @Produces
    static Object object(int foo, Provider<String> fooProvider) {
      return foo + fooProvider.get();
    }
  }

  @Mock private ProductionComponentMonitor componentMonitor;
  private ProducerMonitor monitor;
  private dagger.internal.Provider<Executor> executorProvider;
  private dagger.internal.Provider<ProductionComponentMonitor> componentMonitorProvider;

  @Before
  public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
    monitor = Mockito.mock(ProducerMonitor.class, Mockito.CALLS_REAL_METHODS);
    when(componentMonitor.producerMonitorFor(any(ProducerToken.class))).thenReturn(monitor);
    executorProvider =
        new dagger.internal.Provider<Executor>() {
          @Override
          public Executor get() {
            return MoreExecutors.directExecutor();
          }
        };
    componentMonitorProvider =
        new dagger.internal.Provider<ProductionComponentMonitor>() {
          @Override
          public ProductionComponentMonitor get() {
            return componentMonitor;
          }
        };
  }

  @Test
  public void noArgMethod() throws Exception {
    ProducerToken token = ProducerToken.create(ProducerFactoryTest_TestModule_StrFactory.class);
    Producer<String> producer =
        ProducerFactoryTest_TestModule_StrFactory.create(
            executorProvider, componentMonitorProvider);
    assertThat(producer.get().get()).isEqualTo("str");
    InOrder order = inOrder(componentMonitor, monitor);
    order.verify(componentMonitor).producerMonitorFor(token);
    order.verify(monitor).methodStarting();
    order.verify(monitor).methodFinished();
    order.verify(monitor).succeeded("str");
    order.verifyNoMoreInteractions();
  }

  @Test
  public void singleArgMethod() throws Exception {
    SettableFuture<Integer> intFuture = SettableFuture.create();
    CancellableProducer<Integer> intProducer = producerOfFuture(intFuture);
    Producer<String> producer =
        ProducerFactoryTest_TestModule_StrWithArgFactory.create(
            executorProvider, componentMonitorProvider, intProducer);
    assertThat(producer.get().isDone()).isFalse();
    intFuture.set(42);
    assertThat(producer.get().get()).isEqualTo("str with arg");
  }

  @Test
  public void successMonitor() throws Exception {
    ProducerToken token =
        ProducerToken.create(ProducerFactoryTest_TestModule_SettableFutureStrFactory.class);

    SettableFuture<String> strFuture = SettableFuture.create();
    @SuppressWarnings("FutureReturnValueIgnored")
    SettableFuture<SettableFuture<String>> strFutureFuture = SettableFuture.create();
    CancellableProducer<SettableFuture<String>> strFutureProducer =
        producerOfFuture(strFutureFuture);
    Producer<String> producer =
        ProducerFactoryTest_TestModule_SettableFutureStrFactory.create(
            executorProvider, componentMonitorProvider, strFutureProducer);
    assertThat(producer.get().isDone()).isFalse();

    InOrder order = inOrder(componentMonitor, monitor);
    order.verify(componentMonitor).producerMonitorFor(token);

    strFutureFuture.set(strFuture);
    order.verify(monitor).methodStarting();
    order.verify(monitor).methodFinished();
    assertThat(producer.get().isDone()).isFalse();

    strFuture.set("monkey");
    assertThat(producer.get().get()).isEqualTo("monkey");
    order.verify(monitor).succeeded("monkey");

    order.verifyNoMoreInteractions();
  }

  @Test
  public void failureMonitor() throws Exception {
    ProducerToken token =
        ProducerToken.create(ProducerFactoryTest_TestModule_SettableFutureStrFactory.class);

    SettableFuture<String> strFuture = SettableFuture.create();
    @SuppressWarnings("FutureReturnValueIgnored")
    SettableFuture<SettableFuture<String>> strFutureFuture = SettableFuture.create();
    CancellableProducer<SettableFuture<String>> strFutureProducer =
        producerOfFuture(strFutureFuture);
    Producer<String> producer =
        ProducerFactoryTest_TestModule_SettableFutureStrFactory.create(
            executorProvider, componentMonitorProvider, strFutureProducer);
    assertThat(producer.get().isDone()).isFalse();

    InOrder order = inOrder(componentMonitor, monitor);
    order.verify(componentMonitor).producerMonitorFor(token);

    strFutureFuture.set(strFuture);
    order.verify(monitor).methodStarting();
    order.verify(monitor).methodFinished();
    assertThat(producer.get().isDone()).isFalse();

    Throwable t = new RuntimeException("monkey");
    strFuture.setException(t);
    try {
      producer.get().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isSameInstanceAs(t);
      order.verify(monitor).failed(t);
    }

    order.verifyNoMoreInteractions();
  }

  @Test
  public void failureMonitorDueToThrowingProducer() throws Exception {
    ProducerToken token =
        ProducerToken.create(ProducerFactoryTest_TestModule_ThrowingProducerFactory.class);

    Producer<String> producer =
        ProducerFactoryTest_TestModule_ThrowingProducerFactory.create(
            executorProvider, componentMonitorProvider);
    assertThat(producer.get().isDone()).isTrue();

    InOrder order = inOrder(componentMonitor, monitor);
    order.verify(componentMonitor).producerMonitorFor(token);

    order.verify(monitor).methodStarting();
    order.verify(monitor).methodFinished();

    try {
      producer.get().get();
      fail();
    } catch (ExecutionException e) {
      order.verify(monitor).failed(e.getCause());
    }

    order.verifyNoMoreInteractions();
  }

  @Test
  public void nullComponentMonitorProvider() throws Exception {
    assertThrows(
        NullPointerException.class,
        () -> ProducerFactoryTest_TestModule_StrFactory.create(executorProvider, null));
  }

  private static <T> CancellableProducer<T> producerOfFuture(final ListenableFuture<T> future) {
    return new AbstractProducer<T>() {
      @Override
      public ListenableFuture<T> compute() {
        return future;
      }
    };
  }
}

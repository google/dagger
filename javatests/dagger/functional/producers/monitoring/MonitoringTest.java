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

package dagger.functional.producers.monitoring;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import dagger.functional.producers.ExecutorModule;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for production components using monitoring. */
@RunWith(JUnit4.class)
public final class MonitoringTest {
  @Mock private ProductionComponentMonitor.Factory componentMonitorFactory;
  @Mock private StringStub server1;
  @Mock private StringStub server2;
  private SettableFuture<String> server1Future;
  private SettableFuture<String> server2Future;
  private FakeProductionComponentMonitor componentMonitor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    componentMonitor = new FakeProductionComponentMonitor();
    when(componentMonitorFactory.create(any())).thenReturn(componentMonitor);
    server1Future = SettableFuture.create();
    server2Future = SettableFuture.create();
    when(server1.run(any(String.class))).thenReturn(server1Future);
    when(server2.run(any(String.class))).thenReturn(server2Future);
  }

  @Test
  public void basicMonitoring() throws Exception {
    MonitoredComponent component =
        DaggerMonitoredComponent.builder()
            .monitoringModule(new MonitoringModule(componentMonitorFactory))
            .stubModule(new StubModule(server1, server2))
            .build();
    ListenableFuture<String> output = component.output();
    assertThat(componentMonitor.monitors).hasSize(3);
    ImmutableList<Map.Entry<ProducerToken, ProducerMonitor>> entries =
        ImmutableList.copyOf(componentMonitor.monitors.entrySet());
    assertThat(entries.get(0).getKey().toString()).contains("CallServer2");
    assertThat(entries.get(1).getKey().toString()).contains("CallServer1");
    assertThat(entries.get(2).getKey().toString()).contains("RequestData");

    ProducerMonitor callServer2Monitor = entries.get(0).getValue();
    ProducerMonitor callServer1Monitor = entries.get(1).getValue();
    ProducerMonitor requestDataMonitor = entries.get(2).getValue();

    InOrder inOrder = inOrder(requestDataMonitor, callServer1Monitor, callServer2Monitor);
    inOrder.verify(callServer2Monitor).requested();
    inOrder.verify(callServer1Monitor).requested();
    inOrder.verify(requestDataMonitor).requested();
    inOrder.verify(requestDataMonitor).ready();
    inOrder.verify(requestDataMonitor).methodStarting();
    inOrder.verify(requestDataMonitor).methodFinished();
    inOrder.verify(requestDataMonitor).succeeded("Hello, World!");
    inOrder.verify(callServer1Monitor).ready();
    inOrder.verify(callServer1Monitor).methodStarting();
    inOrder.verify(callServer1Monitor).methodFinished();
    verifyNoMoreInteractions(requestDataMonitor, callServer1Monitor, callServer2Monitor);

    server1Future.set("server 1 response");
    inOrder.verify(callServer1Monitor).succeeded("server 1 response");
    inOrder.verify(callServer2Monitor).ready();
    inOrder.verify(callServer2Monitor).methodStarting();
    inOrder.verify(callServer2Monitor).methodFinished();
    verifyNoMoreInteractions(requestDataMonitor, callServer1Monitor, callServer2Monitor);

    server2Future.set("server 2 response");
    inOrder.verify(callServer2Monitor).succeeded("server 2 response");
    verifyNoMoreInteractions(requestDataMonitor, callServer1Monitor, callServer2Monitor);
    assertThat(output.get()).isEqualTo("server 2 response");
  }

  @Test
  public void basicMonitoringWithFailure() throws Exception {
    MonitoredComponent component =
        DaggerMonitoredComponent.builder()
            .monitoringModule(new MonitoringModule(componentMonitorFactory))
            .stubModule(new StubModule(server1, server2))
            .build();
    ListenableFuture<String> output = component.output();
    assertThat(componentMonitor.monitors).hasSize(3);
    ImmutableList<Map.Entry<ProducerToken, ProducerMonitor>> entries =
        ImmutableList.copyOf(componentMonitor.monitors.entrySet());
    assertThat(entries.get(0).getKey().toString()).contains("CallServer2");
    assertThat(entries.get(1).getKey().toString()).contains("CallServer1");
    assertThat(entries.get(2).getKey().toString()).contains("RequestData");

    ProducerMonitor callServer2Monitor = entries.get(0).getValue();
    ProducerMonitor callServer1Monitor = entries.get(1).getValue();
    ProducerMonitor requestDataMonitor = entries.get(2).getValue();

    InOrder inOrder = inOrder(requestDataMonitor, callServer1Monitor, callServer2Monitor);
    inOrder.verify(callServer2Monitor).requested();
    inOrder.verify(callServer1Monitor).requested();
    inOrder.verify(requestDataMonitor).requested();
    inOrder.verify(requestDataMonitor).ready();
    inOrder.verify(requestDataMonitor).methodStarting();
    inOrder.verify(requestDataMonitor).methodFinished();
    inOrder.verify(requestDataMonitor).succeeded("Hello, World!");
    inOrder.verify(callServer1Monitor).ready();
    inOrder.verify(callServer1Monitor).methodStarting();
    inOrder.verify(callServer1Monitor).methodFinished();
    verifyNoMoreInteractions(requestDataMonitor, callServer1Monitor, callServer2Monitor);

    RuntimeException cause = new RuntimeException("monkey");
    server1Future.setException(cause);
    inOrder.verify(callServer1Monitor).failed(cause);
    inOrder.verify(callServer2Monitor).ready();
    inOrder.verify(callServer2Monitor).failed(any(Throwable.class));
    verifyNoMoreInteractions(requestDataMonitor, callServer1Monitor, callServer2Monitor);
    try {
      output.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(Throwables.getRootCause(e)).isSameInstanceAs(cause);
    }
  }

  private static final class FakeProductionComponentMonitor extends ProductionComponentMonitor {
    final Map<ProducerToken, ProducerMonitor> monitors = new LinkedHashMap<>();

    @Override
    public ProducerMonitor producerMonitorFor(ProducerToken token) {
      ProducerMonitor monitor = mock(ProducerMonitor.class);
      monitors.put(token, monitor);
      return monitor;
    }
  }

  @Test
  public void monitoringWithThreads() throws Exception {
    ThreadRecordingProductionComponentMonitor componentMonitor =
        new ThreadRecordingProductionComponentMonitor();
    when(componentMonitorFactory.create(any())).thenReturn(componentMonitor);

    ThreadMonitoredComponent component =
        DaggerThreadMonitoredComponent.builder()
            .monitoringModule(new MonitoringModule(componentMonitorFactory))
            .executorModule(new ExecutorModule(Executors.newFixedThreadPool(10)))
            .build();
    ThreadAccumulator threadAccumulator = component.threadAccumulator().get();

    assertThat(componentMonitor.monitors).hasSize(3);
    ImmutableList<Map.Entry<ProducerToken, ThreadRecordingProducerMonitor>> entries =
        ImmutableList.copyOf(componentMonitor.monitors.entrySet());

    assertThat(entries.get(0).getKey().toString()).contains("EntryPoint");
    ThreadRecordingProducerMonitor entryPointMonitor = entries.get(0).getValue();
    assertThat(entries.get(1).getKey().toString()).contains("Required");
    ThreadRecordingProducerMonitor requiredMonitor = entries.get(1).getValue();
    assertThat(entries.get(2).getKey().toString()).contains("Deferred");
    ThreadRecordingProducerMonitor deferredMonitor = entries.get(2).getValue();

    // The entry point producer was requested from the main thread, then ran in its own thread.
    assertThat(entryPointMonitor.requestedThreadId).isEqualTo(Thread.currentThread().getId());
    assertThat(entryPointMonitor.startingThreadId)
        .isEqualTo(threadAccumulator.threadId("entryPoint"));
    assertThat(entryPointMonitor.finishedThreadId)
        .isEqualTo(threadAccumulator.threadId("entryPoint"));

    // The deferred producer was requested by the required producer, then ran in its own thread.
    assertThat(deferredMonitor.requestedThreadId).isEqualTo(threadAccumulator.threadId("required"));
    assertThat(deferredMonitor.startingThreadId).isEqualTo(threadAccumulator.threadId("deferred"));
    assertThat(deferredMonitor.finishedThreadId).isEqualTo(threadAccumulator.threadId("deferred"));

    // The required producer was requested by the entry point producer, then ran in its own thread.
    assertThat(requiredMonitor.requestedThreadId).isEqualTo(entryPointMonitor.requestedThreadId);
    assertThat(requiredMonitor.startingThreadId).isEqualTo(threadAccumulator.threadId("required"));
    assertThat(requiredMonitor.finishedThreadId).isEqualTo(threadAccumulator.threadId("required"));

    // Each producer ran in a distinct thread.
    ImmutableSet<Long> threadIds =
        ImmutableSet.of(
            Thread.currentThread().getId(),
            threadAccumulator.threadId("required"),
            threadAccumulator.threadId("deferred"),
            threadAccumulator.threadId("entryPoint"));
    assertThat(threadIds).hasSize(4);
  }

  private static final class ThreadRecordingProductionComponentMonitor
      extends ProductionComponentMonitor {
    final Map<ProducerToken, ThreadRecordingProducerMonitor> monitors = new LinkedHashMap<>();

    @Override
    public ProducerMonitor producerMonitorFor(ProducerToken token) {
      ThreadRecordingProducerMonitor monitor = new ThreadRecordingProducerMonitor();
      monitors.put(token, monitor);
      return monitor;
    }
  }

  private static final class ThreadRecordingProducerMonitor extends ProducerMonitor {
    private long requestedThreadId;
    private long startingThreadId;
    private long finishedThreadId;

    @Override
    public void requested() {
      requestedThreadId = Thread.currentThread().getId();
    }

    @Override
    public void methodStarting() {
      startingThreadId = Thread.currentThread().getId();
    }

    @Override
    public void methodFinished() {
      finishedThreadId = Thread.currentThread().getId();
    }
  }
}

/*
 * Copyright (C) 2015 Google, Inc.
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
package producerstest.monitoring;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

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
            .executor(MoreExecutors.directExecutor())
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
    inOrder.verify(requestDataMonitor).methodStarting();
    inOrder.verify(requestDataMonitor).methodFinished();
    inOrder.verify(requestDataMonitor).succeeded("Hello, World!");
    inOrder.verify(callServer1Monitor).methodStarting();
    inOrder.verify(callServer1Monitor).methodFinished();
    verifyNoMoreInteractions(requestDataMonitor, callServer1Monitor, callServer2Monitor);

    server1Future.set("server 1 response");
    inOrder.verify(callServer1Monitor).succeeded("server 1 response");
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
            .executor(MoreExecutors.directExecutor())
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
    inOrder.verify(requestDataMonitor).methodStarting();
    inOrder.verify(requestDataMonitor).methodFinished();
    inOrder.verify(requestDataMonitor).succeeded("Hello, World!");
    inOrder.verify(callServer1Monitor).methodStarting();
    inOrder.verify(callServer1Monitor).methodFinished();
    verifyNoMoreInteractions(requestDataMonitor, callServer1Monitor, callServer2Monitor);

    RuntimeException cause = new RuntimeException("monkey");
    server1Future.setException(cause);
    inOrder.verify(callServer1Monitor).failed(cause);
    inOrder.verify(callServer2Monitor).failed(any(Throwable.class));
    verifyNoMoreInteractions(requestDataMonitor, callServer1Monitor, callServer2Monitor);
    try {
      output.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(Throwables.getRootCause(e)).isSameAs(cause);
    }
  }

  private static final class FakeProductionComponentMonitor implements ProductionComponentMonitor {
    final Map<ProducerToken, ProducerMonitor> monitors = new LinkedHashMap<>();

    @Override
    public ProducerMonitor producerMonitorFor(ProducerToken token) {
      ProducerMonitor monitor = mock(ProducerMonitor.class);
      monitors.put(token, monitor);
      return monitor;
    }
  }
}

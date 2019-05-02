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

package dagger.producers.monitoring.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class MonitorsTest {
  @Mock private ProductionComponentMonitor.Factory mockProductionComponentMonitorFactory;
  @Mock private ProductionComponentMonitor mockProductionComponentMonitor;
  @Mock private ProducerMonitor mockProducerMonitor;
  @Mock private ProductionComponentMonitor.Factory mockProductionComponentMonitorFactoryA;
  @Mock private ProductionComponentMonitor.Factory mockProductionComponentMonitorFactoryB;
  @Mock private ProductionComponentMonitor.Factory mockProductionComponentMonitorFactoryC;
  @Mock private ProductionComponentMonitor mockProductionComponentMonitorA;
  @Mock private ProductionComponentMonitor mockProductionComponentMonitorB;
  @Mock private ProductionComponentMonitor mockProductionComponentMonitorC;
  @Mock private ProducerMonitor mockProducerMonitorA;
  @Mock private ProducerMonitor mockProducerMonitorB;
  @Mock private ProducerMonitor mockProducerMonitorC;

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void zeroMonitorsReturnsNoOp() {
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.<ProductionComponentMonitor.Factory>of());
    assertThat(factory).isSameInstanceAs(ProductionComponentMonitor.Factory.noOp());
  }

  @Test
  public void singleMonitor_nullProductionComponentMonitor() {
    when(mockProductionComponentMonitorFactory.create(any(Object.class))).thenReturn(null);
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(mockProductionComponentMonitorFactory));
    assertThat(factory.create(new Object())).isSameInstanceAs(ProductionComponentMonitor.noOp());
  }

  @Test
  public void singleMonitor_throwingProductionComponentMonitorFactory() {
    doThrow(new RuntimeException("monkey"))
        .when(mockProductionComponentMonitorFactory)
        .create(any(Object.class));
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(mockProductionComponentMonitorFactory));
    assertThat(factory.create(new Object())).isSameInstanceAs(ProductionComponentMonitor.noOp());
  }

  @Test
  public void singleMonitor_nullProducerMonitor() {
    when(mockProductionComponentMonitorFactory.create(any(Object.class)))
        .thenReturn(mockProductionComponentMonitor);
    when(mockProductionComponentMonitor.producerMonitorFor(any(ProducerToken.class)))
        .thenReturn(null);
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(mockProductionComponentMonitorFactory));
    ProductionComponentMonitor monitor = factory.create(new Object());
    assertThat(monitor.producerMonitorFor(ProducerToken.create(Object.class)))
        .isSameInstanceAs(ProducerMonitor.noOp());
  }

  @Test
  public void singleMonitor_throwingProductionComponentMonitor() {
    when(mockProductionComponentMonitorFactory.create(any(Object.class)))
        .thenReturn(mockProductionComponentMonitor);
    doThrow(new RuntimeException("monkey"))
        .when(mockProductionComponentMonitor)
        .producerMonitorFor(any(ProducerToken.class));
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(mockProductionComponentMonitorFactory));
    ProductionComponentMonitor monitor = factory.create(new Object());
    assertThat(monitor.producerMonitorFor(ProducerToken.create(Object.class)))
        .isSameInstanceAs(ProducerMonitor.noOp());
  }

  @Test
  public void singleMonitor_normalProducerMonitorSuccess() {
    setUpNormalSingleMonitor();
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(mockProductionComponentMonitorFactory));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));
    Object o = new Object();
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.succeeded(o);

    InOrder order = inOrder(mockProducerMonitor);
    order.verify(mockProducerMonitor).requested();
    order.verify(mockProducerMonitor).methodStarting();
    order.verify(mockProducerMonitor).methodFinished();
    order.verify(mockProducerMonitor).succeeded(o);
    verifyNoMoreInteractions(mockProducerMonitor);
  }

  @Test
  public void singleMonitor_normalProducerMonitorFailure() {
    setUpNormalSingleMonitor();
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(mockProductionComponentMonitorFactory));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));
    Throwable t = new RuntimeException("monkey");
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.failed(t);

    InOrder order = inOrder(mockProducerMonitor);
    order.verify(mockProducerMonitor).requested();
    order.verify(mockProducerMonitor).methodStarting();
    order.verify(mockProducerMonitor).methodFinished();
    order.verify(mockProducerMonitor).failed(t);
    verifyNoMoreInteractions(mockProducerMonitor);
  }

  @Test
  public void singleMonitor_throwingProducerMonitorSuccess() {
    setUpNormalSingleMonitor();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitor).requested();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitor).methodStarting();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitor).methodFinished();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitor).succeeded(any(Object.class));
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(mockProductionComponentMonitorFactory));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));
    Object o = new Object();
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.succeeded(o);

    InOrder order = inOrder(mockProducerMonitor);
    order.verify(mockProducerMonitor).requested();
    order.verify(mockProducerMonitor).methodStarting();
    order.verify(mockProducerMonitor).methodFinished();
    order.verify(mockProducerMonitor).succeeded(o);
    verifyNoMoreInteractions(mockProducerMonitor);
  }

  @Test
  public void singleMonitor_throwingProducerMonitorFailure() {
    setUpNormalSingleMonitor();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitor).requested();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitor).methodStarting();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitor).methodFinished();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitor).failed(any(Throwable.class));
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(mockProductionComponentMonitorFactory));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));
    Throwable t = new RuntimeException("gorilla");
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.failed(t);

    InOrder order = inOrder(mockProducerMonitor);
    order.verify(mockProducerMonitor).requested();
    order.verify(mockProducerMonitor).methodStarting();
    order.verify(mockProducerMonitor).methodFinished();
    order.verify(mockProducerMonitor).failed(t);
    verifyNoMoreInteractions(mockProducerMonitor);
  }

  @Test
  public void multipleMonitors_nullProductionComponentMonitors() {
    when(mockProductionComponentMonitorFactoryA.create(any(Object.class))).thenReturn(null);
    when(mockProductionComponentMonitorFactoryB.create(any(Object.class))).thenReturn(null);
    when(mockProductionComponentMonitorFactoryC.create(any(Object.class))).thenReturn(null);
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(
                mockProductionComponentMonitorFactoryA,
                mockProductionComponentMonitorFactoryB,
                mockProductionComponentMonitorFactoryC));
    assertThat(factory.create(new Object())).isSameInstanceAs(ProductionComponentMonitor.noOp());
  }

  @Test
  public void multipleMonitors_throwingProductionComponentMonitorFactories() {
    doThrow(new RuntimeException("monkey"))
        .when(mockProductionComponentMonitorFactoryA)
        .create(any(Object.class));
    doThrow(new RuntimeException("monkey"))
        .when(mockProductionComponentMonitorFactoryB)
        .create(any(Object.class));
    doThrow(new RuntimeException("monkey"))
        .when(mockProductionComponentMonitorFactoryC)
        .create(any(Object.class));
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(
                mockProductionComponentMonitorFactoryA,
                mockProductionComponentMonitorFactoryB,
                mockProductionComponentMonitorFactoryC));
    assertThat(factory.create(new Object())).isSameInstanceAs(ProductionComponentMonitor.noOp());
  }

  @Test
  public void multipleMonitors_someNullProductionComponentMonitors() {
    when(mockProductionComponentMonitorFactoryA.create(any(Object.class)))
        .thenReturn(mockProductionComponentMonitorA);
    when(mockProductionComponentMonitorFactoryB.create(any(Object.class))).thenReturn(null);
    when(mockProductionComponentMonitorFactoryC.create(any(Object.class))).thenReturn(null);
    when(mockProductionComponentMonitorA.producerMonitorFor(any(ProducerToken.class)))
        .thenReturn(mockProducerMonitorA);
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(
                mockProductionComponentMonitorFactoryA,
                mockProductionComponentMonitorFactoryB,
                mockProductionComponentMonitorFactoryC));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));

    Object o = new Object();
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.succeeded(o);

    InOrder order = inOrder(mockProducerMonitorA);
    order.verify(mockProducerMonitorA).requested();
    order.verify(mockProducerMonitorA).methodStarting();
    order.verify(mockProducerMonitorA).methodFinished();
    order.verify(mockProducerMonitorA).succeeded(o);
    verifyNoMoreInteractions(mockProducerMonitorA);
  }

  @Test
  public void multipleMonitors_someThrowingProductionComponentMonitorFactories() {
    when(mockProductionComponentMonitorFactoryA.create(any(Object.class)))
        .thenReturn(mockProductionComponentMonitorA);
    doThrow(new RuntimeException("monkey"))
        .when(mockProductionComponentMonitorFactoryB)
        .create(any(Object.class));
    doThrow(new RuntimeException("monkey"))
        .when(mockProductionComponentMonitorFactoryC)
        .create(any(Object.class));
    when(mockProductionComponentMonitorA.producerMonitorFor(any(ProducerToken.class)))
        .thenReturn(mockProducerMonitorA);
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(
                mockProductionComponentMonitorFactoryA,
                mockProductionComponentMonitorFactoryB,
                mockProductionComponentMonitorFactoryC));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));

    Object o = new Object();
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.succeeded(o);

    InOrder order = inOrder(mockProducerMonitorA);
    order.verify(mockProducerMonitorA).requested();
    order.verify(mockProducerMonitorA).methodStarting();
    order.verify(mockProducerMonitorA).methodFinished();
    order.verify(mockProducerMonitorA).succeeded(o);
    verifyNoMoreInteractions(mockProducerMonitorA);
  }

  @Test
  public void multipleMonitors_normalProductionComponentMonitorSuccess() {
    setUpNormalMultipleMonitors();
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(
                mockProductionComponentMonitorFactoryA,
                mockProductionComponentMonitorFactoryB,
                mockProductionComponentMonitorFactoryC));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));

    Object o = new Object();
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.succeeded(o);

    InOrder order = inOrder(mockProducerMonitorA, mockProducerMonitorB, mockProducerMonitorC);
    order.verify(mockProducerMonitorA).requested();
    order.verify(mockProducerMonitorB).requested();
    order.verify(mockProducerMonitorC).requested();
    order.verify(mockProducerMonitorA).methodStarting();
    order.verify(mockProducerMonitorB).methodStarting();
    order.verify(mockProducerMonitorC).methodStarting();
    order.verify(mockProducerMonitorC).methodFinished();
    order.verify(mockProducerMonitorB).methodFinished();
    order.verify(mockProducerMonitorA).methodFinished();
    order.verify(mockProducerMonitorC).succeeded(o);
    order.verify(mockProducerMonitorB).succeeded(o);
    order.verify(mockProducerMonitorA).succeeded(o);
    verifyNoMoreInteractions(mockProducerMonitorA, mockProducerMonitorB, mockProducerMonitorC);
  }

  @Test
  public void multipleMonitors_normalProductionComponentMonitorFailure() {
    setUpNormalMultipleMonitors();
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(
                mockProductionComponentMonitorFactoryA,
                mockProductionComponentMonitorFactoryB,
                mockProductionComponentMonitorFactoryC));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));

    Throwable t = new RuntimeException("chimpanzee");
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.failed(t);

    InOrder order = inOrder(mockProducerMonitorA, mockProducerMonitorB, mockProducerMonitorC);
    order.verify(mockProducerMonitorA).requested();
    order.verify(mockProducerMonitorB).requested();
    order.verify(mockProducerMonitorC).requested();
    order.verify(mockProducerMonitorA).methodStarting();
    order.verify(mockProducerMonitorB).methodStarting();
    order.verify(mockProducerMonitorC).methodStarting();
    order.verify(mockProducerMonitorC).methodFinished();
    order.verify(mockProducerMonitorB).methodFinished();
    order.verify(mockProducerMonitorA).methodFinished();
    order.verify(mockProducerMonitorC).failed(t);
    order.verify(mockProducerMonitorB).failed(t);
    order.verify(mockProducerMonitorA).failed(t);
    verifyNoMoreInteractions(mockProducerMonitorA, mockProducerMonitorB, mockProducerMonitorC);
  }

  @Test
  public void multipleMonitors_someThrowingProducerMonitorsSuccess() {
    setUpNormalMultipleMonitors();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitorA).requested();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitorA).methodStarting();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitorB).methodFinished();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitorC).succeeded(any(Object.class));
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(
                mockProductionComponentMonitorFactoryA,
                mockProductionComponentMonitorFactoryB,
                mockProductionComponentMonitorFactoryC));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));

    Object o = new Object();
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.succeeded(o);

    InOrder order = inOrder(mockProducerMonitorA, mockProducerMonitorB, mockProducerMonitorC);
    order.verify(mockProducerMonitorA).requested();
    order.verify(mockProducerMonitorB).requested();
    order.verify(mockProducerMonitorC).requested();
    order.verify(mockProducerMonitorA).methodStarting();
    order.verify(mockProducerMonitorB).methodStarting();
    order.verify(mockProducerMonitorC).methodStarting();
    order.verify(mockProducerMonitorC).methodFinished();
    order.verify(mockProducerMonitorB).methodFinished();
    order.verify(mockProducerMonitorA).methodFinished();
    order.verify(mockProducerMonitorC).succeeded(o);
    order.verify(mockProducerMonitorB).succeeded(o);
    order.verify(mockProducerMonitorA).succeeded(o);
    verifyNoMoreInteractions(mockProducerMonitorA, mockProducerMonitorB, mockProducerMonitorC);
  }

  @Test
  public void multipleMonitors_someThrowingProducerMonitorsFailure() {
    setUpNormalMultipleMonitors();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitorA).requested();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitorA).methodStarting();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitorB).methodFinished();
    doThrow(new RuntimeException("monkey")).when(mockProducerMonitorC).failed(any(Throwable.class));
    ProductionComponentMonitor.Factory factory =
        Monitors.delegatingProductionComponentMonitorFactory(
            ImmutableList.of(
                mockProductionComponentMonitorFactoryA,
                mockProductionComponentMonitorFactoryB,
                mockProductionComponentMonitorFactoryC));
    ProductionComponentMonitor monitor = factory.create(new Object());
    ProducerMonitor producerMonitor =
        monitor.producerMonitorFor(ProducerToken.create(Object.class));

    Throwable t = new RuntimeException("chimpanzee");
    producerMonitor.requested();
    producerMonitor.methodStarting();
    producerMonitor.methodFinished();
    producerMonitor.failed(t);

    InOrder order = inOrder(mockProducerMonitorA, mockProducerMonitorB, mockProducerMonitorC);
    order.verify(mockProducerMonitorA).requested();
    order.verify(mockProducerMonitorB).requested();
    order.verify(mockProducerMonitorC).requested();
    order.verify(mockProducerMonitorA).methodStarting();
    order.verify(mockProducerMonitorB).methodStarting();
    order.verify(mockProducerMonitorC).methodStarting();
    order.verify(mockProducerMonitorC).methodFinished();
    order.verify(mockProducerMonitorB).methodFinished();
    order.verify(mockProducerMonitorA).methodFinished();
    order.verify(mockProducerMonitorC).failed(t);
    order.verify(mockProducerMonitorB).failed(t);
    order.verify(mockProducerMonitorA).failed(t);
    verifyNoMoreInteractions(mockProducerMonitorA, mockProducerMonitorB, mockProducerMonitorC);
  }

  private void setUpNormalSingleMonitor() {
    when(mockProductionComponentMonitorFactory.create(any(Object.class)))
        .thenReturn(mockProductionComponentMonitor);
    when(mockProductionComponentMonitor.producerMonitorFor(any(ProducerToken.class)))
        .thenReturn(mockProducerMonitor);
  }

  private void setUpNormalMultipleMonitors() {
    when(mockProductionComponentMonitorFactoryA.create(any(Object.class)))
        .thenReturn(mockProductionComponentMonitorA);
    when(mockProductionComponentMonitorFactoryB.create(any(Object.class)))
        .thenReturn(mockProductionComponentMonitorB);
    when(mockProductionComponentMonitorFactoryC.create(any(Object.class)))
        .thenReturn(mockProductionComponentMonitorC);
    when(mockProductionComponentMonitorA.producerMonitorFor(any(ProducerToken.class)))
        .thenReturn(mockProducerMonitorA);
    when(mockProductionComponentMonitorB.producerMonitorFor(any(ProducerToken.class)))
        .thenReturn(mockProducerMonitorB);
    when(mockProductionComponentMonitorC.producerMonitorFor(any(ProducerToken.class)))
        .thenReturn(mockProducerMonitorC);
  }
}

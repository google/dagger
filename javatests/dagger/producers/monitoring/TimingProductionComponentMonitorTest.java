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

package dagger.producers.monitoring;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.testing.FakeTicker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class TimingProductionComponentMonitorTest {
  private static final class ProducerClassA {}

  private static final class ProducerClassB {}

  @Mock private ProductionComponentTimingRecorder.Factory productionComponentTimingRecorderFactory;
  @Mock private ProductionComponentTimingRecorder productionComponentTimingRecorder;
  @Mock private ProducerTimingRecorder producerTimingRecorderA;
  @Mock private ProducerTimingRecorder producerTimingRecorderB;

  private FakeTicker ticker;
  private ProductionComponentMonitor.Factory monitorFactory;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(productionComponentTimingRecorderFactory.create(any(Object.class)))
        .thenReturn(productionComponentTimingRecorder);
    when(
            productionComponentTimingRecorder.producerTimingRecorderFor(
                ProducerToken.create(ProducerClassA.class)))
        .thenReturn(producerTimingRecorderA);
    when(
            productionComponentTimingRecorder.producerTimingRecorderFor(
                ProducerToken.create(ProducerClassB.class)))
        .thenReturn(producerTimingRecorderB);
    ticker = new FakeTicker();
    monitorFactory =
        new TimingProductionComponentMonitor.Factory(
            productionComponentTimingRecorderFactory, ticker);
  }

  @Test
  public void normalExecution_success() {
    ProductionComponentMonitor monitor = monitorFactory.create(new Object());
    ProducerMonitor producerMonitorA =
        monitor.producerMonitorFor(ProducerToken.create(ProducerClassA.class));
    ticker.advance(5000222);
    producerMonitorA.methodStarting();
    ticker.advance(1333);
    producerMonitorA.methodFinished();
    ticker.advance(40000555);
    ProducerMonitor producerMonitorB =
        monitor.producerMonitorFor(ProducerToken.create(ProducerClassB.class));
    producerMonitorB.methodStarting();
    ticker.advance(2000777);
    producerMonitorA.succeeded(new Object());
    ticker.advance(3000999);
    producerMonitorB.methodFinished();
    ticker.advance(100000222);
    producerMonitorB.succeeded(new Object());

    verify(producerTimingRecorderA).recordMethod(5000222, 1333);
    verify(producerTimingRecorderA).recordSuccess(1333 + 40000555 + 2000777);
    verify(producerTimingRecorderB).recordMethod(5000222 + 1333 + 40000555, 2000777 + 3000999);
    verify(producerTimingRecorderB).recordSuccess(2000777 + 3000999 + 100000222);
    verifyNoMoreInteractions(producerTimingRecorderA, producerTimingRecorderB);
  }

  @Test
  public void normalExecution_failure() {
    Throwable failureA = new RuntimeException("monkey");
    Throwable failureB = new RuntimeException("gorilla");
    ProductionComponentMonitor monitor = monitorFactory.create(new Object());
    ProducerMonitor producerMonitorA =
        monitor.producerMonitorFor(ProducerToken.create(ProducerClassA.class));
    ticker.advance(5000222);
    producerMonitorA.methodStarting();
    ticker.advance(1333);
    producerMonitorA.methodFinished();
    ticker.advance(40000555);
    ProducerMonitor producerMonitorB =
        monitor.producerMonitorFor(ProducerToken.create(ProducerClassB.class));
    producerMonitorB.methodStarting();
    ticker.advance(2000777);
    producerMonitorA.failed(failureA);
    ticker.advance(3000999);
    producerMonitorB.methodFinished();
    ticker.advance(100000222);
    producerMonitorB.failed(failureB);

    verify(producerTimingRecorderA).recordMethod(5000222, 1333);
    verify(producerTimingRecorderA).recordFailure(failureA, 1333 + 40000555 + 2000777);
    verify(producerTimingRecorderB).recordMethod(5000222 + 1333 + 40000555, 2000777 + 3000999);
    verify(producerTimingRecorderB).recordFailure(failureB, 2000777 + 3000999 + 100000222);
    verifyNoMoreInteractions(producerTimingRecorderA, producerTimingRecorderB);
  }
}

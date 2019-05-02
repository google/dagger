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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public final class TimingRecordersTest {
  @Mock
  private ProductionComponentTimingRecorder.Factory mockProductionComponentTimingRecorderFactory;

  @Mock private ProductionComponentTimingRecorder mockProductionComponentTimingRecorder;
  @Mock private ProducerTimingRecorder mockProducerTimingRecorder;

  @Mock
  private ProductionComponentTimingRecorder.Factory mockProductionComponentTimingRecorderFactoryA;

  @Mock
  private ProductionComponentTimingRecorder.Factory mockProductionComponentTimingRecorderFactoryB;

  @Mock
  private ProductionComponentTimingRecorder.Factory mockProductionComponentTimingRecorderFactoryC;

  @Mock private ProductionComponentTimingRecorder mockProductionComponentTimingRecorderA;
  @Mock private ProductionComponentTimingRecorder mockProductionComponentTimingRecorderB;
  @Mock private ProductionComponentTimingRecorder mockProductionComponentTimingRecorderC;
  @Mock private ProducerTimingRecorder mockProducerTimingRecorderA;
  @Mock private ProducerTimingRecorder mockProducerTimingRecorderB;
  @Mock private ProducerTimingRecorder mockProducerTimingRecorderC;

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void zeroRecordersReturnsNoOp() {
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.<ProductionComponentTimingRecorder.Factory>of());
    assertThat(factory)
        .isSameInstanceAs(TimingRecorders.noOpProductionComponentTimingRecorderFactory());
  }

  @Test
  public void singleRecorder_nullProductionComponentTimingRecorder() {
    when(mockProductionComponentTimingRecorderFactory.create(any(Object.class))).thenReturn(null);
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(mockProductionComponentTimingRecorderFactory));
    assertThat(factory.create(new Object()))
        .isSameInstanceAs(TimingRecorders.noOpProductionComponentTimingRecorder());
  }

  @Test
  public void singleRecorder_throwingProductionComponentTimingRecorderFactory() {
    when(mockProductionComponentTimingRecorderFactory.create(any(Object.class)))
        .thenThrow(new RuntimeException("monkey"));
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(mockProductionComponentTimingRecorderFactory));
    assertThat(factory.create(new Object()))
        .isSameInstanceAs(TimingRecorders.noOpProductionComponentTimingRecorder());
  }

  @Test
  public void singleRecorder_nullProducerTimingRecorder() {
    when(mockProductionComponentTimingRecorderFactory.create(any(Object.class)))
        .thenReturn(mockProductionComponentTimingRecorder);
    when(mockProductionComponentTimingRecorder.producerTimingRecorderFor(any(ProducerToken.class)))
        .thenReturn(null);
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(mockProductionComponentTimingRecorderFactory));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    assertThat(recorder.producerTimingRecorderFor(ProducerToken.create(Object.class)))
        .isSameInstanceAs(ProducerTimingRecorder.noOp());
  }

  @Test
  public void singleRecorder_throwingProductionComponentTimingRecorder() {
    when(mockProductionComponentTimingRecorderFactory.create(any(Object.class)))
        .thenReturn(mockProductionComponentTimingRecorder);
    when(mockProductionComponentTimingRecorder.producerTimingRecorderFor(any(ProducerToken.class)))
        .thenThrow(new RuntimeException("monkey"));
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(mockProductionComponentTimingRecorderFactory));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    assertThat(recorder.producerTimingRecorderFor(ProducerToken.create(Object.class)))
        .isSameInstanceAs(ProducerTimingRecorder.noOp());
  }

  @Test
  public void singleRecorder_normalProducerTimingRecorderSuccess() {
    setUpNormalSingleRecorder();
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(mockProductionComponentTimingRecorderFactory));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    ProducerTimingRecorder producerTimingRecorder =
        recorder.producerTimingRecorderFor(ProducerToken.create(Object.class));
    producerTimingRecorder.recordMethod(15, 42);
    producerTimingRecorder.recordSuccess(100);

    InOrder order = inOrder(mockProducerTimingRecorder);
    order.verify(mockProducerTimingRecorder).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorder).recordSuccess(100);
    verifyNoMoreInteractions(mockProducerTimingRecorder);
  }

  @Test
  public void singleRecorder_normalProducerTimingRecorderFailure() {
    setUpNormalSingleRecorder();
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(mockProductionComponentTimingRecorderFactory));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    ProducerTimingRecorder producerTimingRecorder =
        recorder.producerTimingRecorderFor(ProducerToken.create(Object.class));
    Throwable t = new RuntimeException("monkey");
    producerTimingRecorder.recordMethod(15, 42);
    producerTimingRecorder.recordFailure(t, 100);

    InOrder order = inOrder(mockProducerTimingRecorder);
    order.verify(mockProducerTimingRecorder).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorder).recordFailure(t, 100);
    verifyNoMoreInteractions(mockProducerTimingRecorder);
  }

  @Test
  public void singleRecorder_throwingProducerTimingRecorderSuccess() {
    setUpNormalSingleRecorder();
    doThrow(new RuntimeException("monkey"))
        .when(mockProducerTimingRecorder)
        .recordMethod(anyLong(), anyLong());
    doThrow(new RuntimeException("monkey"))
        .when(mockProducerTimingRecorder)
        .recordSuccess(anyLong());
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(mockProductionComponentTimingRecorderFactory));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    ProducerTimingRecorder producerTimingRecorder =
        recorder.producerTimingRecorderFor(ProducerToken.create(Object.class));
    producerTimingRecorder.recordMethod(15, 42);
    producerTimingRecorder.recordSuccess(100);

    InOrder order = inOrder(mockProducerTimingRecorder);
    order.verify(mockProducerTimingRecorder).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorder).recordSuccess(100);
    verifyNoMoreInteractions(mockProducerTimingRecorder);
  }

  @Test
  public void multipleRecorders_nullProductionComponentTimingRecorders() {
    when(mockProductionComponentTimingRecorderFactoryA.create(any(Object.class))).thenReturn(null);
    when(mockProductionComponentTimingRecorderFactoryB.create(any(Object.class))).thenReturn(null);
    when(mockProductionComponentTimingRecorderFactoryC.create(any(Object.class))).thenReturn(null);
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(
                mockProductionComponentTimingRecorderFactoryA,
                mockProductionComponentTimingRecorderFactoryB,
                mockProductionComponentTimingRecorderFactoryC));
    assertThat(factory.create(new Object()))
        .isSameInstanceAs(TimingRecorders.noOpProductionComponentTimingRecorder());
  }

  @Test
  public void multipleRecorders_throwingProductionComponentTimingRecorderFactories() {
    when(mockProductionComponentTimingRecorderFactoryA.create(any(Object.class)))
        .thenThrow(new RuntimeException("monkey"));
    when(mockProductionComponentTimingRecorderFactoryB.create(any(Object.class)))
        .thenThrow(new RuntimeException("monkey"));
    when(mockProductionComponentTimingRecorderFactoryC.create(any(Object.class)))
        .thenThrow(new RuntimeException("monkey"));
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(
                mockProductionComponentTimingRecorderFactoryA,
                mockProductionComponentTimingRecorderFactoryB,
                mockProductionComponentTimingRecorderFactoryC));
    assertThat(factory.create(new Object()))
        .isSameInstanceAs(TimingRecorders.noOpProductionComponentTimingRecorder());
  }

  @Test
  public void multipleRecorders_someNullProductionComponentTimingRecorders() {
    when(mockProductionComponentTimingRecorderFactoryA.create(any(Object.class)))
        .thenReturn(mockProductionComponentTimingRecorderA);
    when(mockProductionComponentTimingRecorderFactoryB.create(any(Object.class))).thenReturn(null);
    when(mockProductionComponentTimingRecorderFactoryC.create(any(Object.class))).thenReturn(null);
    when(mockProductionComponentTimingRecorderA.producerTimingRecorderFor(any(ProducerToken.class)))
        .thenReturn(mockProducerTimingRecorderA);
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(
                mockProductionComponentTimingRecorderFactoryA,
                mockProductionComponentTimingRecorderFactoryB,
                mockProductionComponentTimingRecorderFactoryC));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    ProducerTimingRecorder producerTimingRecorder =
        recorder.producerTimingRecorderFor(ProducerToken.create(Object.class));

    producerTimingRecorder.recordMethod(15, 42);
    producerTimingRecorder.recordSuccess(100);

    InOrder order = inOrder(mockProducerTimingRecorderA);
    order.verify(mockProducerTimingRecorderA).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorderA).recordSuccess(100);
    verifyNoMoreInteractions(mockProducerTimingRecorderA);
  }

  @Test
  public void multipleRecorders_someThrowingProductionComponentTimingRecorderFactories() {
    when(mockProductionComponentTimingRecorderFactoryA.create(any(Object.class)))
        .thenReturn(mockProductionComponentTimingRecorderA);
    when(mockProductionComponentTimingRecorderFactoryB.create(any(Object.class)))
        .thenThrow(new RuntimeException("monkey"));
    when(mockProductionComponentTimingRecorderFactoryC.create(any(Object.class)))
        .thenThrow(new RuntimeException("monkey"));
    when(mockProductionComponentTimingRecorderA.producerTimingRecorderFor(any(ProducerToken.class)))
        .thenReturn(mockProducerTimingRecorderA);
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(
                mockProductionComponentTimingRecorderFactoryA,
                mockProductionComponentTimingRecorderFactoryB,
                mockProductionComponentTimingRecorderFactoryC));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    ProducerTimingRecorder producerTimingRecorder =
        recorder.producerTimingRecorderFor(ProducerToken.create(Object.class));

    producerTimingRecorder.recordMethod(15, 42);
    producerTimingRecorder.recordSuccess(100);

    InOrder order = inOrder(mockProducerTimingRecorderA);
    order.verify(mockProducerTimingRecorderA).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorderA).recordSuccess(100);
    verifyNoMoreInteractions(mockProducerTimingRecorderA);
  }

  @Test
  public void multipleRecorders_normalProductionComponentTimingRecorderSuccess() {
    setUpNormalMultipleRecorders();
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(
                mockProductionComponentTimingRecorderFactoryA,
                mockProductionComponentTimingRecorderFactoryB,
                mockProductionComponentTimingRecorderFactoryC));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    ProducerTimingRecorder producerTimingRecorder =
        recorder.producerTimingRecorderFor(ProducerToken.create(Object.class));

    producerTimingRecorder.recordMethod(15, 42);
    producerTimingRecorder.recordSuccess(100);

    InOrder order =
        inOrder(
            mockProducerTimingRecorderA, mockProducerTimingRecorderB, mockProducerTimingRecorderC);
    order.verify(mockProducerTimingRecorderA).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorderB).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorderC).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorderA).recordSuccess(100);
    order.verify(mockProducerTimingRecorderB).recordSuccess(100);
    order.verify(mockProducerTimingRecorderC).recordSuccess(100);
    verifyNoMoreInteractions(
        mockProducerTimingRecorderA, mockProducerTimingRecorderB, mockProducerTimingRecorderC);
  }

  @Test
  public void multipleRecorders_someThrowingProducerTimingRecordersSuccess() {
    setUpNormalMultipleRecorders();
    doThrow(new RuntimeException("monkey"))
        .when(mockProducerTimingRecorderA)
        .recordMethod(anyLong(), anyLong());
    doThrow(new RuntimeException("monkey"))
        .when(mockProducerTimingRecorderB)
        .recordSuccess(anyLong());
    doThrow(new RuntimeException("monkey"))
        .when(mockProducerTimingRecorderC)
        .recordMethod(anyLong(), anyLong());
    ProductionComponentTimingRecorder.Factory factory =
        TimingRecorders.delegatingProductionComponentTimingRecorderFactory(
            ImmutableList.of(
                mockProductionComponentTimingRecorderFactoryA,
                mockProductionComponentTimingRecorderFactoryB,
                mockProductionComponentTimingRecorderFactoryC));
    ProductionComponentTimingRecorder recorder = factory.create(new Object());
    ProducerTimingRecorder producerTimingRecorder =
        recorder.producerTimingRecorderFor(ProducerToken.create(Object.class));

    producerTimingRecorder.recordMethod(15, 42);
    producerTimingRecorder.recordSuccess(100);

    InOrder order =
        inOrder(
            mockProducerTimingRecorderA, mockProducerTimingRecorderB, mockProducerTimingRecorderC);
    order.verify(mockProducerTimingRecorderA).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorderB).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorderC).recordMethod(15, 42);
    order.verify(mockProducerTimingRecorderA).recordSuccess(100);
    order.verify(mockProducerTimingRecorderB).recordSuccess(100);
    order.verify(mockProducerTimingRecorderC).recordSuccess(100);
    verifyNoMoreInteractions(
        mockProducerTimingRecorderA, mockProducerTimingRecorderB, mockProducerTimingRecorderC);
  }

  private void setUpNormalSingleRecorder() {
    when(mockProductionComponentTimingRecorderFactory.create(any(Object.class)))
        .thenReturn(mockProductionComponentTimingRecorder);
    when(mockProductionComponentTimingRecorder.producerTimingRecorderFor(any(ProducerToken.class)))
        .thenReturn(mockProducerTimingRecorder);
  }

  private void setUpNormalMultipleRecorders() {
    when(mockProductionComponentTimingRecorderFactoryA.create(any(Object.class)))
        .thenReturn(mockProductionComponentTimingRecorderA);
    when(mockProductionComponentTimingRecorderFactoryB.create(any(Object.class)))
        .thenReturn(mockProductionComponentTimingRecorderB);
    when(mockProductionComponentTimingRecorderFactoryC.create(any(Object.class)))
        .thenReturn(mockProductionComponentTimingRecorderC);
    when(mockProductionComponentTimingRecorderA.producerTimingRecorderFor(any(ProducerToken.class)))
        .thenReturn(mockProducerTimingRecorderA);
    when(mockProductionComponentTimingRecorderB.producerTimingRecorderFor(any(ProducerToken.class)))
        .thenReturn(mockProducerTimingRecorderB);
    when(mockProductionComponentTimingRecorderC.producerTimingRecorderFor(any(ProducerToken.class)))
        .thenReturn(mockProducerTimingRecorderC);
  }
}

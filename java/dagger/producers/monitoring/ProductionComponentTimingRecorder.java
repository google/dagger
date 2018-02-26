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

import dagger.producers.Produces;
import dagger.producers.ProductionComponent;

/**
 * A hook for recording timing of the execution of
 * {@linkplain ProductionComponent production components}. To install a
 * {@code ProductionComponentTimingRecorder}, contribute to a set binding of
 * {@code ProductionComponentTimingRecorder.Factory}, and include the {@code TimingMonitorModule} to
 * the component. The factory will be asked to create one timing recorder for the component, and the
 * resulting instance will be used to create individual timing recorders for producers.
 *
 * <p>If any of these methods throw, then the exception will be logged, and the framework will act
 * as though a no-op timing recorder was returned.
 *
 * @since 2.1
 */
public interface ProductionComponentTimingRecorder {
  /** Returns a timing recorder for an individual {@linkplain Produces producer method}. */
  ProducerTimingRecorder producerTimingRecorderFor(ProducerToken token);

  public interface Factory {
    /** Creates a component-specific timing recorder when the component is created. */
    ProductionComponentTimingRecorder create(Object component);
  }
}

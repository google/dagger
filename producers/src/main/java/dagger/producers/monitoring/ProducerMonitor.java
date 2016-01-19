/*
 * Copyright (C) 2015 Google Inc.
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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import dagger.producers.Produces;

/**
 * A hook for monitoring the execution of individual {@linkplain Produces producer methods}. See
 * {@link ProductionComponentMonitor} for how to install these monitors.
 *
 * <p>The lifecycle of the monitor, under normal conditions, is:
 * <ul>
 *   <li>{@link #requested()}
 *   <li>{@link #methodStarting()}
 *   <li>The method is called
 *   <li>{@link #methodFinished()}
 *   <li>If the method returns a value, then:
 *   <ul>
 *     <li>{@link #succeeded(Object)} if the method returned normally; or
 *     <li>{@link #failed(Throwable)} if the method threw an exception.
 *   </ul>
 *   <li>If the method returns a future, then:
 *   <ul>
 *     <li>{@link #succeeded(Object)} if the method returned normally, and the future succeeded; or
 *     <li>{@link #failed(Throwable)} if the method threw an exception, or returned normally and the
 *         future failed.
 *   </ul>
 * </ul>
 *
 * <p>If any input to the monitored producer fails, {@link #failed(Throwable)} will be called
 * immediately with the failed input's exception. If more than one input fails, an arbitrary failed
 * input's exception is used.
 *
 * <p>For example, given an entry point A that depends on B, which depends on C, when the entry
 * point A is called, this will trigger the following sequence of events, assuming all methods and
 * futures complete successfully:
 * <ul>
 *   <li>A requested
 *   <li>B requested
 *   <li>C requested
 *   <li>C methodStarting
 *   <li>C methodFinished
 *   <li>C succeeded
 *   <li>B methodStarting
 *   <li>B methodFinished
 *   <li>B succeeded
 *   <li>A methodStarting
 *   <li>A methodFinished
 *   <li>A succeeded
 * </ul>
 *
 * <p>If any of the monitor's methods throw, then the exception will be logged and processing will
 * continue unaffected.
 *
 * @author Jesse Beder
 */
public abstract class ProducerMonitor {
  /**
   * Called when the producer's output is requested; that is, when the first method is called that
   * requires the production of this producer's output.
   *
   * <p>Note that if a method depends on {@link Producer Producer<T>}, then this does not count as
   * requesting {@code T}; that is only triggered by calling {@link Producer#get()}.
   *
   * <p>When multiple monitors are installed, the order that each monitor will call this method is
   * unspecified, but will remain consistent throughout the course of the execution of a component.
   */
  public void requested() {}

  /**
   * Called when the producer method is about to start executing.
   *
   * <p>When multiple monitors are installed, calls to this method will be in the reverse order from
   * calls to {@link #requested()}.
   */
  public void methodStarting() {}

  /**
   * Called when the producer method has finished executing.
   *
   * <p>When multiple monitors are installed, calls to this method will be in the reverse order from
   * calls to {@link #requested()}.
   */
  public void methodFinished() {}

  /**
   * Called when the producer’s future has completed successfully with a value.
   *
   * <p>When multiple monitors are installed, calls to this method will be in the reverse order from
   * calls to {@link #requested()}.
   */
  public void succeeded(Object o) {}

  /**
   * Called when the producer's future has failed with an exception.
   *
   * <p>When multiple monitors are installed, calls to this method will be in the reverse order from
   * calls to {@link #requested()}.
   */
  public void failed(Throwable t) {}

  /**
   * Adds this monitor's completion methods as a callback to the future. This is only intended to be
   * overridden in the framework!
   */
  public <T> void addCallbackTo(ListenableFuture<T> future) {
    Futures.addCallback(
        future,
        new FutureCallback<T>() {
          @Override
          public void onSuccess(T value) {
            succeeded(value);
          }

          @Override
          public void onFailure(Throwable t) {
            failed(t);
          }
        });
  }
}

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

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.FutureCallback;
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
 * @since 2.1
 */
public abstract class ProducerMonitor {
  /**
   * Called when the producer's output is requested; that is, when the first method is called that
   * requires the production of this producer's output.
   *
   * <p>Note that if a method depends on {@code Producer<T>}, then this does not count as requesting
   * {@code T}; that is only triggered by calling {@link Producer#get()}.
   *
   * <p>Depending on how this producer is requested, the following threading constraints are
   * guaranteed:
   *
   * <ol>
   *   <li>If the producer is requested directly by a method on a component, then {@code requested}
   *       will be called on the same thread as the component method call.
   *   <li>If the producer is requested by value from another producer (i.e., injected as {@code T}
   *       or {@code Produced<T>}), then {@code requested} will be called from the same thread as
   *       the other producer's {@code requested}.
   *   <li>If the producer is requested by calling {@link Producer#get()}, then {@code requested}
   *       will be called from the same thread as that {@code get()} call.
   * </ol>
   *
   * <p>When multiple monitors are installed, the order that each monitor will call this method is
   * unspecified, but will remain consistent throughout the course of the execution of a component.
   *
   * <p>This implementation is a no-op.
   */
  public void requested() {}

  /**
   * Called when all of the producer's inputs are available. This is called regardless of whether
   * the inputs have succeeded or not; when the inputs have succeeded, this is called prior to
   * scheduling the method on the executor, and if an input has failed and the producer will be
   * skipped, this method will be called before {@link #failed(Throwable)} is called.
   *
   * <p>When multiple monitors are installed, the order that each monitor will call this method is
   * unspecified, but will remain consistent throughout the course of the execution of a component.
   *
   * <p>This implementation is a no-op.
   */
  public void ready() {}

  /**
   * Called when the producer method is about to start executing. This will be called from the same
   * thread as the producer method itself.
   *
   * <p>When multiple monitors are installed, calls to this method will be in the reverse order from
   * calls to {@link #requested()}.
   *
   * <p>This implementation is a no-op.
   */
  public void methodStarting() {}

  /**
   * Called when the producer method has finished executing. This will be called from the same
   * thread as {@link #methodStarting()} and the producer method itself.
   *
   * <p>When multiple monitors are installed, calls to this method will be in the reverse order from
   * calls to {@link #requested()}.
   *
   * <p>This implementation is a no-op.
   */
  public void methodFinished() {}

  /**
   * Called when the producer’s future has completed successfully with a value.
   *
   * <p>When multiple monitors are installed, calls to this method will be in the reverse order from
   * calls to {@link #requested()}.
   *
   * <p>This implementation is a no-op.
   */
  public void succeeded(@SuppressWarnings("unused") Object value) {}

  /**
   * Called when the producer's future has failed with an exception.
   *
   * <p>When multiple monitors are installed, calls to this method will be in the reverse order from
   * calls to {@link #requested()}.
   *
   * <p>This implementation is a no-op.
   */
  public void failed(@SuppressWarnings("unused") Throwable t) {}

  /**
   * Adds this monitor's completion methods as a callback to the future. This is only intended to be
   * overridden in the framework!
   */
  public <T> void addCallbackTo(ListenableFuture<T> future) {
    addCallback(
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
        },
        directExecutor());
  }

  private static final ProducerMonitor NO_OP =
      new ProducerMonitor() {
        @Override
        public <T> void addCallbackTo(ListenableFuture<T> future) {
          // overridden to avoid adding a do-nothing callback
        }
      };

  /** Returns a monitor that does no monitoring. */
  public static ProducerMonitor noOp() {
    return NO_OP;
  }
}

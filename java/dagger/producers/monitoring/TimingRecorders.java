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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import dagger.internal.Beta;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods relating to timing.
 *
 * @since 2.1
 */
// TODO(beder): Reduce the visibility of this class to package-private.
@Beta
public final class TimingRecorders {
  private static final Logger logger = Logger.getLogger(TimingRecorders.class.getName());

  /**
   * Returns a timing recorder factory that delegates to the given factories, and ensures that any
   * method called on this object, even transitively, does not throw a {@link RuntimeException} or
   * return null.
   *
   * <p>If the delegate recorders throw an {@link Error}, then that will escape this recorder
   * implementation. Errors are treated as unrecoverable conditions, and may cause the entire
   * component's execution to fail.
   */
  public static ProductionComponentTimingRecorder.Factory
      delegatingProductionComponentTimingRecorderFactory(
          Collection<ProductionComponentTimingRecorder.Factory> factories) {
    switch (factories.size()) {
      case 0:
        return noOpProductionComponentTimingRecorderFactory();
      case 1:
        return new NonThrowingProductionComponentTimingRecorder.Factory(
            Iterables.getOnlyElement(factories));
      default:
        return new DelegatingProductionComponentTimingRecorder.Factory(factories);
    }
  }

  /**
   * A component recorder that delegates to a single recorder, and catches and logs all exceptions
   * that the delegate throws.
   */
  private static final class NonThrowingProductionComponentTimingRecorder
      implements ProductionComponentTimingRecorder {
    private final ProductionComponentTimingRecorder delegate;

    NonThrowingProductionComponentTimingRecorder(ProductionComponentTimingRecorder delegate) {
      this.delegate = delegate;
    }

    @Override
    public ProducerTimingRecorder producerTimingRecorderFor(ProducerToken token) {
      try {
        ProducerTimingRecorder recorder = delegate.producerTimingRecorderFor(token);
        return recorder == null
            ? ProducerTimingRecorder.noOp()
            : new NonThrowingProducerTimingRecorder(recorder);
      } catch (RuntimeException e) {
        logProducerTimingRecorderForException(e, delegate, token);
        return ProducerTimingRecorder.noOp();
      }
    }

    static final class Factory implements ProductionComponentTimingRecorder.Factory {
      private final ProductionComponentTimingRecorder.Factory delegate;

      Factory(ProductionComponentTimingRecorder.Factory delegate) {
        this.delegate = delegate;
      }

      @Override
      public ProductionComponentTimingRecorder create(Object component) {
        try {
          ProductionComponentTimingRecorder recorder = delegate.create(component);
          return recorder == null
              ? noOpProductionComponentTimingRecorder()
              : new NonThrowingProductionComponentTimingRecorder(recorder);
        } catch (RuntimeException e) {
          logCreateException(e, delegate, component);
          return noOpProductionComponentTimingRecorder();
        }
      }
    }
  }

  /**
   * A producer recorder that delegates to a single recorder, and catches and logs all exceptions
   * that the delegate throws.
   */
  private static final class NonThrowingProducerTimingRecorder extends ProducerTimingRecorder {
    private final ProducerTimingRecorder delegate;

    NonThrowingProducerTimingRecorder(ProducerTimingRecorder delegate) {
      this.delegate = delegate;
    }

    @Override
    public void recordMethod(long startedNanos, long durationNanos) {
      try {
        delegate.recordMethod(startedNanos, durationNanos);
      } catch (RuntimeException e) {
        logProducerTimingRecorderMethodException(e, delegate, "recordMethod");
      }
    }

    @Override
    public void recordSuccess(long latencyNanos) {
      try {
        delegate.recordSuccess(latencyNanos);
      } catch (RuntimeException e) {
        logProducerTimingRecorderMethodException(e, delegate, "recordSuccess");
      }
    }

    @Override
    public void recordFailure(Throwable exception, long latencyNanos) {
      try {
        delegate.recordFailure(exception, latencyNanos);
      } catch (RuntimeException e) {
        logProducerTimingRecorderMethodException(e, delegate, "recordFailure");
      }
    }

    @Override
    public void recordSkip(Throwable exception) {
      try {
        delegate.recordSkip(exception);
      } catch (RuntimeException e) {
        logProducerTimingRecorderMethodException(e, delegate, "recordSkip");
      }
    }
  }

  /**
   * A component recorder that delegates to several recorders, and catches and logs all exceptions
   * that the delegates throw.
   */
  private static final class DelegatingProductionComponentTimingRecorder
      implements ProductionComponentTimingRecorder {
    private final ImmutableList<ProductionComponentTimingRecorder> delegates;

    DelegatingProductionComponentTimingRecorder(
        ImmutableList<ProductionComponentTimingRecorder> delegates) {
      this.delegates = delegates;
    }

    @Override
    public ProducerTimingRecorder producerTimingRecorderFor(ProducerToken token) {
      ImmutableList.Builder<ProducerTimingRecorder> recordersBuilder = ImmutableList.builder();
      for (ProductionComponentTimingRecorder delegate : delegates) {
        try {
          ProducerTimingRecorder recorder = delegate.producerTimingRecorderFor(token);
          if (recorder != null) {
            recordersBuilder.add(recorder);
          }
        } catch (RuntimeException e) {
          logProducerTimingRecorderForException(e, delegate, token);
        }
      }
      ImmutableList<ProducerTimingRecorder> recorders = recordersBuilder.build();
      switch (recorders.size()) {
        case 0:
          return ProducerTimingRecorder.noOp();
        case 1:
          return new NonThrowingProducerTimingRecorder(Iterables.getOnlyElement(recorders));
        default:
          return new DelegatingProducerTimingRecorder(recorders);
      }
    }

    static final class Factory implements ProductionComponentTimingRecorder.Factory {
      private final ImmutableList<? extends ProductionComponentTimingRecorder.Factory> delegates;

      Factory(Iterable<? extends ProductionComponentTimingRecorder.Factory> delegates) {
        this.delegates = ImmutableList.copyOf(delegates);
      }

      @Override
      public ProductionComponentTimingRecorder create(Object component) {
        ImmutableList.Builder<ProductionComponentTimingRecorder> recordersBuilder =
            ImmutableList.builder();
        for (ProductionComponentTimingRecorder.Factory delegate : delegates) {
          try {
            ProductionComponentTimingRecorder recorder = delegate.create(component);
            if (recorder != null) {
              recordersBuilder.add(recorder);
            }
          } catch (RuntimeException e) {
            logCreateException(e, delegate, component);
          }
        }
        ImmutableList<ProductionComponentTimingRecorder> recorders = recordersBuilder.build();
        switch (recorders.size()) {
          case 0:
            return noOpProductionComponentTimingRecorder();
          case 1:
            return new NonThrowingProductionComponentTimingRecorder(
                Iterables.getOnlyElement(recorders));
          default:
            return new DelegatingProductionComponentTimingRecorder(recorders);
        }
      }
    }
  }

  /**
   * A producer recorder that delegates to several recorders, and catches and logs all exceptions
   * that the delegates throw.
   */
  private static final class DelegatingProducerTimingRecorder extends ProducerTimingRecorder {
    private final ImmutableList<ProducerTimingRecorder> delegates;

    DelegatingProducerTimingRecorder(ImmutableList<ProducerTimingRecorder> delegates) {
      this.delegates = delegates;
    }

    @Override
    public void recordMethod(long startedNanos, long durationNanos) {
      for (ProducerTimingRecorder delegate : delegates) {
        try {
          delegate.recordMethod(startedNanos, durationNanos);
        } catch (RuntimeException e) {
          logProducerTimingRecorderMethodException(e, delegate, "recordMethod");
        }
      }
    }

    @Override
    public void recordSuccess(long latencyNanos) {
      for (ProducerTimingRecorder delegate : delegates) {
        try {
          delegate.recordSuccess(latencyNanos);
        } catch (RuntimeException e) {
          logProducerTimingRecorderMethodException(e, delegate, "recordSuccess");
        }
      }
    }

    @Override
    public void recordFailure(Throwable exception, long latencyNanos) {
      for (ProducerTimingRecorder delegate : delegates) {
        try {
          delegate.recordFailure(exception, latencyNanos);
        } catch (RuntimeException e) {
          logProducerTimingRecorderMethodException(e, delegate, "recordFailure");
        }
      }
    }

    @Override
    public void recordSkip(Throwable exception) {
      for (ProducerTimingRecorder delegate : delegates) {
        try {
          delegate.recordSkip(exception);
        } catch (RuntimeException e) {
          logProducerTimingRecorderMethodException(e, delegate, "recordSkip");
        }
      }
    }
  }

  /** Returns a recorder factory that returns no-op component recorders. */
  public static ProductionComponentTimingRecorder.Factory
      noOpProductionComponentTimingRecorderFactory() {
    return NO_OP_PRODUCTION_COMPONENT_TIMING_RECORDER_FACTORY;
  }

  /** Returns a component recorder that returns no-op producer recorders. */
  public static ProductionComponentTimingRecorder noOpProductionComponentTimingRecorder() {
    return NO_OP_PRODUCTION_COMPONENT_TIMING_RECORDER;
  }

  private static final ProductionComponentTimingRecorder.Factory
      NO_OP_PRODUCTION_COMPONENT_TIMING_RECORDER_FACTORY =
          new ProductionComponentTimingRecorder.Factory() {
            @Override
            public ProductionComponentTimingRecorder create(Object component) {
              return noOpProductionComponentTimingRecorder();
            }
          };

  private static final ProductionComponentTimingRecorder
      NO_OP_PRODUCTION_COMPONENT_TIMING_RECORDER =
          new ProductionComponentTimingRecorder() {
            @Override
            public ProducerTimingRecorder producerTimingRecorderFor(ProducerToken token) {
              return ProducerTimingRecorder.noOp();
            }
          };

  private static void logCreateException(
      RuntimeException e, ProductionComponentTimingRecorder.Factory factory, Object component) {
    logger.log(
        Level.SEVERE,
        "RuntimeException while calling ProductionComponentTimingRecorder.Factory.create on"
            + " factory "
            + factory
            + " with component "
            + component,
        e);
  }

  private static void logProducerTimingRecorderForException(
      RuntimeException e, ProductionComponentTimingRecorder recorder, ProducerToken token) {
    logger.log(
        Level.SEVERE,
        "RuntimeException while calling ProductionComponentTimingRecorder.producerTimingRecorderFor"
            + "on recorder "
            + recorder
            + " with token "
            + token,
        e);
  }

  private static void logProducerTimingRecorderMethodException(
      RuntimeException e, ProducerTimingRecorder recorder, String method) {
    logger.log(
        Level.SEVERE,
        "RuntimeException while calling ProducerTimingRecorder."
            + method
            + " on recorder "
            + recorder,
        e);
  }

  private TimingRecorders() {}
}

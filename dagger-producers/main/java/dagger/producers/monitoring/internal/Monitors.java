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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Provider;

/**
 * Utility methods relating to monitoring, for use in generated producers code.
 */
public final class Monitors {
  private static final Logger logger = Logger.getLogger(Monitors.class.getName());

  /**
   * Returns a monitor factory that delegates to the given factories, and ensures that any method
   * called on this object, even transitively, does not throw a {@link RuntimeException} or return
   * null.
   *
   * <p>If the delegate monitors throw an {@link Error}, then that will escape this monitor
   * implementation. Errors are treated as unrecoverable conditions, and may cause the entire
   * component's execution to fail.
   */
  public static ProductionComponentMonitor.Factory delegatingProductionComponentMonitorFactory(
      Collection<? extends ProductionComponentMonitor.Factory> factories) {
    if (factories.isEmpty()) {
      return ProductionComponentMonitor.Factory.noOp();
    } else if (factories.size() == 1) {
      return new NonThrowingProductionComponentMonitor.Factory(Iterables.getOnlyElement(factories));
    } else {
      return new DelegatingProductionComponentMonitor.Factory(factories);
    }
  }

  /**
   * Creates a new monitor for the given component, from a set of monitor factories. This will not
   * throw a {@link RuntimeException} or return null.
   */
  public static ProductionComponentMonitor createMonitorForComponent(
      Provider<?> componentProvider,
      Provider<Set<ProductionComponentMonitor.Factory>> monitorFactorySetProvider) {
    try {
      ProductionComponentMonitor.Factory factory =
          delegatingProductionComponentMonitorFactory(monitorFactorySetProvider.get());
      return factory.create(componentProvider.get());
    } catch (RuntimeException e) {
      logger.log(Level.SEVERE, "RuntimeException while constructing monitor factories.", e);
      return ProductionComponentMonitor.noOp();
    }
  }

  /**
   * A component monitor that delegates to a single monitor, and catches and logs all exceptions
   * that the delegate throws.
   */
  private static final class NonThrowingProductionComponentMonitor
      extends ProductionComponentMonitor {
    private final ProductionComponentMonitor delegate;

    NonThrowingProductionComponentMonitor(ProductionComponentMonitor delegate) {
      this.delegate = delegate;
    }

    @Override
    public ProducerMonitor producerMonitorFor(ProducerToken token) {
      try {
        ProducerMonitor monitor = delegate.producerMonitorFor(token);
        return monitor == null ? ProducerMonitor.noOp() : new NonThrowingProducerMonitor(monitor);
      } catch (RuntimeException e) {
        logProducerMonitorForException(e, delegate, token);
        return ProducerMonitor.noOp();
      }
    }

    static final class Factory extends ProductionComponentMonitor.Factory {
      private final ProductionComponentMonitor.Factory delegate;

      Factory(ProductionComponentMonitor.Factory delegate) {
        this.delegate = delegate;
      }

      @Override
      public ProductionComponentMonitor create(Object component) {
        try {
          ProductionComponentMonitor monitor = delegate.create(component);
          return monitor == null
              ? ProductionComponentMonitor.noOp()
              : new NonThrowingProductionComponentMonitor(monitor);
        } catch (RuntimeException e) {
          logCreateException(e, delegate, component);
          return ProductionComponentMonitor.noOp();
        }
      }
    }
  }

  /**
   * A producer monitor that delegates to a single monitor, and catches and logs all exceptions
   * that the delegate throws.
   */
  private static final class NonThrowingProducerMonitor extends ProducerMonitor {
    private final ProducerMonitor delegate;

    NonThrowingProducerMonitor(ProducerMonitor delegate) {
      this.delegate = delegate;
    }

    @Override
    public void requested() {
      try {
        delegate.requested();
      } catch (RuntimeException e) {
        logProducerMonitorMethodException(e, delegate, "requested");
      }
    }

    @Override
    public void ready() {
      try {
        delegate.ready();
      } catch (RuntimeException e) {
        logProducerMonitorMethodException(e, delegate, "ready");
      }
    }

    @Override
    public void methodStarting() {
      try {
        delegate.methodStarting();
      } catch (RuntimeException e) {
        logProducerMonitorMethodException(e, delegate, "methodStarting");
      }
    }

    @Override
    public void methodFinished() {
      try {
        delegate.methodFinished();
      } catch (RuntimeException e) {
        logProducerMonitorMethodException(e, delegate, "methodFinished");
      }
    }

    @Override
    public void succeeded(Object o) {
      try {
        delegate.succeeded(o);
      } catch (RuntimeException e) {
        logProducerMonitorArgMethodException(e, delegate, "succeeded", o);
      }
    }

    @Override
    public void failed(Throwable t) {
      try {
        delegate.failed(t);
      } catch (RuntimeException e) {
        logProducerMonitorArgMethodException(e, delegate, "failed", t);
      }
    }
  }

  /**
   * A component monitor that delegates to several monitors, and catches and logs all exceptions
   * that the delegates throw.
   */
  private static final class DelegatingProductionComponentMonitor
      extends ProductionComponentMonitor {
    private final ImmutableList<ProductionComponentMonitor> delegates;

    DelegatingProductionComponentMonitor(ImmutableList<ProductionComponentMonitor> delegates) {
      this.delegates = delegates;
    }

    @Override
    public ProducerMonitor producerMonitorFor(ProducerToken token) {
      ImmutableList.Builder<ProducerMonitor> monitorsBuilder = ImmutableList.builder();
      for (ProductionComponentMonitor delegate : delegates) {
        try {
          ProducerMonitor monitor = delegate.producerMonitorFor(token);
          if (monitor != null) {
            monitorsBuilder.add(monitor);
          }
        } catch (RuntimeException e) {
          logProducerMonitorForException(e, delegate, token);
        }
      }
      ImmutableList<ProducerMonitor> monitors = monitorsBuilder.build();
      if (monitors.isEmpty()) {
        return ProducerMonitor.noOp();
      } else if (monitors.size() == 1) {
        return new NonThrowingProducerMonitor(Iterables.getOnlyElement(monitors));
      } else {
        return new DelegatingProducerMonitor(monitors);
      }
    }

    static final class Factory extends ProductionComponentMonitor.Factory {
      private final ImmutableList<? extends ProductionComponentMonitor.Factory> delegates;

      Factory(Iterable<? extends ProductionComponentMonitor.Factory> delegates) {
        this.delegates = ImmutableList.copyOf(delegates);
      }

      @Override
      public ProductionComponentMonitor create(Object component) {
        ImmutableList.Builder<ProductionComponentMonitor> monitorsBuilder = ImmutableList.builder();
        for (ProductionComponentMonitor.Factory delegate : delegates) {
          try {
            ProductionComponentMonitor monitor = delegate.create(component);
            if (monitor != null) {
              monitorsBuilder.add(monitor);
            }
          } catch (RuntimeException e) {
            logCreateException(e, delegate, component);
          }
        }
        ImmutableList<ProductionComponentMonitor> monitors = monitorsBuilder.build();
        if (monitors.isEmpty()) {
          return ProductionComponentMonitor.noOp();
        } else if (monitors.size() == 1) {
          return new NonThrowingProductionComponentMonitor(Iterables.getOnlyElement(monitors));
        } else {
          return new DelegatingProductionComponentMonitor(monitors);
        }
      }
    }
  }

  /**
   * A producer monitor that delegates to several monitors, and catches and logs all exceptions
   * that the delegates throw.
   */
  private static final class DelegatingProducerMonitor extends ProducerMonitor {
    private final ImmutableList<ProducerMonitor> delegates;

    DelegatingProducerMonitor(ImmutableList<ProducerMonitor> delegates) {
      this.delegates = delegates;
    }

    @Override
    public void requested() {
      for (ProducerMonitor delegate : delegates) {
        try {
          delegate.requested();
        } catch (RuntimeException e) {
          logProducerMonitorMethodException(e, delegate, "requested");
        }
      }
    }

    @Override
    public void ready() {
      for (ProducerMonitor delegate : delegates) {
        try {
          delegate.ready();
        } catch (RuntimeException e) {
          logProducerMonitorMethodException(e, delegate, "ready");
        }
      }
    }

    @Override
    public void methodStarting() {
      for (ProducerMonitor delegate : delegates) {
        try {
          delegate.methodStarting();
        } catch (RuntimeException e) {
          logProducerMonitorMethodException(e, delegate, "methodStarting");
        }
      }
    }

    @Override
    public void methodFinished() {
      for (ProducerMonitor delegate : delegates.reverse()) {
        try {
          delegate.methodFinished();
        } catch (RuntimeException e) {
          logProducerMonitorMethodException(e, delegate, "methodFinished");
        }
      }
    }

    @Override
    public void succeeded(Object o) {
      for (ProducerMonitor delegate : delegates.reverse()) {
        try {
          delegate.succeeded(o);
        } catch (RuntimeException e) {
          logProducerMonitorArgMethodException(e, delegate, "succeeded", o);
        }
      }
    }

    @Override
    public void failed(Throwable t) {
      for (ProducerMonitor delegate : delegates.reverse()) {
        try {
          delegate.failed(t);
        } catch (RuntimeException e) {
          logProducerMonitorArgMethodException(e, delegate, "failed", t);
        }
      }
    }
  }

  /** Returns a provider of a no-op component monitor. */
  public static Provider<ProductionComponentMonitor> noOpProductionComponentMonitorProvider() {
    return NO_OP_PRODUCTION_COMPONENT_MONITOR_PROVIDER;
  }

  private static final Provider<ProductionComponentMonitor>
      NO_OP_PRODUCTION_COMPONENT_MONITOR_PROVIDER =
          new Provider<ProductionComponentMonitor>() {
            @Override
            public ProductionComponentMonitor get() {
              return ProductionComponentMonitor.noOp();
            }
          };

  private static void logCreateException(
      RuntimeException e, ProductionComponentMonitor.Factory factory, Object component) {
    logger.log(
        Level.SEVERE,
        "RuntimeException while calling ProductionComponentMonitor.Factory.create on factory "
            + factory
            + " with component "
            + component,
        e);
  }

  private static void logProducerMonitorForException(
      RuntimeException e, ProductionComponentMonitor monitor, ProducerToken token) {
    logger.log(
        Level.SEVERE,
        "RuntimeException while calling ProductionComponentMonitor.producerMonitorFor on monitor "
            + monitor
            + " with token "
            + token,
        e);
  }

  private static void logProducerMonitorMethodException(
      RuntimeException e, ProducerMonitor monitor, String method) {
    logger.log(
        Level.SEVERE,
        "RuntimeException while calling ProducerMonitor." + method + " on monitor " + monitor,
        e);
  }

  private static void logProducerMonitorArgMethodException(
      RuntimeException e, ProducerMonitor monitor, String method, Object arg) {
    logger.log(
        Level.SEVERE,
        "RuntimeException while calling ProducerMonitor."
            + method
            + " on monitor "
            + monitor
            + " with "
            + arg,
        e);
  }

  private Monitors() {}
}

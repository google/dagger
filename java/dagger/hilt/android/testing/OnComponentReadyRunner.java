/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.testing;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.base.Throwables.throwIfUnchecked;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import dagger.hilt.EntryPoints;
import dagger.hilt.android.internal.testing.TestApplicationComponentManagerHolder;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to the Singleton component in tests, so that Rules can access it after custom
 * test modules have been added.
 */
public final class OnComponentReadyRunner {
  private final List<EntryPointListener<?>> listeners = new ArrayList<>();
  private boolean haveRunListeners = false;

  /** This should only be called by the framework. */
  public void runListeners() {
    Preconditions.checkState(!haveRunListeners, "Listeners have already been run!");
    haveRunListeners = true;
    for (EntryPointListener<?> listener : listeners) {
      listener.runListener();
    }
  }

  /** Must be called on the test thread, before the Statement is evaluated. */
  public static <T> void addListener(Class<T> entryPoint, Listener<T> listener) {
    Application application = ApplicationProvider.getApplicationContext();
    // TODO(user): Should we throw instead?
    if (application instanceof TestApplicationComponentManagerHolder) {
      Object componentManager =
          ((TestApplicationComponentManagerHolder) application).componentManager();
      Preconditions.checkState(componentManager instanceof Holder);
      ((Holder) componentManager)
          .getOnComponentReadyRunner()
          .addListenerInternal(entryPoint, listener);
    }
  }

  private <T> void addListenerInternal(Class<T> entryPoint, Listener<T> listener) {
    if (haveRunListeners) {
      // If the initial listeners already ran, just call through immediately
      runListener(entryPoint, listener);
    } else {
      listeners.add(EntryPointListener.create(entryPoint, listener));
    }
  }

  @AutoValue
  abstract static class EntryPointListener<T> {
    static <T> EntryPointListener<T> create(Class<T> entryPoint, Listener<T> listener) {
      return new AutoValue_OnComponentReadyRunner_EntryPointListener<T>(entryPoint, listener);
    }

    abstract Class<T> entryPoint();

    abstract Listener<T> listener();

    private void runListener() {
      OnComponentReadyRunner.runListener(entryPoint(), listener());
    }
  }

  private static <T> void runListener(Class<T> entryPoint, Listener<T> listener) {
    try {
      listener.onComponentReady(EntryPoints.get(getApplicationContext(), entryPoint));
    } catch (Throwable t) {
      throwIfUnchecked(t);
      throw new RuntimeException(t);
    }
  }

  /** This should only be called by the framework. */
  public interface Holder {
    OnComponentReadyRunner getOnComponentReadyRunner();
  }

  /** Rules should register an implementation of this to get access to the singleton component */
  public interface Listener<T> {
    void onComponentReady(T entryPoint) throws Throwable;
  }
}

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

import android.app.Application;
import android.content.Context;
import com.google.auto.value.AutoValue;
import dagger.hilt.EntryPoints;
import dagger.hilt.android.internal.Contexts;
import dagger.hilt.android.internal.testing.TestApplicationComponentManagerHolder;
import dagger.hilt.internal.GeneratedComponentManager;
import dagger.hilt.internal.Preconditions;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to the Singleton component in tests, so that Rules can access it after custom
 * test modules have been added.
 */
public final class OnComponentReadyRunner {
  private final List<EntryPointListener<?>> listeners = new ArrayList<>();
  private GeneratedComponentManager<?> componentManager;
  private boolean componentHostSet = false;

  /** Used by generated code, to notify listeners that the component has been created. */
  public void setComponentManager(GeneratedComponentManager<?> componentManager) {
    Preconditions.checkState(!componentHostSet, "Component host was already set.");
    componentHostSet = true;
    this.componentManager = componentManager;
    for (EntryPointListener<?> listener : listeners) {
      listener.deliverComponent(componentManager);
    }
  }

  /** Must be called on the test thread, before the Statement is evaluated. */
  public static <T> void addListener(
      Context context, Class<T> entryPoint, OnComponentReadyListener<T> listener) {
    Application application = Contexts.getApplication(context.getApplicationContext());
    if (application instanceof TestApplicationComponentManagerHolder) {
      TestApplicationComponentManagerHolder managerHolder =
          (TestApplicationComponentManagerHolder) application;
      OnComponentReadyRunnerHolder runnerHolder =
          (OnComponentReadyRunnerHolder) managerHolder.componentManager();
      runnerHolder.getOnComponentReadyRunner().addListenerInternal(entryPoint, listener);
    }
  }

  private <T> void addListenerInternal(Class<T> entryPoint, OnComponentReadyListener<T> listener) {
    if (componentHostSet) {
      // If the componentHost was already set, just call through immediately
      runListener(componentManager, entryPoint, listener);
    } else {
      listeners.add(EntryPointListener.create(entryPoint, listener));
    }
  }

  public boolean isEmpty() {
    return listeners.isEmpty();
  }

  @AutoValue
  abstract static class EntryPointListener<T> {
    static <T> EntryPointListener<T> create(
        Class<T> entryPoint, OnComponentReadyListener<T> listener) {
      return new AutoValue_OnComponentReadyRunner_EntryPointListener<T>(entryPoint, listener);
    }

    abstract Class<T> entryPoint();

    abstract OnComponentReadyListener<T> listener();

    private void deliverComponent(GeneratedComponentManager<?> object) {
      runListener(object, entryPoint(), listener());
    }
  }

  private static <T> void runListener(
      GeneratedComponentManager<?> componentManager,
      Class<T> entryPoint,
      OnComponentReadyListener<T> listener) {
    try {
      listener.onComponentReady(EntryPoints.get(componentManager, entryPoint));
    } catch (RuntimeException | Error t) {
      throw t;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Public for use by generated code and {@link TestApplicationComponentManager} */
  public interface OnComponentReadyRunnerHolder {
    OnComponentReadyRunner getOnComponentReadyRunner();
  }

  /** Rules should register an implementation of this to get access to the singleton component */
  public interface OnComponentReadyListener<T> {
    void onComponentReady(T entryPoint) throws Throwable;
  }
}

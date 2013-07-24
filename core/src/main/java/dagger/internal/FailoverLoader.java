/*
 * Copyright (C) 2013 Square, Inc.
 * Copyright (C) 2013 Google, Inc.
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
package dagger.internal;


import dagger.internal.loaders.ReflectiveAtInjectBinding;
import dagger.internal.loaders.ReflectiveStaticInjection;

import static dagger.internal.loaders.GeneratedAdapters.INJECT_ADAPTER_SUFFIX;
import static dagger.internal.loaders.GeneratedAdapters.MODULE_ADAPTER_SUFFIX;
import static dagger.internal.loaders.GeneratedAdapters.STATIC_INJECTION_SUFFIX;

/**
 * Handles loading/finding of modules, injection bindings, and static injections by use of a
 * strategy of "load the appropriate generated code" or, if no such code is found, create a
 * reflective equivalent.
 */
public final class FailoverLoader extends Loader {

  /**
   * Obtains a module adapter for {@code module} from the first responding resolver.
   */
  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> type, T instance) {
    ModuleAdapter<T> result =
        instantiate(type.getName() + MODULE_ADAPTER_SUFFIX, type.getClassLoader());
    if (result == null) {
      throw new IllegalStateException("Module adapter for " + type + " could not be loaded. "
          + "Please ensure that code generation was run for this module.");
    }
    result.module = (instance != null) ? instance : result.newModule();
    return result;
  }

  @Override public Binding<?> getAtInjectBinding(
      String key, String className, ClassLoader classLoader, boolean mustHaveInjections) {
    Binding<?> result = instantiate(className + INJECT_ADAPTER_SUFFIX, classLoader);
    if (result != null) {
      return result; // Found loadable adapter, returning it.
    }
    Class<?> type = loadClass(classLoader, className);
    if (type.equals(Void.class)) {
      throw new IllegalStateException(
          String.format("Could not load class %s needed for binding %s", className, key));
    }
    if (type.isInterface()) {
      return null; // Short-circuit since we can't build reflective bindings for interfaces.
    }
    return ReflectiveAtInjectBinding.create(type, mustHaveInjections);
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    StaticInjection result = instantiate(
          injectedClass.getName() + STATIC_INJECTION_SUFFIX, injectedClass.getClassLoader());
    if (result != null) {
      return result;
    }
    return ReflectiveStaticInjection.create(injectedClass);
  }
}

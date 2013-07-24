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

/**
 * A test-only loader that merely uses reflection to test internals.
 */
public final class TestingLoader extends Loader {

  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> type, T instance) {
    ModuleAdapter<T> adapter = TestingModuleAdapter.create(type);
    adapter.module = (instance != null) ? instance : adapter.newModule();
    return adapter;
  }

  @Override public Binding<?> getAtInjectBinding(String key, String className, ClassLoader ignored,
      boolean mustHaveInjections) {
     try {
      Class<?> type = getClass().getClassLoader().loadClass(className);
      if (type.isInterface()) {
        return null; // Short-circuit since we can't build reflective bindings for interfaces.
      }
      return ReflectiveAtInjectBinding.create(type, mustHaveInjections);
    } catch (ClassNotFoundException e) {
      throw new TypeNotPresentException(
          String.format("Could not find %s needed for binding %s", className, key), e);
    }
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    return ReflectiveStaticInjection.create(injectedClass);
  }
}

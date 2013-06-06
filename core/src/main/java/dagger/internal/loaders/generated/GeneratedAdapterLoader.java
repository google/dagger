/*
 * Copyright (C) 2012 Square, Inc.
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
package dagger.internal.loaders.generated;

import dagger.internal.Binding;
import dagger.internal.Loader;
import dagger.internal.ModuleAdapter;
import dagger.internal.StaticInjection;
import java.lang.reflect.Constructor;

/**
 * A runtime {@link Loader} that loads generated classes.
 */
public final class GeneratedAdapterLoader implements Loader {
  public static final String INJECT_ADAPTER_SUFFIX = "$$InjectAdapter";
  public static final String MODULE_ADAPTER_SUFFIX = "$$ModuleAdapter";
  public static final String STATIC_INJECTION_SUFFIX = "$$StaticInjection";

  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module) {
    return instantiate(moduleClass.getName(), MODULE_ADAPTER_SUFFIX, moduleClass.getClassLoader());
  }

  @Override public Binding<?> getAtInjectBinding(
      String key, String className, ClassLoader cl, boolean mustHaveInjections) {
    return instantiate(className, INJECT_ADAPTER_SUFFIX, cl);
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    return instantiate(injectedClass.getName(), STATIC_INJECTION_SUFFIX,
        injectedClass.getClassLoader());
  }

  @SuppressWarnings("unchecked") // We use a naming convention to defend against mismatches.
  private <T> T instantiate(String className, String suffix, ClassLoader cl) {
    String name = className + suffix;
    try {
      // Seriously.  The bootstrap classloader could be null?  Who thought this API up?
      cl = (cl != null) ? cl : Thread.currentThread().getContextClassLoader();
      Class<?> generatedClass = cl.loadClass(name);
      Constructor<?> constructor = generatedClass.getConstructor();
      constructor.setAccessible(true);
      return (T) constructor.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Unexpected failure loading " + name, e);
    }
  }
}

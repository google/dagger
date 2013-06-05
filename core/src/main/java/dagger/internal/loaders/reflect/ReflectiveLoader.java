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
package dagger.internal.loaders.reflect;

import dagger.Module;
import dagger.internal.Binding;
import dagger.internal.Loader;
import dagger.internal.ModuleAdapter;
import dagger.internal.StaticInjection;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * Uses reflection to create bindings, module adapters and static injections.
 */
public final class ReflectiveLoader implements Loader {
  @Override public Binding<?> getAtInjectBinding(
      String key, String className, ClassLoader cl, boolean mustHaveInjections) {
    Class<?> c;
    try {
      // Seriously.  The bootstrap classloader could be null?  Who thought this API up?
      cl = (cl != null) ? cl : Thread.currentThread().getContextClassLoader();
      c = cl.loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    if (c.isInterface()) {
      return null;
    }

    return ReflectiveAtInjectBinding.create(c, mustHaveInjections);
  }

  @SuppressWarnings("unchecked") // Runtime checks validate that the result type matches 'T'.
  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module) {
    Module annotation = moduleClass.getAnnotation(Module.class);
    if (annotation == null) {
      throw new IllegalArgumentException("No @Module on " + moduleClass.getName());
    }
    if (moduleClass.getSuperclass() != Object.class) {
      throw new IllegalArgumentException(
          "Modules must not extend from other classes: " + moduleClass.getName());
    }
    return (ModuleAdapter<T>) new ReflectiveModuleAdapter(moduleClass, annotation);
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    List<Field> fields = new ArrayList<Field>();
    for (Field field : injectedClass.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.isAnnotationPresent(Inject.class)) {
        field.setAccessible(true);
        fields.add(field);
      }
    }
    if (fields.isEmpty()) {
      throw new IllegalArgumentException("No static injections: " + injectedClass.getName());
    }
    return new ReflectiveStaticInjection(injectedClass.getClassLoader(),
        fields.toArray(new Field[fields.size()]));
  }
}
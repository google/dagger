/*
 * Copyright (C) 2012 Square, Inc.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Provides a point of configuration of the basic resolving functions within Dagger, namely
 * that of Module handling, injection binding creation, and static injection.  A plugin must
 * provide all resolution methods
 */
public abstract class Loader {

  private final LruCache<ClassLoader, LruCache<String, Class<?>>> caches =
      new LruCache<ClassLoader, LruCache<String, Class<?>>>(Integer.MAX_VALUE) {
    @Override protected LruCache<String, Class<?>> create(final ClassLoader classLoader) {
      return new LruCache<String, Class<?>>(Integer.MAX_VALUE) {
        @Override protected Class<?> create(String className) {
          try {
            return classLoader.loadClass(className);
          } catch (ClassNotFoundException e) {
            return Void.class; // Cache the failure (negative case).
          }
        }
      };
    }
  };

  /**
   * Returns a binding that uses {@code @Inject} annotations, or null if no valid binding can
   * be found or created.
   */
  public abstract Binding<?> getAtInjectBinding(
      String key, String className, ClassLoader classLoader, boolean mustHaveInjections);

  /**
   * Returns a module adapter for {@code module} or throws a {@code TypeNotPresentException} if
   * none can be found.
   */
  public abstract <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module);

  /**
   * Returns the static injection for {@code injectedClass}.
   */
  public abstract StaticInjection getStaticInjection(Class<?> injectedClass);

  /**
   * Loads a class from a {@code ClassLoader}-specific cache if it's already there, or
   * loads it from the given {@code ClassLoader} and caching it for future requests.  Failures
   * to load are also cached using the Void.class type.  A null {@code ClassLoader} is assumed
   * to be the system classloader.
   */
  protected Class<?> loadClass(ClassLoader classLoader, String name) {
    // A null classloader is the system classloader.
    classLoader = (classLoader != null) ? classLoader : ClassLoader.getSystemClassLoader();
    return caches.get(classLoader).get(name);
  }

  /**
   * Instantiates a class using its default constructor and the given {@link ClassLoader}.
   */
  protected <T> T instantiate(String name, ClassLoader classLoader) {
    try {
      Class<?> generatedClass = loadClass(classLoader, name);
      if (generatedClass == Void.class) {
        return null;
      }
      Constructor<?> constructor = generatedClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return (T) constructor.newInstance();
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("No default constructor found on " + name, e);
    } catch (InstantiationException e) {
      throw new RuntimeException("Failed to initialize " + name, e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to initialize " + name, e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException("Error while initializing " + name, e.getCause());
    }
  }

}
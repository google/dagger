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
package dagger.internal.loaders;

import dagger.internal.Binding;
import dagger.internal.Keys;
import dagger.internal.Linker;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Injects the {@code @Inject}-annotated fields and constructors of a class
 * using reflection.
 */
public final class ReflectiveAtInjectBinding<T> extends Binding<T> {
  private final Field[] fields;
  private final ClassLoader loader;
  private final Constructor<T> constructor;
  private final Class<?> supertype;
  private final String[] keys;
  private final Binding<?>[] fieldBindings;
  private final Binding<?>[] parameterBindings;
  private Binding<? super T> supertypeBinding;

  /**
   * @param keys keys for the fields, constructor parameters and supertype in
   *     that order. These are precomputed to minimize reflection when {@code
   *     attach} is called multiple times.
   * @param constructor the injectable constructor, or null if this binding
   *     supports members injection only.
   * @param supertype the injectable supertype, or null if the supertype is a
   */
  private ReflectiveAtInjectBinding(String provideKey, String membersKey, boolean singleton,
      Class<?> type, Field[] fields, Constructor<T> constructor, int parameterCount,
      Class<?> supertype, String[] keys) {
    super(provideKey, membersKey, singleton, type);
    this.constructor = constructor;
    this.fields = fields;
    this.supertype = supertype;
    this.keys = keys;
    this.parameterBindings = new Binding<?>[parameterCount];
    this.fieldBindings = new Binding<?>[fields.length];
    this.loader = type.getClassLoader();
  }

  @SuppressWarnings("unchecked") // We're careful to make keys and bindings match up.
  @Override public void attach(Linker linker) {
    int k = 0;
    for (int i = 0; i < fields.length; i++) {
      if (fieldBindings[i] == null) {
        fieldBindings[i] = linker.requestBinding(keys[k], fields[i], loader);
      }
      k++;
    }
    if (constructor != null) {
      for (int i = 0; i < parameterBindings.length; i++) {
        if (parameterBindings[i] == null) {
          parameterBindings[i] = linker.requestBinding(keys[k], constructor, loader);
        }
        k++;
      }
    }
    if (supertype != null && supertypeBinding == null) {
      supertypeBinding =
          (Binding<? super T>) linker.requestBinding(keys[k], membersKey, loader, false, true);
    }
  }

  @Override public T get() {
    if (constructor == null) {
      throw new UnsupportedOperationException();
    }
    Object[] args = new Object[parameterBindings.length];
    for (int i = 0; i < parameterBindings.length; i++) {
      args[i] = parameterBindings[i].get();
    }
    T result;
    try {
      result = constructor.newInstance(args);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      throw cause instanceof RuntimeException
          ? (RuntimeException) cause
          : new RuntimeException(cause);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
    injectMembers(result);
    return result;
  }

  @Override public void injectMembers(T t) {
    try {
      for (int i = 0; i < fields.length; i++) {
        fields[i].set(t, fieldBindings[i].get());
      }
      if (supertypeBinding != null) {
        supertypeBinding.injectMembers(t);
      }
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
    if (parameterBindings != null) {
      Collections.addAll(get, parameterBindings);
    }
    Collections.addAll(injectMembers, fieldBindings);
    if (supertypeBinding != null) {
      injectMembers.add(supertypeBinding);
    }
  }

  @Override public String toString() {
    return provideKey != null ? provideKey : membersKey;
  }

  public static <T> Binding<T> create(Class<T> type, boolean mustHaveInjections) {
    boolean singleton = type.isAnnotationPresent(Singleton.class);
    List<String> keys = new ArrayList<String>();

    // Lookup the injectable fields and their corresponding keys.
    List<Field> injectedFields = new ArrayList<Field>();
    for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (!field.isAnnotationPresent(Inject.class) || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        if ((field.getModifiers() & Modifier.PRIVATE) != 0) {
          throw new IllegalStateException("Can't inject private field: " + field);
        }
        field.setAccessible(true);
        injectedFields.add(field);
        keys.add(Keys.get(field.getGenericType(), field.getAnnotations(), field));
      }
    }

    // Look up @Inject-annotated constructors. If there's no @Inject-annotated
    // constructor, use a default public constructor if the class has other
    // injections. Otherwise treat the class as non-injectable.
    Constructor<T> injectedConstructor = null;
    for (Constructor<T> constructor : getConstructorsForType(type)) {
      if (!constructor.isAnnotationPresent(Inject.class)) {
        continue;
      }
      if (injectedConstructor != null) {
        throw new InvalidBindingException(type.getName(), "has too many injectable constructors");
      }
      injectedConstructor = constructor;
    }
    if (injectedConstructor == null) {
      if (!injectedFields.isEmpty()) {
        try {
          injectedConstructor = type.getDeclaredConstructor();
        } catch (NoSuchMethodException ignored) {
        }
      } else if (mustHaveInjections) {
        throw new InvalidBindingException(type.getName(),
            "has no injectable members. Do you want to add an injectable constructor?");
      }
    }

    int parameterCount;
    String provideKey;
    if (injectedConstructor != null) {
      if ((injectedConstructor.getModifiers() & Modifier.PRIVATE) != 0) {
        throw new IllegalStateException("Can't inject private constructor: " + injectedConstructor);
      }

      provideKey = Keys.get(type);
      injectedConstructor.setAccessible(true);
      Type[] types = injectedConstructor.getGenericParameterTypes();
      parameterCount = types.length;
      if (parameterCount != 0) {
        Annotation[][] annotations = injectedConstructor.getParameterAnnotations();
        for (int p = 0; p < types.length; p++) {
          keys.add(Keys.get(types[p], annotations[p], injectedConstructor));
        }
      }
    } else {
      provideKey = null;
      parameterCount = 0;
      if (singleton) {
        throw new IllegalArgumentException(
            "No injectable constructor on @Singleton " + type.getName());
      }
    }

    Class<? super T> supertype = type.getSuperclass();
    if (supertype != null) {
      if (Keys.isPlatformType(supertype.getName())) {
        supertype = null;
      } else {
        keys.add(Keys.getMembersKey(supertype));
      }
    }

    String membersKey = Keys.getMembersKey(type);
    return new ReflectiveAtInjectBinding<T>(provideKey, membersKey, singleton, type,
        injectedFields.toArray(new Field[injectedFields.size()]), injectedConstructor,
        parameterCount, supertype, keys.toArray(new String[keys.size()]));
  }

  @SuppressWarnings("unchecked") // Class.getDeclaredConstructors is an unsafe API.
  private static <T> Constructor<T>[] getConstructorsForType(Class<T> type) {
    return (Constructor<T>[]) type.getDeclaredConstructors();
  }
}

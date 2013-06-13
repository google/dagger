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

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.internal.Binding;
import dagger.internal.Keys;
import dagger.internal.Linker;
import dagger.internal.ModuleAdapter;
import dagger.internal.SetBinding;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import javax.inject.Singleton;

public final class ReflectiveModuleAdapter extends ModuleAdapter<Object> {
  final Class<?> moduleClass;

  public ReflectiveModuleAdapter(Class<?> moduleClass, Module annotation) {
    super(
        injectableTypesToKeys(annotation.injects()),
        annotation.staticInjections(),
        annotation.overrides(),
        annotation.includes(),
        annotation.complete(),
        annotation.library());
    this.moduleClass = moduleClass;
  }

  private static String[] injectableTypesToKeys(Class<?>[] injectableTypes) {
    String[] result = new String[injectableTypes.length];
    for (int i = 0; i < injectableTypes.length; i++) {
      Class<?> injectableType = injectableTypes[i];
      result[i] = injectableType.isInterface()
          ? Keys.get(injectableType)
          : Keys.getMembersKey(injectableType);
    }
    return result;
  }

  @Override public void getBindings(Map<String, Binding<?>> bindings) {
    for (Class<?> c = moduleClass; !c.equals(Object.class); c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        Provides provides = method.getAnnotation(Provides.class);
        if (provides != null) {
          Type genericReturnType = method.getGenericReturnType();

          Type typeToCheck = genericReturnType;
          if (genericReturnType instanceof ParameterizedType) {
            typeToCheck = ((ParameterizedType) genericReturnType).getRawType();
          }
          if (Provider.class.equals(typeToCheck)) {
            throw new IllegalStateException("@Provides method must not return Provider directly: "
                + c.getName()
                + "."
                + method.getName());
          }
          if (Lazy.class.equals(typeToCheck)) {
            throw new IllegalStateException("@Provides method must not return Lazy directly: "
                + c.getName()
                + "."
                + method.getName());
          }

          String key = Keys.get(genericReturnType, method.getAnnotations(), method);
          switch (provides.type()) {
            case UNIQUE:
              handleBindings(bindings, method, key, library);
              break;
            case SET:
              handleSetBindings(bindings, method, key, library);
              break;
            default:
              throw new AssertionError("Unknown @Provides type " + provides.type());
          }
        }
      }
    }
  }

  private <T> void handleBindings(Map<String, Binding<?>> bindings, Method method, String key,
      boolean library) {
    bindings.put(key, new ProviderMethodBinding<T>(method, key, module, library));
  }

  private <T> void handleSetBindings(Map<String, Binding<?>> bindings, Method method, String key,
      boolean library) {
    String setKey = Keys.getSetKey(method.getGenericReturnType(), method.getAnnotations(), method);
    SetBinding.<T>add(bindings, setKey, new ProviderMethodBinding<T>(method, key, module,
        library));
  }

  @Override public Object newModule() {
    try {
      Constructor<?> constructor = moduleClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException(e.getCause());
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Could not construct " + moduleClass.getName()
          + " as it lacks an accessible no-args constructor. This module must be passed"
          + " in as an instance, or an accessible no-args constructor must be added.", e);
    } catch (InstantiationException e) {
      throw new IllegalArgumentException("Failed to construct " + moduleClass.getName(), e);
    } catch (IllegalAccessException e) {
      throw new AssertionError();
    }
  }

  /**
   * Creates a ReflectiveModuleAdapter or throws an {@code IllegalArgumentException}.
   */
  @SuppressWarnings("unchecked") // Runtime checks validate that the result type matches 'T'.
  public static <T> ModuleAdapter<T> createAdaptor(Class<? extends T> moduleClass) {
    Module annotation = moduleClass.getAnnotation(Module.class);
    if (annotation == null) {
      throw new IllegalArgumentException("No @Module on " + moduleClass.getName());
    }
    if (!moduleClass.getSuperclass().equals(Object.class)) {
      throw new IllegalArgumentException(
          "Modules must not extend from other classes: " + moduleClass.getName());
    }
    return (ModuleAdapter<T>) new ReflectiveModuleAdapter(moduleClass, annotation);
  }

  /**
   * Invokes a method to provide a value. The method's parameters are injected.
   */
  private final class ProviderMethodBinding<T> extends Binding<T> {
    private Binding<?>[] parameters;
    private final Method method;
    private final Object instance;

    public ProviderMethodBinding(Method method, String key, Object instance, boolean library) {
      super(key, null, method.isAnnotationPresent(Singleton.class),
          moduleClass.getName() + "." + method.getName() + "()");
      this.method = method;
      this.instance = instance;
      method.setAccessible(true);
      setLibrary(library);
    }

    @Override public void attach(Linker linker) {
      Type[] types = method.getGenericParameterTypes();
      Annotation[][] annotations = method.getParameterAnnotations();
      parameters = new Binding[types.length];
      for (int i = 0; i < parameters.length; i++) {
        String key = Keys.get(types[i], annotations[i], method + " parameter " + i);
        parameters[i] = linker.requestBinding(key, method, instance.getClass().getClassLoader());
      }
    }

    @SuppressWarnings("unchecked") // We defined 'T' in terms of the method's return type.
    @Override public T get() {
      Object[] args = new Object[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        args[i] = parameters[i].get();
      }
      try {
        return (T) method.invoke(instance, args);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        throw cause instanceof RuntimeException
            ? (RuntimeException) cause
            : new RuntimeException(cause);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
      for (Binding<?> binding : parameters) {
        get.add(binding);
      }
    }

    @Override public void injectMembers(T t) {
      throw new AssertionError("Provides method bindings are not MembersInjectors");
    }

    @Override public String toString() {
      return method.toString();
    }
  }
}

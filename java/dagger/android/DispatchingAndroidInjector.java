/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.android;

import static dagger.internal.DaggerCollections.newLinkedHashMapWithExpectedSize;
import static dagger.internal.Preconditions.checkNotNull;

import android.app.Activity;
import android.app.Fragment;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.internal.Beta;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Performs members-injection on instances of core Android types (e.g. {@link Activity}, {@link
 * Fragment}) that are constructed by the Android framework and not by Dagger. This class relies on
 * an injected mapping from each concrete class to an {@link AndroidInjector.Factory} for an {@link
 * AndroidInjector} of that class. Each concrete class must have its own entry in the map, even if
 * it extends another class which is already present in the map. Calls {@link Object#getClass()} on
 * the instance in order to find the appropriate {@link AndroidInjector.Factory}.
 *
 * @param <T> the core Android type to be injected
 */
@Beta
public final class DispatchingAndroidInjector<T> implements AndroidInjector<T> {
  private static final String NO_SUPERTYPES_BOUND_FORMAT =
      "No injector factory bound for Class<%s>";
  private static final String SUPERTYPES_BOUND_FORMAT =
      "No injector factory bound for Class<%1$s>. Injector factories were bound for supertypes "
          + "of %1$s: %2$s. Did you mean to bind an injector factory for the subtype?";

  private final Map<String, Provider<AndroidInjector.Factory<?>>> injectorFactories;

  @Inject
  DispatchingAndroidInjector(
      Map<Class<?>, Provider<AndroidInjector.Factory<?>>> injectorFactoriesWithClassKeys,
      Map<String, Provider<AndroidInjector.Factory<?>>> injectorFactoriesWithStringKeys) {
    this.injectorFactories = merge(injectorFactoriesWithClassKeys, injectorFactoriesWithStringKeys);
  }

  /**
   * Merges the two maps into one by transforming the values of the {@code classKeyedMap} with
   * {@link Class#getName()}.
   *
   * <p>An SPI plugin verifies the logical uniqueness of the keysets of these two maps so we're
   * assured there's no overlap.
   *
   * <p>Ideally we could achieve this with a generic {@code @Provides} method, but we'd need to have
   * <i>N</i> modules that each extend one base module.
   */
  private static <C, V> Map<String, Provider<AndroidInjector.Factory<?>>> merge(
      Map<Class<? extends C>, V> classKeyedMap, Map<String, V> stringKeyedMap) {
    if (classKeyedMap.isEmpty()) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Map<String, Provider<AndroidInjector.Factory<?>>> safeCast = (Map) stringKeyedMap;
      return safeCast;
    }

    Map<String, V> merged =
        newLinkedHashMapWithExpectedSize(classKeyedMap.size() + stringKeyedMap.size());
    merged.putAll(stringKeyedMap);
    for (Entry<Class<? extends C>, V> entry : classKeyedMap.entrySet()) {
      merged.put(entry.getKey().getName(), entry.getValue());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Map<String, Provider<AndroidInjector.Factory<?>>> safeCast = (Map) merged;
    return Collections.unmodifiableMap(safeCast);
  }

  /**
   * Attempts to perform members-injection on {@code instance}, returning {@code true} if
   * successful, {@code false} otherwise.
   *
   * @throws InvalidInjectorBindingException if the injector factory bound for a class does not
   *     inject instances of that class
   */
  @CanIgnoreReturnValue
  public boolean maybeInject(T instance) {
    Provider<AndroidInjector.Factory<?>> factoryProvider =
        injectorFactories.get(instance.getClass().getName());
    if (factoryProvider == null) {
      return false;
    }

    @SuppressWarnings("unchecked")
    AndroidInjector.Factory<T> factory = (AndroidInjector.Factory<T>) factoryProvider.get();
    try {
      AndroidInjector<T> injector =
          checkNotNull(
              factory.create(instance), "%s.create(I) should not return null.", factory.getClass());

      injector.inject(instance);
      return true;
    } catch (ClassCastException e) {
      throw new InvalidInjectorBindingException(
          String.format(
              "%s does not implement AndroidInjector.Factory<%s>",
              factory.getClass().getCanonicalName(), instance.getClass().getCanonicalName()),
          e);
    }
  }

  /**
   * Performs members-injection on {@code instance}.
   *
   * @throws InvalidInjectorBindingException if the injector factory bound for a class does not
   *     inject instances of that class
   * @throws IllegalArgumentException if no {@link AndroidInjector.Factory} is bound for {@code
   *     instance}
   */
  @Override
  public void inject(T instance) {
    boolean wasInjected = maybeInject(instance);
    if (!wasInjected) {
      throw new IllegalArgumentException(errorMessageSuggestions(instance));
    }
  }

  /**
   * Exception thrown if an incorrect binding is made for a {@link AndroidInjector.Factory}. If you
   * see this exception, make sure the value in your {@code @ActivityKey(YourActivity.class)} or
   * {@code @FragmentKey(YourFragment.class)} matches the type argument of the injector factory.
   */
  @Beta
  public static final class InvalidInjectorBindingException extends RuntimeException {
    InvalidInjectorBindingException(String message, ClassCastException cause) {
      super(message, cause);
    }
  }

  /** Returns an error message with the class names that are supertypes of {@code instance}. */
  private String errorMessageSuggestions(T instance) {
    List<String> suggestions = new ArrayList<>();
    for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
      if (injectorFactories.containsKey(clazz.getCanonicalName())) {
        suggestions.add(clazz.getCanonicalName());
      }
    }

    return suggestions.isEmpty()
        ? String.format(NO_SUPERTYPES_BOUND_FORMAT, instance.getClass().getCanonicalName())
        : String.format(
            SUPERTYPES_BOUND_FORMAT, instance.getClass().getCanonicalName(), suggestions);
  }
}

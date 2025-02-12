/*
 * Copyright (C) 2024 The Dagger Authors.
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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A class keyed map that delegates to a string keyed map under the hood.
 *
 * <p>A {@code LazyClassKeyMap} is created for @LazyClassKey contributed map binding.
 */
public final class LazyClassKeyMap<V> implements Map<Class<?>, V> {
  private final Map<String, V> delegate;

  public static <V> Map<Class<?>, V> of(Map<String, V> delegate) {
    return new LazyClassKeyMap<>(delegate);
  }

  private LazyClassKeyMap(Map<String, V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public V get(@Nullable Object key) {
    if (!(key instanceof Class)) {
      throw new IllegalArgumentException("Key must be a class");
    }
    return (@NonNull V) delegate.get(((Class<?>) key).getName());
  }

  @Override
  public Set<Class<?>> keySet() {
    // This method will load all class keys, therefore no need to use @LazyClassKey annotated
    // bindings.
    throw new UnsupportedOperationException(
        "Maps created with @LazyClassKey do not support usage of keySet(). Consider @ClassKey"
            + " instead.");
  }

  @Override
  public Collection<V> values() {
    return delegate.values();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    if (!(key instanceof Class)) {
      throw new IllegalArgumentException("Key must be a class");
    }
    return delegate.containsKey(((Class<?>) key).getName());
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return delegate.containsValue(value);
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public Set<Map.Entry<Class<?>, V>> entrySet() {
    // This method will load all class keys, therefore no need to use @LazyClassKey annotated
    // bindings.
    throw new UnsupportedOperationException(
        "Maps created with @LazyClassKey do not support usage of entrySet(). Consider @ClassKey"
            + " instead.");
  }

  // The dagger map binding should be a immutable map.
  @Override
  public V remove(@Nullable Object key) {
    throw new UnsupportedOperationException("Dagger map bindings are immutable");
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException("Dagger map bindings are immutable");
  }

  @Override
  public V put(Class<?> key, V value) {
    throw new UnsupportedOperationException("Dagger map bindings are immutable");
  }

  @Override
  public void putAll(Map<? extends Class<?>, ? extends V> map) {
    throw new UnsupportedOperationException("Dagger map bindings are immutable");
  }

  /** Wrapper around {@link MapFactory}. */
  public static class MapFactory<V> implements Factory<Map<Class<?>, V>> {
    Factory<Map<String, V>> delegate;

    public static <V> MapFactory<V> of(Factory<Map<String, V>> delegate) {
      return new MapFactory<V>(delegate);
    }

    private MapFactory(Factory<Map<String, V>> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Map<Class<?>, V> get() {
      return LazyClassKeyMap.of(delegate.get());
    }
  }

  /** Wrapper around for {@link MapProviderFactory}. */
  public static class MapProviderFactory<V> implements Factory<Map<Class<?>, Provider<V>>> {
    Factory<Map<String, Provider<V>>> delegate;

    public static <V> MapProviderFactory<V> of(Factory<Map<String, Provider<V>>> delegate) {
      return new MapProviderFactory<V>(delegate);
    }

    private MapProviderFactory(Factory<Map<String, Provider<V>>> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Map<Class<?>, Provider<V>> get() {
      return LazyClassKeyMap.of(delegate.get());
    }
  }
}

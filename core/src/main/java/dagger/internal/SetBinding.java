/*
 * Copyright (C) 2012 Google, Inc.
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
package dagger.internal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@code Binding<T>} which contains contributors (other bindings marked with
 * {@code @Provides} {@code @OneOf}), to which it delegates provision
 * requests on an as-needed basis.
 */
public final class SetBinding<T> extends Binding<Set<T>> {

  @SuppressWarnings("unchecked")
  public static <T> void add(Map<String, Binding<?>> bindings, String setKey, Binding<?> binding) {
    Binding<?> previous = bindings.get(setKey);
    SetBinding<T> setBinding;
    if (previous instanceof SetBinding) {
      setBinding = (SetBinding) previous;
    } else if (previous != null) {
      throw new IllegalArgumentException("Duplicate:\n    " + previous + "\n    " + binding);
    } else {
      setBinding = new SetBinding<T>(setKey, binding.requiredBy);
      bindings.put(setKey, setBinding);
    }
    setBinding.contributors.add(Linker.scope(binding));
  }

  private final Set<Binding<?>> contributors = new LinkedHashSet<Binding<?>>();

  public SetBinding(String key, Object requiredBy) {
    super(key, null, false, requiredBy);
  }

  @Override public void attach(Linker linker) {
    for (Binding<?> contributor : contributors) {
      contributor.attach(linker);
    }
  }

  @SuppressWarnings("unchecked") // Only Binding<T> and Set<T> are added to contributors.
  @Override public Set<T> get() {
    Set<T> result = new LinkedHashSet<T>(contributors.size());
    for (Binding<?> contributor : contributors) {
      Object contribution = contributor.get(); // Let runtime exceptions through.
      if (contributor.provideKey.equals(provideKey)) {
        result.addAll((Set<T>) contribution);
      } else {
        result.add((T) contribution);
      }
    }
    return Collections.unmodifiableSet(result);
  }

  @Override public void getDependencies(
      Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {
    getBindings.addAll(contributors);
  }

  @Override public void injectMembers(Set<T> t) {
    throw new UnsupportedOperationException("Cannot inject into a Set binding");
  }

  @Override public String toString() {
    return "SetBinding" + contributors;
  }
}

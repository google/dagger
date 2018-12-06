/*
 * Copyright (C) 2018 The Dagger Authors.
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

/**
 * A {@link Factory} that always throws on calls to {@link Factory#get()}. This is necessary in
 * ahead-of-time subcomponents mode, where modifiable binding methods need to return a {@code
 * Provider<T>} to a framework instance initialization that is pruned and no longer in the binding
 * graph, but was present in a superclass implementation. This class fulfills that requirement but
 * is still practically unusable.
 */
public final class MissingBindingFactory<T> implements Factory<T> {
  private static final MissingBindingFactory<Object> INSTANCE = new MissingBindingFactory<>();

  private MissingBindingFactory() {}

  @SuppressWarnings({"unchecked", "rawtypes"}) // safe covariant cast
  public static <T> Factory<T> create() {
    return (Factory) INSTANCE;
  }

  @Override
  public T get() {
    throw new AssertionError(
        "This binding is not part of the final binding graph. The key was requested by a binding "
            + "that was believed to possibly be part of the graph, but is no longer requested. "
            + "If this exception is thrown, it is the result of a Dagger bug.");
  }
}

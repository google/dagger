/*
 * Copyright (C) 2014 Google, Inc.
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
 * A {@link Factory} implementation that returns a single instance for all invocations of
 * {@link #get}.
 *
 * <p>Note that while this is a {@link Factory} implementation, and thus unscoped, each call to
 * {@link #get} will always return the same instance.  As such, any scoping applied to this factory
 * is redundant and unnecessary.  However, using this with {@link DoubleCheck#provider} is valid and
 * may be desired for testing or contractual guarantees.
 *
 * @author Gregory Kick
 * @since 2.0
 */
public final class InstanceFactory<T> implements Factory<T> {
  public static <T> Factory<T> create(T instance) {
    if (instance == null) {
      throw new NullPointerException();
    }
    return new InstanceFactory<T>(instance);
  }

  private final T instance;

  private InstanceFactory(T instance) {
    this.instance = instance;
  }

  @Override
  public T get() {
    return instance;
  }
}

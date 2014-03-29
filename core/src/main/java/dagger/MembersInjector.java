/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2009 Google Inc.
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
package dagger;

/**
 * Injects dependencies into the fields and methods on instances of type {@code T}. Ignores the
 * presence or absence of an injectable constructor.
 *
 * @param <T> type to inject members of
 *
 * @author Bob Lee
 * @author Jesse Wilson
 * @since 2.0 (since 1.0 without the provision that {@link #injectMembers} cannot accept
 *      {@code null})
 */
public interface MembersInjector<T> {

  /**
   * Injects dependencies into the fields and methods of {@code instance}. Ignores the presence or
   * absence of an injectable constructor.
   *
   * <p>Whenever the object graph creates an instance, it performs this injection automatically
   * (after first performing constructor injection), so if you're able to let the object graph
   * create all your objects for you, you'll never need to use this method.
   *
   * @param instance into which members are to be injected
   * @throws NullPointerException if {@code instance} is {@code null}
   */
  void injectMembers(T instance);
}

/*
 * Copyright (C) 2014 Google Inc.
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
package dagger2.internal;

import dagger2.MembersInjector;
import javax.inject.Inject;

/**
 * Basic {@link MembersInjector} implementations used by the framework.
 *
 * @author Gregory Kick
 * @since 2.0
 */
public final class MembersInjectors {
  /**
   * Returns a {@link MembersInjector} implementation that injects no members
   *
   * <p>Note that there is no verification that the type being injected does not have {@link Inject}
   * members, so care should be taken to ensure appropriate use.
   */
  @SuppressWarnings("unchecked")
  public static <T> MembersInjector<T> noOp() {
    return (MembersInjector<T>) NoOpMembersInjector.INSTANCE;
  }

  private static enum NoOpMembersInjector implements MembersInjector<Object> {
    INSTANCE;

    @Override public void injectMembers(Object instance) {
      if (instance == null) {
        throw new NullPointerException();
      }
    }
  }

  /**
   * Returns a {@link MembersInjector} that delegates to the {@link MembersInjector} of its
   * supertype.  This is useful for cases where a type is known not to have its own {@link Inject}
   * members, but must still inject members on its supertype(s).
   *
   * <p>Note that there is no verification that the type being injected does not have {@link Inject}
   * members, so care should be taken to ensure appropriate use.
   */
  @SuppressWarnings("unchecked")
  public static <T> MembersInjector<T> delegatingTo(MembersInjector<? super T> delegate) {
    return (MembersInjector<T>) delegate;
  }

  private MembersInjectors() {}
}

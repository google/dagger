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
package dagger.internal;

import dagger.MembersInjector;
import java.util.Set;
import javax.inject.Provider;

/**
 * Injects a value of a specific type.
 */
public abstract class Binding<T> implements Provider<T>, MembersInjector<T> {
  public static final Binding<Object> UNRESOLVED = new Binding<Object>(null, null, false, null) {
    @Override public Object get() {
      throw new AssertionError("Unresolved binding should never be called to inject.");
    }
    @Override public void injectMembers(Object t) {
      throw new AssertionError("Unresolved binding should never be called to inject.");
    }
  };
  protected static final boolean IS_SINGLETON = true;
  protected static final boolean NOT_SINGLETON = false;

  /** Set if the provided instance is always the same object. */
  private static final int SINGLETON = 1 << 0;

  /** Set if this binding's {@link #attach} completed without any missing dependencies. */
  private static final int LINKED = 1 << 1;

  /** Set if {@link ProblemDetector} is actively visiting this binding. */
  private static final int VISITING = 1 << 2;

  /** Set if {@link ProblemDetector} has confirmed this binding has no circular dependencies. */
  private static final int CYCLE_FREE = 1 << 3;

  private static final int DEPENDED_ON = 1 << 4;

  private static final int LIBRARY = 1 << 5;

  /** The key used to provide instances of 'T', or null if this binding cannot provide instances. */
  public final String provideKey;

  /** The key used to inject members of 'T', or null if this binding cannot inject members. */
  public final String membersKey;

  /** Bitfield of states like SINGLETON and LINKED. */
  private int bits;

  public final Object requiredBy;

  protected Binding(String provideKey, String membersKey, boolean singleton, Object requiredBy) {
    if (singleton && provideKey == null) {
      throw new InvalidBindingException(Keys.getClassName(membersKey),
          "is exclusively members injected and therefore cannot be scoped");
    }
    this.provideKey = provideKey;
    this.membersKey = membersKey;
    this.requiredBy = requiredBy;
    this.bits = (singleton ? SINGLETON : 0);
  }

  /**
   * Links this binding to its dependencies.
   */
  public void attach(Linker linker) {
  }

  @Override public void injectMembers(T t) {
    // If no members to inject, no-op.  Some classes will have no injectable members even
    // if their supertypes do.
  }

  @Override public T get() {
    throw new UnsupportedOperationException("No injectable constructor on " + getClass().getName());
  }

  /**
   * Populates {@code getBindings} and {@code injectMembersBindings} with the
   * bindings used by this binding to satisfy {@link #get} and {@link
   * #injectMembers} calls, respectively.
   *
   * @param getBindings the bindings required by this binding's {@code get}
   *     method. Although {@code get} usually calls into {@code injectMembers},
   *     this <i>does not</i> contain the injectMembers bindings.
   * @param injectMembersBindings the bindings required by this binding's {@code
   *     injectMembers} method.
   */
  public void getDependencies(Set<Binding<?>> getBindings, Set<Binding<?>> injectMembersBindings) {
    // Do nothing.  No override == no dependencies to contribute.
  }

  void setLinked() {
    bits |= LINKED;
  }

  public boolean isLinked() {
    return (bits & LINKED) != 0;
  }

  boolean isSingleton() {
    return (bits & SINGLETON) != 0;
  }

  public boolean isVisiting() {
    return (bits & VISITING) != 0;
  }

  public void setVisiting(boolean visiting) {
    this.bits = visiting ? (bits | VISITING) : (bits & ~VISITING);
  }

  public boolean isCycleFree() {
    return (bits & CYCLE_FREE) != 0;
  }

  public void setCycleFree(boolean cycleFree) {
    this.bits = cycleFree ? (bits | CYCLE_FREE) : (bits & ~CYCLE_FREE);
  }

  public void setLibrary(boolean library) {
    this.bits = library ? (bits | LIBRARY) : (bits & ~LIBRARY);
  }

  public boolean library() {
    return (bits & LIBRARY) != 0;
  }

  public void setDependedOn(boolean dependedOn) {
    this.bits = dependedOn ? (bits | DEPENDED_ON) : (bits & ~DEPENDED_ON);
  }

  public boolean dependedOn() {
    return (bits & DEPENDED_ON) != 0;
  }

  @Override public String toString() {
    return getClass().getSimpleName()
            + "[provideKey=\"" + provideKey + "\", memberskey=\"" + membersKey + "\"]";
  }

  /** An exception thrown by anything attempting to construct a binding which is invalid. */
  public static class InvalidBindingException extends RuntimeException {
    public final String type;

    public InvalidBindingException(String type, String error) {
      super(error);
      this.type = type;
    }

    public InvalidBindingException(String type, String error, Throwable cause) {
      super("Binding for " + type + " was invalid: " + error, cause);
      this.type = type;
    }
  }
}

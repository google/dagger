/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.functional.multipackage.a;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Retention;
import javax.inject.Inject;
import javax.inject.Qualifier;

/** Types with package-private cyclic and intersection bounds for cross-package testing. */
public final class InaccessibleBoundedGenerics {
  interface InaccessibleBound<T extends InaccessibleBound<T>> {
    String boundValue();
  }

  interface OtherInaccessibleBound {
    String otherValue();
  }

  static final class InaccessibleBoundImpl
      implements InaccessibleBound<InaccessibleBoundImpl>, OtherInaccessibleBound {
    @Inject
    InaccessibleBoundImpl() {}

    @Override
    public String boundValue() {
      return "bound";
    }

    @Override
    public String otherValue() {
      return "other";
    }
  }

  public static class GenericConstructorInjectClass<
      T extends InaccessibleBound<T> & OtherInaccessibleBound> {
    final T constructorDep;

    @Inject
    GenericConstructorInjectClass(T constructorDep) {
      this.constructorDep = constructorDep;
    }

    public String summary() {
      return constructorDep.boundValue() + ":" + constructorDep.otherValue();
    }
  }

  // Intentionally combines constructor and member injection to test both _Factory and
  // _MembersInjector generation on a package-private generic class.
  @SuppressWarnings("BadInject")
  static class PackagePrivateGenericInjectClass<
      T extends InaccessibleBound<T> & OtherInaccessibleBound> {
    final T constructorDep;
    @Inject T fieldDep;
    T methodDep;

    @Inject
    PackagePrivateGenericInjectClass(T constructorDep) {
      this.constructorDep = constructorDep;
    }

    @Inject
    void injectMethod(T methodDep) {
      this.methodDep = methodDep;
    }

    String summary() {
      return constructorDep.boundValue()
          + ":"
          + fieldDep.otherValue()
          + ":"
          + methodDep.boundValue();
    }
  }

  public static class GenericMembersInjectedBase<
      T extends InaccessibleBound<T> & OtherInaccessibleBound> {
    @Inject T baseField;
    T baseMethodDep;

    @Inject
    void injectBaseMethod(T baseMethodDep) {
      this.baseMethodDep = baseMethodDep;
    }

    public String baseSummary() {
      return baseField.boundValue() + ":" + baseMethodDep.otherValue();
    }
  }

  public static final class ConcreteMembersInjected
      extends GenericMembersInjectedBase<InaccessibleBoundImpl> {}

  @Qualifier
  @Retention(RUNTIME)
  public @interface BoundedModuleValue {}

  @Module
  abstract static class GenericBoundedModule<
      T extends InaccessibleBound<T> & OtherInaccessibleBound> {
    @Provides
    @BoundedModuleValue
    String provideBoundedValue(T t) {
      return t.boundValue() + ":" + t.otherValue();
    }
  }

  @Module
  public static final class ConcreteBoundedModule
      extends GenericBoundedModule<InaccessibleBoundImpl> {}

  public static final class UsesInaccessibleBoundedGenerics {
    public final GenericConstructorInjectClass<?> publicGenericInject;
    private final PackagePrivateGenericInjectClass<?> packagePrivateGenericInject;
    public final String moduleProvidedValue;

    @Inject
    UsesInaccessibleBoundedGenerics(
        GenericConstructorInjectClass<InaccessibleBoundImpl> publicGenericInject,
        PackagePrivateGenericInjectClass<InaccessibleBoundImpl> packagePrivateGenericInject,
        @BoundedModuleValue String moduleProvidedValue) {
      this.publicGenericInject = publicGenericInject;
      this.packagePrivateGenericInject = packagePrivateGenericInject;
      this.moduleProvidedValue = moduleProvidedValue;
    }

    public String packagePrivateSummary() {
      return packagePrivateGenericInject.summary();
    }
  }

  private InaccessibleBoundedGenerics() {}
}

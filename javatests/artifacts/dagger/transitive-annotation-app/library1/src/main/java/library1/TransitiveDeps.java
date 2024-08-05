/*
 * Copyright (C) 2022 The Dagger Authors.
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

package library1;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import javax.inject.Inject;
import javax.inject.Named;
import library2.MyTransitiveType;

/**
 * Contains classes for regression cases for https://github.com/google/dagger/issues/3262.
 *
 * <p>These classes test that {@code @Provides} and {@code @Binds} methods can return types that
 * are not on the class path when building the component.
 */
public final class TransitiveDeps {
  static class ExtendsMyTransitiveType extends MyTransitiveType {}

  // Define a module that returns transitive types via @Provides and @Binds.
  @Module
  public abstract static class TransitiveDepsModule {
    @Named("UsesProvides")
    @Provides
    static MyTransitiveType provideMyTransitiveType() {
      return new MyTransitiveType();
    }

    @Named("UsesBinds")
    @Binds
    abstract MyTransitiveType bind(ExtendsMyTransitiveType impl);

    @Provides
    static ExtendsMyTransitiveType provideExtendsMyTransitiveType() {
      return new ExtendsMyTransitiveType();
    }

    @Provides
    static DependsOnMyTransitiveTypeViaProvides provideDependsOnMyTransitiveType(
        @Named("UsesProvides") MyTransitiveType myTransitiveProvidesType,
        @Named("UsesBinds") MyTransitiveType myTransitiveBindsType) {
      return new DependsOnMyTransitiveTypeViaProvides(
          myTransitiveProvidesType, myTransitiveBindsType);
    }
  }

  // Define a a class that depends on the transitive types via @Provides
  public static final class DependsOnMyTransitiveTypeViaProvides {
    private final MyTransitiveType myTransitiveProvidesType;
    private final MyTransitiveType myTransitiveBindsType;

    DependsOnMyTransitiveTypeViaProvides(
        MyTransitiveType myTransitiveProvidesType,
        MyTransitiveType myTransitiveBindsType) {
      this.myTransitiveProvidesType = myTransitiveProvidesType;
      this.myTransitiveBindsType = myTransitiveBindsType;
    }
  }

  // Define a a class that depends on the transitive types via @Inject
  public static final class DependsOnMyTransitiveTypeViaInject {
    private final MyTransitiveType myTransitiveProvidesType;
    private final MyTransitiveType myTransitiveBindsType;

    @Inject @Named("UsesProvides") MyTransitiveType myTransitiveProvidesTypeField;
    @Inject @Named("UsesBinds") MyTransitiveType myTransitiveBindsTypeField;

    @Inject
    DependsOnMyTransitiveTypeViaInject(
        @Named("UsesProvides") MyTransitiveType myTransitiveProvidesType,
        @Named("UsesBinds") MyTransitiveType myTransitiveBindsType) {
      this.myTransitiveProvidesType = myTransitiveProvidesType;
      this.myTransitiveBindsType = myTransitiveBindsType;
    }


    @Inject
    void methodInjection(
        @Named("UsesProvides") MyTransitiveType myTransitiveProvidesType,
        @Named("UsesBinds") MyTransitiveType myTransitiveBindsType) {}
  }

  // TODO(bcorso): Add entry point request for transitive type
  // TODO(bcorso): Add field/method injection request for transitive type
  // TODO(bcorso): Add component dependency provision

  private TransitiveDeps() {}
}

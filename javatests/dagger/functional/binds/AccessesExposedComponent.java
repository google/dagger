/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.binds;

import dagger.Component;
import dagger.functional.binds.subpackage.Exposed;
import dagger.functional.binds.subpackage.ExposedModule;
import dagger.functional.binds.subpackage.UsesExposedInjectsMembers;
import java.util.List;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * This component tests cases where the right-hand-side of a {@link dagger.Binds} method is not
 * accessible from the component, but the left-hand-side is. If the right-hand-side is represented
 * as a Provider (e.g. because it is scoped), then the raw {@code Provider.get()} will return {@link
 * Object}, which must be downcasted to the type accessible from the component. See {@code
 * instanceRequiresCast()} in {@link dagger.internal.codegen.DelegateBindingExpression}.
 */
@Singleton
@Component(modules = ExposedModule.class)
interface AccessesExposedComponent {
  Exposed exposed();
  Provider<Exposed> exposedProvider();

  List<? extends Exposed> listOfExposed();
  Provider<List<? extends Exposed>> providerOfListOfExposed();

  UsesExposedInjectsMembers usesExposedInjectsMembers();

  /**
   * This provider needs a {@code Provider<ExposedInjectsMembers>}, which is bound to a {@code
   * Provider<NotExposedInjectsMembers>}. This method is here to make sure that the cast happens
   * appropriately.
   */
  Provider<UsesExposedInjectsMembers> usesExposedInjectsMembersProvider();
}

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

package dagger.internal.codegen;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.Component;
import dagger.Provides;
import dagger.model.Key;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Maintains the collection of provision bindings from {@link Inject} constructors and members
 * injection bindings from {@link Inject} fields and methods known to the annotation processor. Note
 * that this registry <b>does not</b> handle any explicit bindings (those from {@link Provides}
 * methods, {@link Component} dependencies, etc.).
 */
interface InjectBindingRegistry {
  /**
   * Returns a {@link ProvisionBinding} for {@code key}. If none has been registered yet, registers
   * one.
   */
  Optional<ProvisionBinding> getOrFindProvisionBinding(Key key);

  /**
   * Returns a {@link MembersInjectionBinding} for {@code key}. If none has been registered yet,
   * registers one, along with all necessary members injection bindings for superclasses.
   */
  Optional<MembersInjectionBinding> getOrFindMembersInjectionBinding(Key key);

  /**
   * Returns a {@link ProvisionBinding} for a {@link dagger.MembersInjector} of {@code key}. If none
   * has been registered yet, registers one.
   */
  Optional<ProvisionBinding> getOrFindMembersInjectorProvisionBinding(Key key);

  @CanIgnoreReturnValue
  Optional<ProvisionBinding> tryRegisterConstructor(ExecutableElement constructorElement);

  @CanIgnoreReturnValue
  Optional<MembersInjectionBinding> tryRegisterMembersInjectedType(TypeElement typeElement);

  /**
   * This method ensures that sources for all registered {@link Binding bindings} (either explicitly
   * or implicitly via {@link #getOrFindMembersInjectionBinding} or {@link
   * #getOrFindProvisionBinding}) are generated.
   */
  void generateSourcesForRequiredBindings(
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator)
      throws SourceFileGenerationException;
}

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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.binding.BindingType.PRODUCTION;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.multibindings.Multibinds;
import dagger.spi.model.BindingKind;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.DaggerElement;
import dagger.spi.model.DaggerTypeElement;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import dagger.spi.model.Scope;
import java.util.Optional;
import javax.lang.model.element.Element;

/**
 * An implementation of {@link dagger.spi.model.Binding} that also exposes {@link
 * BindingDeclaration}s associated with the binding.
 */
// TODO(dpb): Consider a supertype of dagger.spi.model.Binding that
// dagger.internal.codegen.binding.Binding
// could also implement.
@AutoValue
public abstract class BindingNode implements dagger.spi.model.Binding {
  public static BindingNode create(
      ComponentPath component,
      Binding delegate,
      ImmutableSet<MultibindingDeclaration> multibindingDeclarations,
      ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations,
      ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations,
      BindingDeclarationFormatter bindingDeclarationFormatter) {
    BindingNode node =
        new AutoValue_BindingNode(
            component,
            delegate,
            multibindingDeclarations,
            optionalBindingDeclarations,
            subcomponentDeclarations);
    node.bindingDeclarationFormatter = checkNotNull(bindingDeclarationFormatter);
    return node;
  }

  private BindingDeclarationFormatter bindingDeclarationFormatter;

  public abstract Binding delegate();

  public abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();

  public abstract ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations();

  public abstract ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations();

  /**
   * The {@link Element}s (other than the binding's {@link #bindingElement()}) that are associated
   * with the binding.
   *
   * <ul>
   *   <li>{@linkplain BindsOptionalOf optional binding} declarations
   *   <li>{@linkplain Module#subcomponents() module subcomponent} declarations
   *   <li>{@linkplain Multibinds multibinding} declarations
   * </ul>
   */
  public final Iterable<BindingDeclaration> associatedDeclarations() {
    return Iterables.concat(
        multibindingDeclarations(), optionalBindingDeclarations(), subcomponentDeclarations());
  }

  @Override
  public Key key() {
    return delegate().key();
  }

  @Override
  public ImmutableSet<DependencyRequest> dependencies() {
    return delegate().dependencies();
  }

  @Override
  public Optional<DaggerElement> bindingElement() {
    return delegate().bindingElement().map(DaggerElement::fromJava);
  }

  @Override
  public Optional<DaggerTypeElement> contributingModule() {
    return delegate().contributingModule().map(DaggerTypeElement::fromJava);
  }

  @Override
  public boolean requiresModuleInstance() {
    return delegate().requiresModuleInstance();
  }

  @Override
  public Optional<Scope> scope() {
    return delegate().scope();
  }

  @Override
  public boolean isNullable() {
    return delegate().isNullable();
  }

  @Override
  public boolean isProduction() {
    return delegate().bindingType().equals(PRODUCTION);
  }

  @Override
  public BindingKind kind() {
    return delegate().kind();
  }

  @Override
  public final String toString() {
    return bindingDeclarationFormatter.format(delegate());
  }
}

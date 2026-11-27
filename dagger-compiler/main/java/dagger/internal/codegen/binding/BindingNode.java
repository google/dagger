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

import static androidx.room3.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.binding.BindingType.PRODUCTION;

import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.processing.XElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.DaggerElement;
import dagger.internal.codegen.model.DaggerTypeElement;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;
import javax.inject.Inject;

/**
 * An implementation of {@link dagger.internal.codegen.model.Binding} that also exposes {@link
 * Declaration}s associated with the binding.
 */
// TODO(dpb): Consider a supertype of dagger.internal.codegen.model.Binding that
// dagger.internal.codegen.binding.Binding
// could also implement.
@AutoValue
public abstract class BindingNode implements dagger.internal.codegen.model.Binding {
  private DeclarationFormatter declarationFormatter;

  public abstract Binding delegate();

  public abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();

  public abstract ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations();

  public abstract ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations();

  /**
   * The elements (other than the binding's {@link #bindingElement()}) that are associated with the
   * binding.
   *
   * <ul>
   *   <li>{@linkplain BindsOptionalOf optional binding} declarations
   *   <li>{@linkplain Module#subcomponents() module subcomponent} declarations
   *   <li>{@linkplain Multibinds multibinding} declarations
   * </ul>
   */
  public final Iterable<Declaration> associatedDeclarations() {
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
    return delegate().bindingElement().map(DaggerElement::from);
  }

  @Override
  public Optional<DaggerTypeElement> contributingModule() {
    return delegate().contributingModule().map(DaggerTypeElement::from);
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
    return declarationFormatter.format(delegate());
  }

  /**
   * Returns the type that identifies the binding.
   *
   * <p>This is the type that is referenced by @BindingIdentifier. If @BindingIdentifier is not
   * specified on the binding, then @Inject binding will return its own type as the identifier, and
   * all other types of bindings will return empty.
   */
  @Memoized
  public Optional<XClassName> bindingIdentifier() {
    if (bindingElement().isPresent()) {
      XElement element = bindingElement().get().xprocessing();
      if (element.hasAnnotation(XTypeNames.BINDING_IDENTIFIER)) {
        return Optional.of(
            element
                .getAnnotation(XTypeNames.BINDING_IDENTIFIER)
                .getAsType("value")
                .getTypeElement()
                .asClassName());
      }
      if (InjectionAnnotations.hasInjectAnnotation(element)) {
        XElement enclosingElement = element.getEnclosingElement();
        if (enclosingElement != null && isTypeElement(enclosingElement)) {
          return Optional.of(XElements.asTypeElement(enclosingElement).asClassName());
        }
      }
    }
    return Optional.empty();
  }

  public BindingNode withBindingType(BindingType bindingType) {
    return create(
        componentPath(),
        ((ContributionBinding) delegate()).withBindingType(bindingType),
        multibindingDeclarations(),
        optionalBindingDeclarations(),
        subcomponentDeclarations(),
        declarationFormatter);
  }

  static final class Factory {
    private final DeclarationFormatter declarationFormatter;

    @Inject
    Factory(DeclarationFormatter declarationFormatter) {
      this.declarationFormatter = declarationFormatter;
    }

    public BindingNode forContributionBindings(
        ComponentPath component,
        ContributionBinding delegate,
        Iterable<MultibindingDeclaration> multibindingDeclarations,
        Iterable<OptionalBindingDeclaration> optionalBindingDeclarations,
        Iterable<SubcomponentDeclaration> subcomponentDeclarations) {
      return create(
          component,
          delegate,
          ImmutableSet.copyOf(multibindingDeclarations),
          ImmutableSet.copyOf(optionalBindingDeclarations),
          ImmutableSet.copyOf(subcomponentDeclarations));
    }

    public BindingNode forMembersInjectionBinding(
        ComponentPath component, MembersInjectionBinding delegate) {
      return create(component, delegate, ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of());
    }

    private BindingNode create(
        ComponentPath component,
        Binding delegate,
        ImmutableSet<MultibindingDeclaration> multibindingDeclarations,
        ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations,
        ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations) {
      return BindingNode.create(
          component,
          delegate,
          multibindingDeclarations,
          optionalBindingDeclarations,
          subcomponentDeclarations,
          declarationFormatter);
    }
  }

  private static BindingNode create(
      ComponentPath component,
      Binding delegate,
      ImmutableSet<MultibindingDeclaration> multibindingDeclarations,
      ImmutableSet<OptionalBindingDeclaration> optionalBindingDeclarations,
      ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations,
      DeclarationFormatter declarationFormatter) {
    BindingNode node =
        new AutoValue_BindingNode(
            component,
            delegate,
            multibindingDeclarations,
            optionalBindingDeclarations,
            subcomponentDeclarations);
    node.declarationFormatter = declarationFormatter;
    return node;
  }
}

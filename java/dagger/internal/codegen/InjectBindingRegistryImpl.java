/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.InjectionAnnotations.injectedConstructors;
import static dagger.internal.codegen.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.Keys.isValidMembersInjectionKey;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import dagger.Component;
import dagger.MembersInjector;
import dagger.Provides;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Key;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

/**
 * Maintains the collection of provision bindings from {@link Inject} constructors and members
 * injection bindings from {@link Inject} fields and methods known to the annotation processor.
 * Note that this registry <b>does not</b> handle any explicit bindings (those from {@link Provides}
 * methods, {@link Component} dependencies, etc.).
 */
@Singleton
final class InjectBindingRegistryImpl implements InjectBindingRegistry {
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final Messager messager;
  private final InjectValidator injectValidator;
  private final InjectValidator injectValidatorWhenGeneratingCode;
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;
  private final CompilerOptions compilerOptions;

  final class BindingsCollection<B extends Binding> {
    private final Class<?> factoryClass;
    private final Map<Key, B> bindingsByKey = Maps.newLinkedHashMap();
    private final Deque<B> bindingsRequiringGeneration = new ArrayDeque<>();
    private final Set<Key> materializedBindingKeys = Sets.newLinkedHashSet();

    BindingsCollection(Class<?> factoryClass) {
      this.factoryClass = factoryClass;
    }

    void generateBindings(SourceFileGenerator<B> generator) throws SourceFileGenerationException {
      for (B binding = bindingsRequiringGeneration.poll();
          binding != null;
          binding = bindingsRequiringGeneration.poll()) {
        checkState(!binding.unresolved().isPresent());
        if (injectValidatorWhenGeneratingCode.isValidType(binding.key().type())) {
          generator.generate(binding);
        }
        materializedBindingKeys.add(binding.key());
      }
      // Because Elements instantiated across processing rounds are not guaranteed to be equals() to
      // the logically same element, clear the cache after generating
      bindingsByKey.clear();
    }

    /** Returns a previously cached binding. */
    B getBinding(Key key) {
      return bindingsByKey.get(key);
    }

    /** Caches the binding and generates it if it needs generation. */
    void tryRegisterBinding(B binding, boolean warnIfNotAlreadyGenerated) {
      tryToCacheBinding(binding);
      tryToGenerateBinding(binding, warnIfNotAlreadyGenerated);
    }

    /**
     * Tries to generate a binding, not generating if it already is generated. For resolved
     * bindings, this will try to generate the unresolved version of the binding.
     */
    void tryToGenerateBinding(B binding, boolean warnIfNotAlreadyGenerated) {
      if (shouldGenerateBinding(binding, generatedClassNameForBinding(binding))) {
        bindingsRequiringGeneration.offer(binding);
        if (compilerOptions.warnIfInjectionFactoryNotGeneratedUpstream()
            && warnIfNotAlreadyGenerated) {
          messager.printMessage(
              Kind.NOTE,
              String.format(
                  "Generating a %s for %s. "
                      + "Prefer to run the dagger processor over that class instead.",
                  factoryClass.getSimpleName(),
                  types.erasure(binding.key().type()))); // erasure to strip <T> from msgs.
        }
      }
    }

    /** Returns true if the binding needs to be generated. */
    private boolean shouldGenerateBinding(B binding, ClassName factoryName) {
      return !binding.unresolved().isPresent()
          && !materializedBindingKeys.contains(binding.key())
          && !bindingsRequiringGeneration.contains(binding)
          && elements.getTypeElement(factoryName) == null;
    }

    /** Caches the binding for future lookups by key. */
    private void tryToCacheBinding(B binding) {
      // We only cache resolved bindings or unresolved bindings w/o type arguments.
      // Unresolved bindings w/ type arguments aren't valid for the object graph.
      if (binding.unresolved().isPresent()
          || binding.bindingTypeElement().get().getTypeParameters().isEmpty()) {
        Key key = binding.key();
        Binding previousValue = bindingsByKey.put(key, binding);
        checkState(previousValue == null || binding.equals(previousValue),
            "couldn't register %s. %s was already registered for %s",
            binding, previousValue, key);
      }
    }
  }

  private final BindingsCollection<ProvisionBinding> provisionBindings =
      new BindingsCollection<>(Provider.class);
  private final BindingsCollection<MembersInjectionBinding> membersInjectionBindings =
      new BindingsCollection<>(MembersInjector.class);

  @Inject
  InjectBindingRegistryImpl(
      DaggerElements elements,
      DaggerTypes types,
      Messager messager,
      InjectValidator injectValidator,
      KeyFactory keyFactory,
      BindingFactory bindingFactory,
      CompilerOptions compilerOptions) {
    this.elements = elements;
    this.types = types;
    this.messager = messager;
    this.injectValidator = injectValidator;
    this.injectValidatorWhenGeneratingCode = injectValidator.whenGeneratingCode();
    this.keyFactory = keyFactory;
    this.bindingFactory = bindingFactory;
    this.compilerOptions = compilerOptions;
  }

  // TODO(dpb): make the SourceFileGenerators fields so they don't have to be passed in
  @Override
  public void generateSourcesForRequiredBindings(
      SourceFileGenerator<ProvisionBinding> factoryGenerator,
      SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator)
      throws SourceFileGenerationException {
    provisionBindings.generateBindings(factoryGenerator);
    membersInjectionBindings.generateBindings(membersInjectorGenerator);
  }

  /**
   * Registers the binding for generation and later lookup. If the binding is resolved, we also
   * attempt to register an unresolved version of it.
   */
  private void registerBinding(ProvisionBinding binding, boolean warnIfNotAlreadyGenerated) {
    provisionBindings.tryRegisterBinding(binding, warnIfNotAlreadyGenerated);
    if (binding.unresolved().isPresent()) {
      provisionBindings.tryToGenerateBinding(binding.unresolved().get(), warnIfNotAlreadyGenerated);
    }
  }

  /**
   * Registers the binding for generation and later lookup. If the binding is resolved, we also
   * attempt to register an unresolved version of it.
   */
  private void registerBinding(MembersInjectionBinding binding, boolean warnIfNotAlreadyGenerated) {
    /*
     * We generate MembersInjector classes for types with @Inject constructors only if they have any
     * injection sites.
     *
     * We generate MembersInjector classes for types without @Inject constructors only if they have
     * local (non-inherited) injection sites.
     *
     * Warn only when registering bindings post-hoc for those types.
     */
    warnIfNotAlreadyGenerated =
        warnIfNotAlreadyGenerated
            && (!injectedConstructors(binding.membersInjectedType()).isEmpty()
                ? !binding.injectionSites().isEmpty()
                : binding.hasLocalInjectionSites());
    membersInjectionBindings.tryRegisterBinding(binding, warnIfNotAlreadyGenerated);
    if (binding.unresolved().isPresent()) {
      membersInjectionBindings.tryToGenerateBinding(
          binding.unresolved().get(), warnIfNotAlreadyGenerated);
    }
  }

  @Override
  public Optional<ProvisionBinding> tryRegisterConstructor(ExecutableElement constructorElement) {
    return tryRegisterConstructor(constructorElement, Optional.empty(), false);
  }

  @CanIgnoreReturnValue
  private Optional<ProvisionBinding> tryRegisterConstructor(
      ExecutableElement constructorElement,
      Optional<TypeMirror> resolvedType,
      boolean warnIfNotAlreadyGenerated) {
    TypeElement typeElement = MoreElements.asType(constructorElement.getEnclosingElement());
    DeclaredType type = MoreTypes.asDeclared(typeElement.asType());
    Key key = keyFactory.forInjectConstructorWithResolvedType(type);
    ProvisionBinding cachedBinding = provisionBindings.getBinding(key);
    if (cachedBinding != null) {
      return Optional.of(cachedBinding);
    }

    ValidationReport<TypeElement> report = injectValidator.validateConstructor(constructorElement);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      ProvisionBinding binding = bindingFactory.injectionBinding(constructorElement, resolvedType);
      registerBinding(binding, warnIfNotAlreadyGenerated);
      if (!binding.injectionSites().isEmpty()) {
        tryRegisterMembersInjectedType(typeElement, resolvedType, warnIfNotAlreadyGenerated);
      }
      return Optional.of(binding);
    }
    return Optional.empty();
  }

  @Override
  public Optional<MembersInjectionBinding> tryRegisterMembersInjectedType(TypeElement typeElement) {
    return tryRegisterMembersInjectedType(typeElement, Optional.empty(), false);
  }

  @CanIgnoreReturnValue
  private Optional<MembersInjectionBinding> tryRegisterMembersInjectedType(
      TypeElement typeElement,
      Optional<TypeMirror> resolvedType,
      boolean warnIfNotAlreadyGenerated) {
    DeclaredType type = MoreTypes.asDeclared(typeElement.asType());
    Key key = keyFactory.forInjectConstructorWithResolvedType(type);
    MembersInjectionBinding cachedBinding = membersInjectionBindings.getBinding(key);
    if (cachedBinding != null) {
      return Optional.of(cachedBinding);
    }

    ValidationReport<TypeElement> report =
        injectValidator.validateMembersInjectionType(typeElement);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      MembersInjectionBinding binding = bindingFactory.membersInjectionBinding(type, resolvedType);
      registerBinding(binding, warnIfNotAlreadyGenerated);
      for (Optional<DeclaredType> supertype = types.nonObjectSuperclass(type);
          supertype.isPresent();
          supertype = types.nonObjectSuperclass(supertype.get())) {
        getOrFindMembersInjectionBinding(keyFactory.forMembersInjectedType(supertype.get()));
      }
      return Optional.of(binding);
    }
    return Optional.empty();
  }

  @CanIgnoreReturnValue
  @Override
  public Optional<ProvisionBinding> getOrFindProvisionBinding(Key key) {
    checkNotNull(key);
    if (!isValidImplicitProvisionKey(key, types)) {
      return Optional.empty();
    }
    ProvisionBinding binding = provisionBindings.getBinding(key);
    if (binding != null) {
      return Optional.of(binding);
    }

    // ok, let's see if we can find an @Inject constructor
    TypeElement element = MoreElements.asType(types.asElement(key.type()));
    ImmutableSet<ExecutableElement> injectConstructors = injectedConstructors(element);
    switch (injectConstructors.size()) {
      case 0:
        // No constructor found.
        return Optional.empty();
      case 1:
        return tryRegisterConstructor(
            Iterables.getOnlyElement(injectConstructors), Optional.of(key.type()), true);
      default:
        throw new IllegalStateException("Found multiple @Inject constructors: "
            + injectConstructors);
    }
  }

  @CanIgnoreReturnValue
  @Override
  public Optional<MembersInjectionBinding> getOrFindMembersInjectionBinding(Key key) {
    checkNotNull(key);
    // TODO(gak): is checking the kind enough?
    checkArgument(isValidMembersInjectionKey(key));
    MembersInjectionBinding binding = membersInjectionBindings.getBinding(key);
    if (binding != null) {
      return Optional.of(binding);
    }
    Optional<MembersInjectionBinding> newBinding =
        tryRegisterMembersInjectedType(
            MoreTypes.asTypeElement(key.type()), Optional.of(key.type()), true);
    return newBinding;
  }

  @Override
  public Optional<ProvisionBinding> getOrFindMembersInjectorProvisionBinding(Key key) {
    if (!isValidMembersInjectionKey(key)) {
      return Optional.empty();
    }
    Key membersInjectionKey = keyFactory.forMembersInjectedType(types.unwrapType(key.type()));
    return getOrFindMembersInjectionBinding(membersInjectionKey)
        .map(binding -> bindingFactory.membersInjectorBinding(key, binding));
  }
}

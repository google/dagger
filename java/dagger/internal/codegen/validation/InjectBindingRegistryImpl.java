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

package dagger.internal.codegen.validation;

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.base.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.base.Keys.isValidMembersInjectionKey;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectedConstructors;
import static dagger.internal.codegen.binding.InjectionAnnotations.injectedConstructors;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XTypes.erasedTypeName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.nonObjectSuperclass;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;

import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import dagger.Component;
import dagger.Provides;
import dagger.internal.codegen.base.SourceFileGenerationException;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.Key;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.tools.Diagnostic.Kind;

/**
 * Maintains the collection of provision bindings from {@link Inject} constructors and members
 * injection bindings from {@link Inject} fields and methods known to the annotation processor. Note
 * that this registry <b>does not</b> handle any explicit bindings (those from {@link Provides}
 * methods, {@link Component} dependencies, etc.).
 */
@Singleton
final class InjectBindingRegistryImpl implements InjectBindingRegistry {
  private final XProcessingEnv processingEnv;
  private final XMessager messager;
  private final InjectValidator injectValidator;
  private final InjectValidator injectValidatorWhenGeneratingCode;
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;
  private final CompilerOptions compilerOptions;

  final class BindingsCollection<B extends Binding> {
    private final ClassName factoryClass;
    private final Map<Key, B> bindingsByKey = Maps.newLinkedHashMap();
    private final Deque<B> bindingsRequiringGeneration = new ArrayDeque<>();
    private final Set<Key> materializedBindingKeys = Sets.newLinkedHashSet();

    BindingsCollection(ClassName factoryClass) {
      this.factoryClass = factoryClass;
    }

    void generateBindings(SourceFileGenerator<B> generator) throws SourceFileGenerationException {
      for (B binding = bindingsRequiringGeneration.poll();
          binding != null;
          binding = bindingsRequiringGeneration.poll()) {
        checkState(!binding.unresolved().isPresent());
        XType type = binding.key().type().xprocessing();
        if (!isDeclared(type)
            || injectValidatorWhenGeneratingCode.validate(type.getTypeElement()).isClean()) {
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

      @SuppressWarnings("unchecked")
      B maybeUnresolved =
          binding.unresolved().isPresent() ? (B) binding.unresolved().get() : binding;
      tryToGenerateBinding(maybeUnresolved, warnIfNotAlreadyGenerated);
    }

    /**
     * Tries to generate a binding, not generating if it already is generated. For resolved
     * bindings, this will try to generate the unresolved version of the binding.
     */
    void tryToGenerateBinding(B binding, boolean warnIfNotAlreadyGenerated) {
      if (shouldGenerateBinding(binding)) {
        bindingsRequiringGeneration.offer(binding);
        if (compilerOptions.warnIfInjectionFactoryNotGeneratedUpstream()
            && warnIfNotAlreadyGenerated) {
          messager.printMessage(
              Kind.NOTE,
              String.format(
                  "Generating a %s for %s. "
                      + "Prefer to run the dagger processor over that class instead.",
                  factoryClass.simpleName(),
                  // erasure to strip <T> from msgs.
                  erasedTypeName(binding.key().type().xprocessing())));
        }
      }
    }

    /** Returns true if the binding needs to be generated. */
    private boolean shouldGenerateBinding(B binding) {
      return !binding.unresolved().isPresent()
          && !materializedBindingKeys.contains(binding.key())
          && !bindingsRequiringGeneration.contains(binding)
          && processingEnv.findTypeElement(generatedClassNameForBinding(binding)) == null;
    }

    /** Caches the binding for future lookups by key. */
    private void tryToCacheBinding(B binding) {
      // We only cache resolved bindings or unresolved bindings w/o type arguments.
      // Unresolved bindings w/ type arguments aren't valid for the object graph.
      if (binding.unresolved().isPresent()
          || binding.bindingTypeElement().get().getType().getTypeArguments().isEmpty()) {
        Key key = binding.key();
        Binding previousValue = bindingsByKey.put(key, binding);
        checkState(previousValue == null || binding.equals(previousValue),
            "couldn't register %s. %s was already registered for %s",
            binding, previousValue, key);
      }
    }
  }

  private final BindingsCollection<ProvisionBinding> provisionBindings =
      new BindingsCollection<>(TypeNames.PROVIDER);
  private final BindingsCollection<MembersInjectionBinding> membersInjectionBindings =
      new BindingsCollection<>(TypeNames.MEMBERS_INJECTOR);

  @Inject
  InjectBindingRegistryImpl(
      XProcessingEnv processingEnv,
      XMessager messager,
      InjectValidator injectValidator,
      KeyFactory keyFactory,
      BindingFactory bindingFactory,
      CompilerOptions compilerOptions) {
    this.processingEnv = processingEnv;
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
    if (warnIfNotAlreadyGenerated) {
      boolean hasInjectConstructor =
          !(injectedConstructors(binding.membersInjectedType()).isEmpty()
              && assistedInjectedConstructors(binding.membersInjectedType()).isEmpty());
      warnIfNotAlreadyGenerated =
          hasInjectConstructor
              ? !binding.injectionSites().isEmpty()
              : binding.hasLocalInjectionSites();
    }

    membersInjectionBindings.tryRegisterBinding(binding, warnIfNotAlreadyGenerated);
  }

  @Override
  public Optional<ProvisionBinding> tryRegisterInjectConstructor(
      XConstructorElement constructorElement) {
    return tryRegisterConstructor(constructorElement, Optional.empty(), false);
  }

  @CanIgnoreReturnValue
  private Optional<ProvisionBinding> tryRegisterConstructor(
      XConstructorElement constructorElement,
      Optional<XType> resolvedType,
      boolean warnIfNotAlreadyGenerated) {
    XTypeElement typeElement = constructorElement.getEnclosingElement();

    // Validating here shouldn't have a performance penalty because the validator caches its reports
    ValidationReport report = injectValidator.validate(typeElement);
    report.printMessagesTo(messager);
    if (!report.isClean()) {
      return Optional.empty();
    }

    XType type = typeElement.getType();
    Key key = keyFactory.forInjectConstructorWithResolvedType(type);
    ProvisionBinding cachedBinding = provisionBindings.getBinding(key);
    if (cachedBinding != null) {
      return Optional.of(cachedBinding);
    }

    ProvisionBinding binding = bindingFactory.injectionBinding(constructorElement, resolvedType);
    registerBinding(binding, warnIfNotAlreadyGenerated);
    if (!binding.injectionSites().isEmpty()) {
      tryRegisterMembersInjectedType(typeElement, resolvedType, warnIfNotAlreadyGenerated);
    }
    return Optional.of(binding);
  }

  @Override
  public Optional<MembersInjectionBinding> tryRegisterInjectField(XFieldElement fieldElement) {
    // TODO(b/204116636): Add a test for this once we're able to test kotlin sources.
    // TODO(b/204208307): Add validation for KAPT to test if this came from a top-level field.
    if (!isTypeElement(fieldElement.getEnclosingElement())) {
      messager.printMessage(
          Kind.ERROR,
          "@Inject fields must be enclosed in a type.",
          fieldElement);
    }
    return tryRegisterMembersInjectedType(
        asTypeElement(fieldElement.getEnclosingElement()), Optional.empty(), false);
  }

  @Override
  public Optional<MembersInjectionBinding> tryRegisterInjectMethod(XMethodElement methodElement) {
    // TODO(b/204116636): Add a test for this once we're able to test kotlin sources.
    // TODO(b/204208307): Add validation for KAPT to test if this came from a top-level method.
    if (!isTypeElement(methodElement.getEnclosingElement())) {
      messager.printMessage(
          Kind.ERROR,
          "@Inject methods must be enclosed in a type.",
          methodElement);
    }
    return tryRegisterMembersInjectedType(
        asTypeElement(methodElement.getEnclosingElement()), Optional.empty(), false);
  }

  @CanIgnoreReturnValue
  private Optional<MembersInjectionBinding> tryRegisterMembersInjectedType(
      XTypeElement typeElement, Optional<XType> resolvedType, boolean warnIfNotAlreadyGenerated) {
    // Validating here shouldn't have a performance penalty because the validator caches its reports
    ValidationReport report = injectValidator.validateForMembersInjection(typeElement);
    report.printMessagesTo(messager);
    if (!report.isClean()) {
      return Optional.empty();
    }

    XType type = typeElement.getType();
    Key key = keyFactory.forInjectConstructorWithResolvedType(type);
    MembersInjectionBinding cachedBinding = membersInjectionBindings.getBinding(key);
    if (cachedBinding != null) {
      return Optional.of(cachedBinding);
    }

    MembersInjectionBinding binding = bindingFactory.membersInjectionBinding(type, resolvedType);
    registerBinding(binding, warnIfNotAlreadyGenerated);
    for (Optional<XType> supertype = nonObjectSuperclass(type);
        supertype.isPresent();
        supertype = nonObjectSuperclass(supertype.get())) {
      getOrFindMembersInjectionBinding(keyFactory.forMembersInjectedType(supertype.get()));
    }
    return Optional.of(binding);
  }

  @CanIgnoreReturnValue
  @Override
  public Optional<ProvisionBinding> getOrFindProvisionBinding(Key key) {
    checkNotNull(key);
    if (!isValidImplicitProvisionKey(key)) {
      return Optional.empty();
    }
    ProvisionBinding binding = provisionBindings.getBinding(key);
    if (binding != null) {
      return Optional.of(binding);
    }

    XType type = key.type().xprocessing();
    XTypeElement element = type.getTypeElement();

    ValidationReport report = injectValidator.validate(element);
    report.printMessagesTo(messager);
    if (!report.isClean()) {
      return Optional.empty();
    }

    return Stream.concat(
            injectedConstructors(element).stream(),
            assistedInjectedConstructors(element).stream())
        // We're guaranteed that there's at most 1 @Inject constructors from above validation.
        .collect(toOptional())
        .flatMap(constructor -> tryRegisterConstructor(constructor, Optional.of(type), true));
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
    return tryRegisterMembersInjectedType(
        key.type().xprocessing().getTypeElement(), Optional.of(key.type().xprocessing()), true);
  }

  @Override
  public Optional<ProvisionBinding> getOrFindMembersInjectorProvisionBinding(Key key) {
    if (!isValidMembersInjectionKey(key)) {
      return Optional.empty();
    }
    Key membersInjectionKey =
        keyFactory.forMembersInjectedType(unwrapType(key.type().xprocessing()));
    return getOrFindMembersInjectionBinding(membersInjectionKey)
        .map(binding -> bindingFactory.membersInjectorBinding(key, binding));
  }
}

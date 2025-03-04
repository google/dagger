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
import static androidx.room.compiler.processing.compat.XConverters.toKS;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.base.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.base.Keys.isValidMembersInjectionKey;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectedConstructors;
import static dagger.internal.codegen.binding.InjectionAnnotations.hasInjectAnnotation;
import static dagger.internal.codegen.binding.InjectionAnnotations.injectedConstructors;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.xprocessing.XTypes.erasedTypeName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.nonObjectSuperclass;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.devtools.ksp.symbol.Origin;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.Component;
import dagger.Provides;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.binding.InjectionBinding;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.MembersInjectorBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.xprocessing.XTypeNames;
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
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;
  private final CompilerOptions compilerOptions;

  private final class BindingsCollection<B extends Binding> {
    private final XClassName factoryClass;
    private final Map<Key, B> bindingsByKey = Maps.newLinkedHashMap();
    private final Deque<B> bindingsRequiringGeneration = new ArrayDeque<>();
    private final Set<Key> materializedBindingKeys = Sets.newLinkedHashSet();

    BindingsCollection(XClassName factoryClass) {
      this.factoryClass = factoryClass;
    }

    void generateBindings(SourceFileGenerator<B> generator) {
      for (B binding = bindingsRequiringGeneration.poll();
          binding != null;
          binding = bindingsRequiringGeneration.poll()) {
        checkState(!binding.unresolved().isPresent());
        XType type = binding.key().type().xprocessing();
        if (!isDeclared(type)
                || injectValidator.validateWhenGeneratingCode(type.getTypeElement()).isClean()) {
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
    void tryRegisterBinding(B binding, boolean isCalledFromInjectProcessor) {
      if (processingEnv.getBackend() == XProcessingEnv.Backend.KSP) {
        Origin origin =
            toKS(closestEnclosingTypeElement(binding.bindingElement().get())).getOrigin();
        // If the origin of the element is from a source file in the current compilation unit then
        // we're guaranteed that the InjectProcessor should run over the element so only generate
        // the Factory/MembersInjector if we're being called from the InjectProcessor.
        //
        // TODO(bcorso): generally, this isn't something we would need to keep track of manually.
        // However, KSP incremental processing has a bug that will overwrite the cache for the
        // element if we generate files for it, which can lead to missing generated files from
        // other processors. See https://github.com/google/dagger/issues/4063 and
        // https://github.com/google/dagger/issues/4054. Remove this once that bug is fixed.
        if (!isCalledFromInjectProcessor && (origin == Origin.JAVA || origin == Origin.KOTLIN)) {
          return;
        }
      }
      tryToCacheBinding(binding);

      @SuppressWarnings("unchecked")
      B maybeUnresolved =
          binding.unresolved().isPresent() ? (B) binding.unresolved().get() : binding;
      tryToGenerateBinding(maybeUnresolved, isCalledFromInjectProcessor);
    }

    /**
     * Tries to generate a binding, not generating if it already is generated. For resolved
     * bindings, this will try to generate the unresolved version of the binding.
     */
    void tryToGenerateBinding(B binding, boolean isCalledFromInjectProcessor) {
      if (shouldGenerateBinding(binding)) {
        bindingsRequiringGeneration.offer(binding);
        if (compilerOptions.warnIfInjectionFactoryNotGeneratedUpstream()
                && !isCalledFromInjectProcessor) {
          messager.printMessage(
              Kind.NOTE,
              String.format(
                  "Generating a %s for %s. "
                      + "Prefer to run the dagger processor over that class instead.",
                  factoryClass.getSimpleName(),
                  // erasure to strip <T> from msgs.
                  erasedTypeName(binding.key().type().xprocessing())));
        }
      }
    }

    /** Returns true if the binding needs to be generated. */
    private boolean shouldGenerateBinding(B binding) {
      if (binding instanceof MembersInjectionBinding) {
        MembersInjectionBinding membersInjectionBinding = (MembersInjectionBinding) binding;
        // Empty members injection bindings are special and don't need source files.
        if (membersInjectionBinding.injectionSites().isEmpty()) {
          return false;
        }
        // Members injectors for classes with no local injection sites and no @Inject
        // constructor are unused.
        boolean hasInjectConstructor =
            !(injectedConstructors(membersInjectionBinding.membersInjectedType()).isEmpty()
                && assistedInjectedConstructors(
                    membersInjectionBinding.membersInjectedType()).isEmpty());
        if (!membersInjectionBinding.hasLocalInjectionSites() && !hasInjectConstructor) {
          return false;
        }
      }
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

  private final BindingsCollection<ContributionBinding> injectionBindings =
      new BindingsCollection<>(XTypeNames.PROVIDER);
  private final BindingsCollection<MembersInjectionBinding> membersInjectionBindings =
      new BindingsCollection<>(XTypeNames.MEMBERS_INJECTOR);

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
    this.keyFactory = keyFactory;
    this.bindingFactory = bindingFactory;
    this.compilerOptions = compilerOptions;
  }

  // TODO(dpb): make the SourceFileGenerators fields so they don't have to be passed in
  @Override
  public void generateSourcesForRequiredBindings(
      SourceFileGenerator<ContributionBinding> factoryGenerator,
      SourceFileGenerator<MembersInjectionBinding> membersInjectorGenerator) {
    injectionBindings.generateBindings(factoryGenerator);
    membersInjectionBindings.generateBindings(membersInjectorGenerator);
  }

  @Override
  public Optional<ContributionBinding> tryRegisterInjectConstructor(
      XConstructorElement constructorElement) {
    return tryRegisterConstructor(
        constructorElement,
        Optional.empty(),
        /* isCalledFromInjectProcessor= */ true);
  }

  @CanIgnoreReturnValue
  private Optional<ContributionBinding> tryRegisterConstructor(
      XConstructorElement constructorElement,
      Optional<XType> resolvedType,
      boolean isCalledFromInjectProcessor) {
    XTypeElement typeElement = constructorElement.getEnclosingElement();

    // Validating here shouldn't have a performance penalty because the validator caches its reports
    ValidationReport report = injectValidator.validate(typeElement);
    report.printMessagesTo(messager);
    if (!report.isClean()) {
      return Optional.empty();
    }

    XType type = typeElement.getType();
    Key key = keyFactory.forInjectConstructorWithResolvedType(type);
    ContributionBinding cachedBinding = injectionBindings.getBinding(key);
    if (cachedBinding != null) {
      return Optional.of(cachedBinding);
    }

    if (hasInjectAnnotation(constructorElement)) {
      InjectionBinding binding = bindingFactory.injectionBinding(constructorElement, resolvedType);
      injectionBindings.tryRegisterBinding(binding, isCalledFromInjectProcessor);
      if (!binding.injectionSites().isEmpty()) {
        tryRegisterMembersInjectedType(typeElement, resolvedType, isCalledFromInjectProcessor);
      }
      return Optional.of(binding);
    } else if (constructorElement.hasAnnotation(XTypeNames.ASSISTED_INJECT)) {
      AssistedInjectionBinding binding =
          bindingFactory.assistedInjectionBinding(constructorElement, resolvedType);
      injectionBindings.tryRegisterBinding(binding, isCalledFromInjectProcessor);
      if (!binding.injectionSites().isEmpty()) {
        tryRegisterMembersInjectedType(typeElement, resolvedType, isCalledFromInjectProcessor);
      }
      return Optional.of(binding);
    }
    throw new AssertionError(
        "Expected either an @Inject or @AssistedInject annotated constructor: "
            + constructorElement.getEnclosingElement().getQualifiedName());
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
        asTypeElement(fieldElement.getEnclosingElement()),
        Optional.empty(),
        /* isCalledFromInjectProcessor= */ true);
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
        asTypeElement(methodElement.getEnclosingElement()),
        Optional.empty(),
        /* isCalledFromInjectProcessor= */ true);
  }

  @CanIgnoreReturnValue
  private Optional<MembersInjectionBinding> tryRegisterMembersInjectedType(
      XTypeElement typeElement,
      Optional<XType> resolvedType,
      boolean isCalledFromInjectProcessor) {
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
    membersInjectionBindings.tryRegisterBinding(binding, isCalledFromInjectProcessor);
    for (Optional<XType> supertype = nonObjectSuperclass(type);
        supertype.isPresent();
        supertype = nonObjectSuperclass(supertype.get())) {
      getOrFindMembersInjectionBinding(keyFactory.forMembersInjectedType(supertype.get()));
    }
    return Optional.of(binding);
  }

  @CanIgnoreReturnValue
  @Override
  public Optional<ContributionBinding> getOrFindInjectionBinding(Key key) {
    checkNotNull(key);
    if (!isValidImplicitProvisionKey(key)) {
      return Optional.empty();
    }
    ContributionBinding binding = injectionBindings.getBinding(key);
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
        .flatMap(
            constructor ->
                tryRegisterConstructor(
                    constructor,
                    Optional.of(type),
                    /* isCalledFromInjectProcessor= */ false));
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
        key.type().xprocessing().getTypeElement(),
        Optional.of(key.type().xprocessing()),
        /* isCalledFromInjectProcessor= */ false);
  }

  @Override
  public Optional<MembersInjectorBinding> getOrFindMembersInjectorBinding(Key key) {
    if (!isValidMembersInjectionKey(key)) {
      return Optional.empty();
    }
    Key membersInjectionKey =
        keyFactory.forMembersInjectedType(unwrapType(key.type().xprocessing()));
    return getOrFindMembersInjectionBinding(membersInjectionKey)
        .map(binding -> bindingFactory.membersInjectorBinding(key, binding));
  }
}

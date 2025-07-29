/*
 * Copyright (C) 2024 The Dagger Authors.
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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static androidx.room.compiler.codegen.compat.XConverters.toXPoet;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.SourceFiles.generatedMonitoringModuleName;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.WildcardTypeName;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.FrameworkTypes;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Key.MultibindingContributionIdentifier;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;
import javax.inject.Inject;

/** Stores the bindings and declarations of a component by key. */
final class ComponentDeclarations {
  private final KeyFactory keyFactory;
  private final ImmutableSetMultimap<Key, ContributionBinding> bindings;
  private final ImmutableSetMultimap<Key, DelegateDeclaration> delegates;
  private final ImmutableSetMultimap<Key, OptionalBindingDeclaration> optionalBindings;
  private final ImmutableSetMultimap<Key, SubcomponentDeclaration> subcomponents;
  private final ImmutableSetMultimap<TypeNameKey, MultibindingDeclaration> multibindings;
  private final ImmutableSetMultimap<TypeNameKey, ContributionBinding> multibindingContributions;
  private final ImmutableSetMultimap<TypeNameKey, DelegateDeclaration>
      delegateMultibindingContributions;

  private ComponentDeclarations(
      KeyFactory keyFactory,
      ImmutableSetMultimap<Key, ContributionBinding> bindings,
      ImmutableSetMultimap<Key, DelegateDeclaration> delegates,
      ImmutableSetMultimap<Key, OptionalBindingDeclaration> optionalBindings,
      ImmutableSetMultimap<Key, SubcomponentDeclaration> subcomponents,
      ImmutableSetMultimap<TypeNameKey, MultibindingDeclaration> multibindings,
      ImmutableSetMultimap<TypeNameKey, ContributionBinding> multibindingContributions,
      ImmutableSetMultimap<TypeNameKey, DelegateDeclaration> delegateMultibindingContributions) {
    this.keyFactory = keyFactory;
    this.bindings = bindings;
    this.delegates = delegates;
    this.optionalBindings = optionalBindings;
    this.subcomponents = subcomponents;
    this.multibindings = multibindings;
    this.multibindingContributions = multibindingContributions;
    this.delegateMultibindingContributions = delegateMultibindingContributions;
  }

  ImmutableSet<ContributionBinding> bindings(Key key) {
    return bindings.get(key);
  }

  ImmutableSet<DelegateDeclaration> delegates(Key key) {
    // @Binds @IntoMap declarations have key Map<K, V> but may be requested as
    // Map<K, Provider/Producer<V>> keys, so unwrap the multibinding map contribution key first.
    // TODO(b/366277730): This can be simplified to "delegates.get(key)" once the flag for
    // "useFrameworkTypeInMapMultibindingContributionKey" is removed.
    return delegates.get(
        key.multibindingContributionIdentifier().isPresent()
            // TODO(bcorso): Consider using TypeNameKey here instead of Key, to avoid losing
            // variance information when unwrapping KSP types (see TypeNameKey's javadoc).
            ? keyFactory.unwrapMapValueType(key)
            : key);
  }

  /**
   * Returns the delegate multibinding contributions (e.g. {@code @Binds @IntoMap}) for the given
   * {@code key}, or an empty set if none exist.
   *
   * <p>For map multibindings, the following request keys represent the same underlying binding and
   * will return the same results:
   * <ul>
   *   <li> {@code Map<K, V>}
   *   <li> {@code Map<K, Provider<V>>}
   *   <li> {@code Map<K, Producer<V>>}
   *   <li> {@code Map<K, Produced<V>>}
   * </ul>
   *
   * <p>For set multibindings, the following request keys represent the same underlying binding and
   * will return the same results:
   * <ul>
   *   <li> {@code Set<V>}
   *   <li> {@code Set<Produced<V>>}
   * </ul>
   */
  ImmutableSet<DelegateDeclaration> delegateMultibindingContributions(Key key) {
    return delegateMultibindingContributions.get(unwrapMultibindingKey(key));
  }

  /**
   * Returns the multibinding declarations (i.e. {@code @Multibinds}) for the given {@code key}, or
   * an empty set if none exists.
   *
   * <p>For map multibindings, the following request keys represent the same underlying binding and
   * will return the same results:
   * <ul>
   *   <li> {@code Map<K, V>}
   *   <li> {@code Map<K, Provider<V>>}
   *   <li> {@code Map<K, Producer<V>>}
   *   <li> {@code Map<K, Produced<V>>}
   * </ul>
   *
   * <p>For set multibindings, the following request keys represent the same underlying binding and
   * will return the same results:
   * <ul>
   *   <li> {@code Set<V>}
   *   <li> {@code Set<Produced<V>>}
   * </ul>
   */
  ImmutableSet<MultibindingDeclaration> multibindings(Key key) {
    return multibindings.get(unwrapMultibindingKey(key));
  }

  /**
   * Returns the multibinding contributions (e.g. {@code @Provides @IntoMap}) for the given
   * {@code key}, or an empty set if none exists.
   *
   * <p>For map multibindings, the following request keys represent the same underlying binding and
   * will return the same results:
   * <ul>
   *   <li> {@code Map<K, V>}
   *   <li> {@code Map<K, Provider<V>>}
   *   <li> {@code Map<K, Producer<V>>}
   *   <li> {@code Map<K, Produced<V>>}
   * </ul>
   *
   * <p>For set multibindings, the following request keys represent the same underlying binding and
   * will return the same results:
   * <ul>
   *   <li> {@code Set<V>}
   *   <li> {@code Set<Produced<V>>}
   * </ul>
   */
  ImmutableSet<ContributionBinding> multibindingContributions(Key key) {
    return multibindingContributions.get(unwrapMultibindingKey(key));
  }

  ImmutableSet<OptionalBindingDeclaration> optionalBindings(Key key) {
    return optionalBindings.get(key);
  }

  ImmutableSet<SubcomponentDeclaration> subcomponents(Key key) {
    return subcomponents.get(key);
  }

  ImmutableSet<Declaration> allDeclarations() {
    return ImmutableSet.<Declaration>builder()
        .addAll(bindings.values())
        .addAll(delegates.values())
        .addAll(multibindings.values())
        .addAll(optionalBindings.values())
        .addAll(subcomponents.values())
        .build();
  }

  static final class Factory {
    private final XProcessingEnv processingEnv;
    private final KeyFactory keyFactory;
    private final ModuleDescriptor.Factory moduleDescriptorFactory;

    @Inject
    Factory(
        XProcessingEnv processingEnv,
        KeyFactory keyFactory,
        ModuleDescriptor.Factory moduleDescriptorFactory) {
      this.processingEnv = processingEnv;
      this.keyFactory = keyFactory;
      this.moduleDescriptorFactory = moduleDescriptorFactory;
    }

    ComponentDeclarations create(
        Optional<ComponentDescriptor> parentDescriptor, ComponentDescriptor descriptor) {
      ImmutableSet.Builder<ContributionBinding> bindings = ImmutableSet.builder();
      ImmutableSet.Builder<DelegateDeclaration> delegates = ImmutableSet.builder();
      ImmutableSet.Builder<MultibindingDeclaration> multibindings = ImmutableSet.builder();
      ImmutableSet.Builder<OptionalBindingDeclaration> optionalBindings =ImmutableSet.builder();
      ImmutableSet.Builder<SubcomponentDeclaration> subcomponents = ImmutableSet.builder();

      bindings.addAll(descriptor.bindings());
      delegates.addAll(descriptor.delegateDeclarations());
      multibindings.addAll(descriptor.multibindingDeclarations());
      optionalBindings.addAll(descriptor.optionalBindingDeclarations());
      subcomponents.addAll(descriptor.subcomponentDeclarations());

      // Note: The implicit production modules are not included directly in the component descriptor
      // because we don't know whether to install them or not without knowing the parent component.
      for (ModuleDescriptor module : implicitProductionModules(descriptor, parentDescriptor)) {
        bindings.addAll(module.bindings());
        delegates.addAll(module.delegateDeclarations());
        multibindings.addAll(module.multibindingDeclarations());
        optionalBindings.addAll(module.optionalDeclarations());
        subcomponents.addAll(module.subcomponentDeclarations());
      }

      return new ComponentDeclarations(
          keyFactory,
          indexDeclarationsByKey(bindings.build()),
          indexDeclarationsByKey(delegates.build()),
          indexDeclarationsByKey(optionalBindings.build()),
          indexDeclarationsByKey(subcomponents.build()),
          // The @Multibinds declarations and @IntoSet/@IntoMap multibinding contributions are all
          // indexed by their "unwrapped" multibinding key (i.e. Map<K, V> or Set<V>) so that we
          // don't have to check multiple different keys to gather all of the contributions.
          indexDeclarationsByUnwrappedMultibindingKey(multibindings.build()),
          indexDeclarationsByUnwrappedMultibindingKey(multibindingContributions(bindings.build())),
          indexDeclarationsByUnwrappedMultibindingKey(
              multibindingContributions(delegates.build())));
    }

    /**
     * Returns all the modules that should be installed in the component. For production components
     * and production subcomponents that have a parent that is not a production component or
     * subcomponent, also includes the production monitoring module for the component and the
     * production executor module.
     */
    private ImmutableSet<ModuleDescriptor> implicitProductionModules(
        ComponentDescriptor descriptor, Optional<ComponentDescriptor> parentDescriptor) {
      return shouldIncludeImplicitProductionModules(descriptor, parentDescriptor)
          ? ImmutableSet.of(
              moduleDescriptorFactory.create(
                  DaggerSuperficialValidation.requireTypeElement(
                      processingEnv, generatedMonitoringModuleName(descriptor.typeElement()))),
              moduleDescriptorFactory.create(
                  processingEnv.requireTypeElement(XTypeNames.PRODUCTION_EXECTUTOR_MODULE)))
          : ImmutableSet.of();
    }

    private static boolean shouldIncludeImplicitProductionModules(
        ComponentDescriptor descriptor, Optional<ComponentDescriptor> parentDescriptor) {
      return descriptor.isProduction()
          && descriptor.isRealComponent()
          && (parentDescriptor.isEmpty() || !parentDescriptor.get().isProduction());
    }

    /** Indexes {@code bindingDeclarations} by {@link Declaration#key()}. */
    private static <T extends Declaration>
        ImmutableSetMultimap<Key, T> indexDeclarationsByKey(Iterable<T> declarations) {
      return ImmutableSetMultimap.copyOf(Multimaps.index(declarations, Declaration::key));
    }

    /** Indexes {@code bindingDeclarations} by the unwrapped multibinding key. */
    private <T extends Declaration> ImmutableSetMultimap<TypeNameKey, T>
        indexDeclarationsByUnwrappedMultibindingKey(Iterable<T> declarations) {
      return ImmutableSetMultimap.copyOf(
          Multimaps.index(
              declarations,
              declaration ->
                  unwrapMultibindingKey(
                      declaration.key().withoutMultibindingContributionIdentifier())));
    }

    private static <T extends Declaration> ImmutableSet<T> multibindingContributions(
        ImmutableSet<T> declarations) {
      return declarations.stream()
          .filter(declaration -> declaration.key().multibindingContributionIdentifier().isPresent())
          .collect(toImmutableSet());
    }
  }

  /**
   * Returns a {@link TypeNameKey} with the same qualifiers and multibinding identifier as the
   * original key, but with an unwrapped typed.
   *
   * <p>In this case, an unwrapped type is a map or set where the value type has been stripped of a
   * leading framework type. If the given type is neither a map nor set type, then the original type
   * is returned.
   *
   * <p>The following map types have an unwrapped type equal to {@code Map<K, V>}:
   * <ul>
   *   <li> {@code Map<K, V>}
   *   <li> {@code Map<K, Provider<V>>}
   *   <li> {@code Map<K, Producer<V>>}
   *   <li> {@code Map<K, Produced<V>>}
   * </ul>
   *
   * <p>The following set types have an unwrapped type equal to {@code Set<V>}:
   * <ul>
   *   <li> {@code Set<V>}
   *   <li> {@code Set<Produced<V>>}
   * </ul>
   */
  private static TypeNameKey unwrapMultibindingKey(Key multibindingKey) {
    return TypeNameKey.from(
        multibindingKey.multibindingContributionIdentifier(),
        multibindingKey.qualifier(),
        unwrapMultibindingTypeName(multibindingKey.type().xprocessing().getTypeName()));
  }

  private static TypeName unwrapMultibindingTypeName(TypeName typeName) {
    if (isValidMapMultibindingTypeName(typeName)) {
      ParameterizedTypeName mapTypeName = (ParameterizedTypeName) typeName;
      TypeName mapKeyTypeName = mapTypeName.typeArguments.get(0);
      TypeName mapValueTypeName = mapTypeName.typeArguments.get(1);
      return ParameterizedTypeName.get(
            mapTypeName.rawType,
            mapKeyTypeName,
            unwrapFrameworkTypeName(mapValueTypeName, FrameworkTypes.MAP_VALUE_FRAMEWORK_TYPES));
    }
    if (isValidSetMultibindingTypeName(typeName)) {
      ParameterizedTypeName setTypeName = (ParameterizedTypeName) typeName;
      TypeName setValueTypeName = getOnlyElement(setTypeName.typeArguments);
      return ParameterizedTypeName.get(
          setTypeName.rawType,
          unwrapFrameworkTypeName(setValueTypeName, FrameworkTypes.SET_VALUE_FRAMEWORK_TYPES));
    }
    return typeName;
  }

  private static boolean isValidMapMultibindingTypeName(TypeName typeName) {
    if (!(typeName instanceof ParameterizedTypeName)) {
      return false;
    }
    ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
    return parameterizedTypeName.rawType.equals(toJavaPoet(XTypeName.MAP))
        && parameterizedTypeName.typeArguments.size() == 2
        && !(parameterizedTypeName.typeArguments.get(0) instanceof WildcardTypeName)
        && !(parameterizedTypeName.typeArguments.get(1) instanceof WildcardTypeName);
  }

  private static boolean isValidSetMultibindingTypeName(TypeName typeName) {
    if (!(typeName instanceof ParameterizedTypeName)) {
      return false;
    }
    ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
    return parameterizedTypeName.rawType.equals(toJavaPoet(XTypeName.SET))
        && parameterizedTypeName.typeArguments.size() == 1
        && !(getOnlyElement(parameterizedTypeName.typeArguments) instanceof WildcardTypeName);
  }

  private static TypeName unwrapFrameworkTypeName(
      TypeName typeName, ImmutableSet<XClassName> frameworkTypeNames) {
    if (typeName instanceof ParameterizedTypeName) {
      ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
      if (frameworkTypeNames.contains(toXPoet(parameterizedTypeName.rawType))) {
        typeName = getOnlyElement(parameterizedTypeName.typeArguments);
      }
    }
    return typeName;
  }

  /**
   * Represents a class similar to {@link Key} but uses {@link TypeName} rather than {@code XType}.
   *
   * <p>We use {@code TypeName} rather than {@code XType} here because we can lose variance
   * information when unwrapping an {@code XType} in KSP (b/352142595), and using {@code TypeName}
   * avoids this issue.
   */
  @AutoValue
  abstract static class TypeNameKey {
    static TypeNameKey from(
        Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier,
        Optional<DaggerAnnotation> qualifier,
        TypeName typeName) {
      return new AutoValue_ComponentDeclarations_TypeNameKey(
          multibindingContributionIdentifier, qualifier, typeName);
    }

    abstract Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier();

    abstract Optional<DaggerAnnotation> qualifier();

    abstract TypeName type();
  }
}

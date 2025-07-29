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

package dagger.internal.codegen.binding;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ProducerAnnotations.productionImplementationQualifier;
import static dagger.internal.codegen.base.ProducerAnnotations.productionQualifier;
import static dagger.internal.codegen.base.RequestKinds.extractKeyType;
import static dagger.internal.codegen.binding.MapKeys.getMapKey;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XTypeNames.isFutureType;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.FrameworkTypes;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.OptionalType;
import dagger.internal.codegen.base.RequestKinds;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.DaggerExecutableElement;
import dagger.internal.codegen.model.DaggerType;
import dagger.internal.codegen.model.DaggerTypeElement;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.multibindings.Multibinds;
import java.util.Optional;
import javax.inject.Inject;

/** A factory for {@link Key}s. */
public final class KeyFactory {
  private final XProcessingEnv processingEnv;
  private final CompilerOptions compilerOptions;
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  KeyFactory(
      XProcessingEnv processingEnv,
      CompilerOptions compilerOptions,
      InjectionAnnotations injectionAnnotations) {
    this.processingEnv = processingEnv;
    this.compilerOptions = compilerOptions;
    this.injectionAnnotations = injectionAnnotations;
  }

  private XType setOf(XType elementType) {
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(XTypeName.SET), elementType.boxed());
  }

  private XType mapOf(XType keyType, XType valueType) {
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(XTypeName.MAP), keyType.boxed(), valueType.boxed());
  }

  /**
   * If {@code key}'s type is {@code Optional<T>} for some {@code T}, returns a key with the same
   * qualifier whose type is {@linkplain RequestKinds#extractKeyType(RequestKind, XType)}
   * extracted} from {@code T}.
   */
  Key optionalOf(Key key) {
    return key.withType(DaggerType.from(optionalOf(key.type().xprocessing())));
  }

  private XType optionalOf(XType type) {
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(XTypeNames.JDK_OPTIONAL), type.boxed());
  }

  /** Returns {@code Map<KeyType, FrameworkType<ValueType>>}. */
  private XType mapOfFrameworkType(XType keyType, XClassName frameworkClassName, XType valueType) {
    checkArgument(FrameworkTypes.MAP_VALUE_FRAMEWORK_TYPES.contains(frameworkClassName));
    return mapOf(
        keyType,
        processingEnv.getDeclaredType(
            processingEnv.requireTypeElement(frameworkClassName), valueType.boxed()));
  }

  Key forComponentMethod(XMethodElement componentMethod) {
    return forMethod(componentMethod, componentMethod.getReturnType());
  }

  Key forProductionComponentMethod(XMethodElement componentMethod) {
    XType returnType = componentMethod.getReturnType();
    XType keyType =
        isFutureType(returnType) ? getOnlyElement(returnType.getTypeArguments()) : returnType;
    return forMethod(componentMethod, keyType);
  }

  Key forSubcomponentCreatorMethod(
      XMethodElement subcomponentCreatorMethod, XType declaredContainer) {
    checkArgument(isDeclared(declaredContainer));
    XMethodType resolvedMethod = subcomponentCreatorMethod.asMemberOf(declaredContainer);
    return forType(resolvedMethod.getReturnType());
  }

  public Key forSubcomponentCreator(XType creatorType) {
    return forType(creatorType);
  }

  public Key forProvidesMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(XTypeNames.PROVIDES));
    return forBindingMethod(method, contributingModule, Optional.of(XTypeNames.JAVAX_PROVIDER));
  }

  public Key forProducesMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(XTypeNames.PRODUCES));
    return forBindingMethod(method, contributingModule, Optional.of(XTypeNames.PRODUCER));
  }

  /** Returns the key bound by a {@link Binds} method. */
  Key forBindsMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(XTypeNames.BINDS));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  /** Returns the base key bound by a {@link BindsOptionalOf} method. */
  Key forBindsOptionalOfMethod(XMethodElement method, XTypeElement contributingModule) {
    checkArgument(method.hasAnnotation(XTypeNames.BINDS_OPTIONAL_OF));
    return forBindingMethod(method, contributingModule, Optional.empty());
  }

  private Key forBindingMethod(
      XMethodElement method,
      XTypeElement contributingModule,
      Optional<XClassName> frameworkClassName) {
    XMethodType methodType = method.asMemberOf(contributingModule.getType());
    ContributionType contributionType = ContributionType.fromBindingElement(method);
    XType returnType = methodType.getReturnType();
    if (frameworkClassName.isPresent() && frameworkClassName.get().equals(XTypeNames.PRODUCER)) {
      if (isFutureType(returnType)) {
        returnType = getOnlyElement(returnType.getTypeArguments());
      } else if (contributionType.equals(ContributionType.SET_VALUES)
          && SetType.isSet(returnType)) {
        SetType setType = SetType.from(returnType);
        if (isFutureType(setType.elementType())) {
          returnType = setOf(unwrapType(setType.elementType()));
        }
      }
    }
    XType keyType = bindingMethodKeyType(returnType, method, contributionType, frameworkClassName);
    Key key = forMethod(method, keyType);
    return contributionType.equals(ContributionType.UNIQUE)
        ? key
        : key.withMultibindingContributionIdentifier(
            DaggerTypeElement.from(contributingModule), DaggerExecutableElement.from(method));
  }

  /**
   * Returns the key for a {@link Multibinds @Multibinds} method.
   *
   * <p>The key's type is either {@code Set<T>} or {@code Map<K, Provider<V>>}. The latter works
   * even for maps used by {@code Producer}s.
   */
  Key forMultibindsMethod(XMethodElement method, XMethodType methodType) {
    XType returnType = method.getReturnType();
    XType keyType =
        MapType.isMap(returnType)
            ? mapOfFrameworkType(
                MapType.from(returnType).keyType(),
                XTypeNames.JAVAX_PROVIDER,
                MapType.from(returnType).valueType())
            : returnType;
    return forMethod(method, keyType);
  }

  private XType bindingMethodKeyType(
      XType returnType,
      XMethodElement method,
      ContributionType contributionType,
      Optional<XClassName> frameworkClassName) {
    switch (contributionType) {
      case UNIQUE:
        return returnType;
      case SET:
        return setOf(returnType);
      case MAP:
        Optional<XType> mapKeyType = getMapKey(method).map(MapKeys::mapKeyType);
        // TODO(bcorso): We've added a special checkState here since a number of people have run
        // into this particular case, but technically it shouldn't be necessary if we are properly
        // doing superficial validation and deferring on unresolvable types. We should revisit
        // whether this is necessary once we're able to properly defer this case.
        checkState(
            mapKeyType.isPresent(),
            "Missing map key annotation for method: %s#%s. That method was annotated with: %s. If a"
                + " map key annotation is included in that list, it means Dagger wasn't able to"
                + " detect that it was a map key because the dependency is missing from the"
                + " classpath of the current build. To fix, add a dependency for the map key to the"
                + " current build. For more details, see"
                + " https://github.com/google/dagger/issues/3133#issuecomment-1002790894.",
            method.getEnclosingElement(),
            method,
            method.getAllAnnotations().stream()
                .map(XAnnotations::toString)
                .collect(toImmutableList()));
        return (frameworkClassName.isPresent()
                && compilerOptions.useFrameworkTypeInMapMultibindingContributionKey())
            ? mapOfFrameworkType(mapKeyType.get(), frameworkClassName.get(), returnType)
            : mapOf(mapKeyType.get(), returnType);
      case SET_VALUES:
        // TODO(gak): do we want to allow people to use "covariant return" here?
        checkArgument(SetType.isSet(returnType), "%s is not a Set", returnType);
        return returnType;
    }
    throw new AssertionError();
  }

  /**
   * Returns the key for a binding associated with a {@link DelegateDeclaration}.
   *
   * <p>If {@code delegateDeclaration} is a multibinding map contribution and {@link
   * CompilerOptions#useFrameworkTypeInMapMultibindingContributionKey()} is enabled, then transforms
   * the {@code Map<K, V>} key into {@code Map<K, FrameworkType<V>>}, otherwise returns the
   * unaltered key.
   */
  Key forDelegateBinding(DelegateDeclaration delegateDeclaration, XClassName frameworkType) {
    return delegateDeclaration.contributionType().equals(ContributionType.MAP)
            && compilerOptions.useFrameworkTypeInMapMultibindingContributionKey()
        ? wrapMapValue(delegateDeclaration.key(), frameworkType)
        : delegateDeclaration.key();
  }

  private Key forMethod(XMethodElement method, XType keyType) {
    return forQualifiedType(injectionAnnotations.getQualifier(method), keyType);
  }

  public Key forInjectConstructorWithResolvedType(XType type) {
    return forType(type);
  }

  Key forType(XType type) {
    return Key.builder(DaggerType.from(type)).build();
  }

  public Key forMembersInjectedType(XType type) {
    return forType(type);
  }

  Key forQualifiedType(Optional<XAnnotation> qualifier, XType type) {
    return Key.builder(DaggerType.from(type.boxed()))
        .qualifier(qualifier.map(DaggerAnnotation::from))
        .build();
  }

  public Key forProductionExecutor() {
    return Key.builder(DaggerType.from(processingEnv.requireType(XTypeNames.EXECUTOR)))
        .qualifier(DaggerAnnotation.from(productionQualifier(processingEnv)))
        .build();
  }

  public Key forProductionImplementationExecutor() {
    return Key.builder(DaggerType.from(processingEnv.requireType(XTypeNames.EXECUTOR)))
        .qualifier(DaggerAnnotation.from(productionImplementationQualifier(processingEnv)))
        .build();
  }

  public Key forProductionComponentMonitor() {
    return forType(processingEnv.requireType(XTypeNames.PRODUCTION_COMPONENT_MONITOR));
  }

  /**
   * If {@code key}'s type is {@code Map<K, Provider<V>>}, {@code Map<K, Producer<V>>}, or {@code
   * Map<K, Produced<V>>}, returns a key with the same qualifier and {@link
   * Key#multibindingContributionIdentifier()} whose type is simply {@code Map<K, V>}.
   *
   * <p>Otherwise, returns {@code key}.
   */
  public Key unwrapMapValueType(Key key) {
    if (MapType.isMap(key)) {
      MapType mapType = MapType.from(key);
      if (!mapType.isRawType() && mapType.valuesAreFrameworkType()) {
        return key.withType(
            DaggerType.from(mapOf(mapType.keyType(), mapType.unwrappedFrameworkValueType())));
      }
    }
    return key;
  }

  /**
   * Returns a key with the type {@code Map<K, FrameworkType<V>>} if the given key has a type of
   * {@code Map<K, V>}. Otherwise, returns the unaltered key.
   *
   * @throws IllegalArgumentException if the {@code frameworkClassName} is not a valid framework
   *     type for multibinding maps.
   * @throws IllegalStateException if the {@code key} is already wrapped in a (different) framework
   *     type.
   */
  private Key wrapMapValue(Key key, XClassName frameworkClassName) {
    checkArgument(FrameworkTypes.MAP_VALUE_FRAMEWORK_TYPES.contains(frameworkClassName));
    if (MapType.isMap(key)) {
      MapType mapType = MapType.from(key);
      if (!mapType.isRawType() && !mapType.valuesAreTypeOf(frameworkClassName)) {
        checkState(!mapType.valuesAreFrameworkType());
        XTypeElement frameworkTypeElement = processingEnv.findTypeElement(frameworkClassName);
        if (frameworkTypeElement == null) {
          // This target might not be compiled with Producers, so wrappingClass might not have an
          // associated element.
          return key;
        }
        XType wrappedValueType =
            processingEnv.getDeclaredType(frameworkTypeElement, mapType.valueType());
        return key.withType(DaggerType.from(mapOf(mapType.keyType(), wrappedValueType)));
      }
    }
    return key;
  }

  /**
   * If {@code key}'s type is {@code Optional<T>} for some {@code T}, returns a key with the same
   * qualifier whose type is {@linkplain RequestKinds#extractKeyType(RequestKind, XType)}
   * extracted} from {@code T}.
   */
  Optional<Key> unwrapOptional(Key key) {
    if (!OptionalType.isOptional(key)) {
      return Optional.empty();
    }

    XType optionalValueType = OptionalType.from(key).valueType();
    return Optional.of(key.withType(DaggerType.from(extractKeyType(optionalValueType))));
  }
}

/*
 * Copyright (C) 2019 The Dagger Authors.
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.MoreAnnotationValues.asAnnotationValues;
import static dagger.internal.codegen.serialization.ProtoSerialization.fromAnnotationValue;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.ComponentDefinitionType;
import dagger.internal.ConfigureInitializationParameters;
import dagger.internal.ModifiableBinding;
import dagger.internal.ModifiableModule;
import dagger.internal.codegen.ComponentImplementation.ConfigureInitializationMethod;
import dagger.internal.codegen.serialization.BindingRequestProto;
import dagger.internal.codegen.serialization.ComponentRequirementProto;
import dagger.internal.codegen.serialization.FrameworkTypeWrapper;
import dagger.internal.codegen.serialization.KeyProto;
import dagger.model.Key;
import dagger.model.RequestKind;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Reconstructs {@link ComponentImplementation}s that have already been compiled. Uses metadata
 * annotations on the generated type and it's methods to reconstitute the equivalent {@link
 * ComponentImplementation} state.
 */
final class DeserializedComponentImplementationBuilder {
  private final CompilerOptions compilerOptions;
  private final ComponentCreatorImplementationFactory componentCreatorImplementationFactory;
  private final TypeProtoConverter typeProtoConverter;
  private final KeyFactory keyFactory;

  @Inject
  DeserializedComponentImplementationBuilder(
      CompilerOptions compilerOptions,
      ComponentCreatorImplementationFactory componentCreatorImplementationFactory,
      TypeProtoConverter typeProtoConverter,
      KeyFactory keyFactory) {
    this.compilerOptions = compilerOptions;
    this.componentCreatorImplementationFactory = componentCreatorImplementationFactory;
    this.typeProtoConverter = typeProtoConverter;
    this.keyFactory = keyFactory;
  }

  /** Creates a new {@link ComponentImplementation} from a compiled component. */
  ComponentImplementation create(ComponentDescriptor component, TypeElement generatedComponent) {
    Optional<ComponentImplementation> superclassImplementation =
        deserializedSuperclassImplementation(
            component, MoreTypes.asTypeElement(generatedComponent.getSuperclass()));

    ComponentImplementation componentImplementation =
        ComponentImplementation.forDeserializedComponent(
            component,
            ClassName.get(generatedComponent),
            generatedComponent.getNestingKind(),
            superclassImplementation,
            compilerOptions);

    componentImplementation.setCreatorImplementation(
        superclassImplementation.isPresent()
            ? Optional.empty()
            : componentCreatorImplementationFactory.create(
                componentImplementation, Optional.empty()));

    // TODO(b/117833324): Consider omitting superclass implementations, so that only one instance of
    // ComponentImplementation needs to be created (in most cases, we don't care about nested levels
    // of superclass implementations, except for the base implementation). If that's possible, use
    // getLocalAndInheritedMethods instead of getEnclosedElements() here.
    for (ExecutableElement method : methodsIn(generatedComponent.getEnclosedElements())) {
      getAnnotationMirror(method, ModifiableBinding.class)
          .asSet()
          .forEach(
              annotation ->
                  addModifiableBindingMethod(componentImplementation, method, annotation));

      getAnnotationMirror(method, ModifiableModule.class)
          .asSet()
          .forEach(
              annotation -> addModifiableModuleMethod(componentImplementation, method, annotation));

      getAnnotationMirror(method, ConfigureInitializationParameters.class)
          .asSet()
          .forEach(
              annotation ->
                  setConfigureInitializationMethod(componentImplementation, method, annotation));
    }

    for (TypeElement nestedType : typesIn(generatedComponent.getEnclosedElements())) {
      addChildImplementation(component, componentImplementation, nestedType);
    }

    return componentImplementation;
  }

  private Optional<ComponentImplementation> deserializedSuperclassImplementation(
      ComponentDescriptor component, TypeElement superclassElement) {
    return isAnnotationPresent(superclassElement, ComponentDefinitionType.class)
        ? Optional.of(create(component, superclassElement))
        : Optional.empty();
  }

  private void addModifiableBindingMethod(
      ComponentImplementation componentImplementation,
      ExecutableElement method,
      AnnotationMirror metadataAnnotation) {
    ModifiableBindingType modifiableBindingType =
        ModifiableBindingType.valueOf(
            getAnnotationValue(metadataAnnotation, "modifiableBindingType").getValue().toString());

    BindingRequest request =
        parseBindingRequest(getAnnotationValue(metadataAnnotation, "bindingRequest"));

    ImmutableList<Key> multibindingContributions =
        asAnnotationValues(getAnnotationValue(metadataAnnotation, "multibindingContributions"))
            .stream()
            .map(this::parseKey)
            .collect(toImmutableList());

    componentImplementation.addModifiableBindingMethod(
        modifiableBindingType,
        request,
        method.getReturnType(),
        methodDeclaration(method),
        method.getModifiers().contains(FINAL));
    componentImplementation.registerImplementedMultibindingKeys(request, multibindingContributions);
  }

  private BindingRequest fromProto(BindingRequestProto bindingRequest) {
    Key key = keyFactory.fromProto(bindingRequest.getKey());
    return bindingRequest.getFrameworkType().equals(FrameworkTypeWrapper.FrameworkType.UNKNOWN)
        ? bindingRequest(key, RequestKind.valueOf(bindingRequest.getRequestKind().name()))
        : bindingRequest(key, FrameworkType.valueOf(bindingRequest.getFrameworkType().name()));
  }

  /**
   * Returns a {@link MethodSpec} for a {@link
   * dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod}. The method contents
   * are not relevant since this represents a method that has already been compiled.
   *
   * <p>Ideally this could be {@code MethodSpec.overriding(method).build()}, but that doesn't work
   * for {@code final} methods
   */
  private MethodSpec methodDeclaration(ExecutableElement method) {
    return methodBuilder(method.getSimpleName().toString())
        .addModifiers(method.getModifiers())
        .returns(TypeName.get(method.getReturnType()))
        .build();
  }

  private void addModifiableModuleMethod(
      ComponentImplementation componentImplementation,
      ExecutableElement method,
      AnnotationMirror metadataAnnotation) {
    ComponentRequirement moduleRequirement =
        parseComponentRequirement(getAnnotationValue(metadataAnnotation, "value"));
    componentImplementation.registerModifiableModuleMethod(
        moduleRequirement, method.getSimpleName().toString());
  }

  private void setConfigureInitializationMethod(
      ComponentImplementation componentImplementation,
      ExecutableElement method,
      AnnotationMirror metadataAnnotation) {
    ImmutableSet<ComponentRequirement> parameters =
        asAnnotationValues(getAnnotationValue(metadataAnnotation, "value")).stream()
            .map(this::parseComponentRequirement)
            .collect(toImmutableSet());

    componentImplementation.setConfigureInitializationMethod(
        ConfigureInitializationMethod.create(MethodSpec.overriding(method).build(), parameters));
  }

  private void addChildImplementation(
      ComponentDescriptor component,
      ComponentImplementation componentImplementation,
      TypeElement nestedType) {
    getAnnotationMirror(nestedType, ComponentDefinitionType.class)
        .transform(annotation -> (TypeMirror) getAnnotationValue(annotation, "value").getValue())
        .transform(MoreTypes::asTypeElement)
        .asSet()
        .forEach(
            componentDefinitionType -> {
              ComponentDescriptor child =
                  component.childComponentsByElement().get(componentDefinitionType);
              componentImplementation.addChild(child, create(child, nestedType));
            });
  }

  private Key parseKey(AnnotationValue annotationValue) {
    return keyFactory.fromProto(
        fromAnnotationValue(annotationValue, KeyProto.getDefaultInstance()));
  }

  private BindingRequest parseBindingRequest(AnnotationValue annotationValue) {
    return fromProto(
        fromAnnotationValue(annotationValue, BindingRequestProto.getDefaultInstance()));
  }

  private ComponentRequirement parseComponentRequirement(AnnotationValue annotationValue) {
    return fromProto(
        fromAnnotationValue(annotationValue, ComponentRequirementProto.getDefaultInstance()));
  }

  private ComponentRequirement fromProto(ComponentRequirementProto proto) {
    switch (proto.getRequirementCase()) {
      case MODULE:
        return ComponentRequirement.forModule(typeProtoConverter.fromProto(proto.getModule()));
      case DEPENDENCY:
        return ComponentRequirement.forDependency(
            typeProtoConverter.fromProto(proto.getDependency()));
      case BOUND_INSTANCE:
        return ComponentRequirement.forBoundInstance(
            keyFactory.fromProto(proto.getBoundInstance().getKey()),
            proto.getBoundInstance().getNullable(),
            proto.getBoundInstance().getVariableName());
      case REQUIREMENT_NOT_SET:
        // fall through
    }
    throw new AssertionError(proto);
  }
}

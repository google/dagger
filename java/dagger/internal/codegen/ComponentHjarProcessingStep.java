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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static dagger.internal.codegen.ComponentGenerator.componentName;
import static dagger.internal.codegen.ComponentProcessingStep.getElementsFromAnnotations;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.BindsInstance;
import dagger.Component;
import dagger.internal.codegen.ComponentDescriptor.Factory;
import dagger.internal.codegen.ComponentValidator.ComponentValidationReport;
import dagger.producers.ProductionComponent;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A processing step that emits the API of a generated component, without any actual implementation.
 *
 * <p>When compiling a header jar (hjar), Bazel needs to run annotation processors that generate
 * API, like Dagger, to see what code they might output. Full {@link BindingGraph} analysis is
 * costly and unnecessary from the perspective of the header compiler; it's sole goal is to pass
 * along a slimmed down version of what will be the jar for a particular compilation, whether or not
 * that compilation succeeds. If it does not, the compilation pipeline will fail, even if header
 * compilation succeeded.
 *
 * <p>The components emitted by this processing step include all of the API elements exposed by the
 * normal {@link ComponentWriter}. Method bodies are omitted as Turbine ignores them entirely.
 */
final class ComponentHjarProcessingStep implements ProcessingStep {
  private final Elements elements;
  private final SourceVersion sourceVersion;
  private final Types types;
  private final Filer filer;
  private final Messager messager;
  private final ComponentValidator componentValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;

  @Inject
  ComponentHjarProcessingStep(
      Elements elements,
      SourceVersion sourceVersion,
      Types types,
      Filer filer,
      Messager messager,
      ComponentValidator componentValidator,
      Factory componentDescriptorFactory) {
    this.elements = elements;
    this.sourceVersion = sourceVersion;
    this.types = types;
    this.filer = filer;
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(Component.class, ProductionComponent.class);
  }

  @Override
  public ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> rejectedElements = ImmutableSet.builder();

    ImmutableSet<Element> componentElements =
        getElementsFromAnnotations(
            elementsByAnnotation, Component.class, ProductionComponent.class);

    for (Element element : componentElements) {
      TypeElement componentTypeElement = MoreElements.asType(element);
      try {
        // TODO(ronshapiro): component validation might not be necessary. We should measure it and
        // figure out if it's worth seeing if removing it will still work. We could potentially
        // add a new catch clause for any exception that's not TypeNotPresentException and ignore
        // the component entirely in that case.
        ComponentValidationReport validationReport =
            componentValidator.validate(componentTypeElement, ImmutableSet.of(), ImmutableSet.of());
        validationReport.report().printMessagesTo(messager);
        if (validationReport.report().isClean()) {
          new EmptyComponentGenerator(filer, elements, sourceVersion)
              .generate(componentDescriptorFactory.forComponent(componentTypeElement), messager);
        }
      } catch (TypeNotPresentException e) {
        rejectedElements.add(componentTypeElement);
      }
    }
    return rejectedElements.build();
  }

  private final class EmptyComponentGenerator extends SourceFileGenerator<ComponentDescriptor> {
    EmptyComponentGenerator(Filer filer, Elements elements, SourceVersion sourceVersion) {
      super(filer, elements, sourceVersion);
    }

    @Override
    ClassName nameGeneratedType(ComponentDescriptor input) {
      return componentName(input.componentDefinitionType());
    }

    @Override
    Optional<? extends Element> getElementForErrorReporting(ComponentDescriptor input) {
      return Optional.of(input.componentDefinitionType());
    }

    @Override
    Optional<TypeSpec.Builder> write(
        ClassName generatedTypeName, ComponentDescriptor componentDescriptor) {
      TypeSpec.Builder generatedComponent =
          TypeSpec.classBuilder(generatedTypeName)
              .addModifiers(PUBLIC, FINAL)
              .addMethod(privateConstructor());
      TypeElement componentElement = componentDescriptor.componentDefinitionType();
      addSupertype(generatedComponent, componentElement);

      TypeName builderMethodReturnType;
      if (componentDescriptor.builderSpec().isPresent()) {
        builderMethodReturnType =
            ClassName.get(componentDescriptor.builderSpec().get().builderDefinitionType());
      } else {
        TypeSpec.Builder builder =
            TypeSpec.classBuilder("Builder")
                .addModifiers(PUBLIC, STATIC, FINAL)
                .addMethod(privateConstructor());
        ClassName builderClassName = generatedTypeName.nestedClass("Builder");
        builderMethodReturnType = builderClassName;
        componentRequirements(componentDescriptor)
            .map(requirement -> builderInstanceMethod(requirement.typeElement(), builderClassName))
            .forEach(builder::addMethod);
        builder.addMethod(builderBuildMethod(componentDescriptor));
        generatedComponent.addType(builder.build());
      }

      generatedComponent.addMethod(staticBuilderMethod(builderMethodReturnType));

      if (componentRequirements(componentDescriptor)
              .noneMatch(requirement -> requirement.requiresAPassedInstance(elements, types))
          && !hasBindsInstanceMethods(componentDescriptor)) {
        generatedComponent.addMethod(createMethod(componentDescriptor));
      }

      DeclaredType componentType = MoreTypes.asDeclared(componentElement.asType());
      // TODO(ronshapiro): unify with AbstractComponentWriter
      Set<MethodSignature> methodSignatures =
          Sets.newHashSetWithExpectedSize(componentDescriptor.componentMethods().size());
      componentDescriptor
          .componentMethods()
          .stream()
          .filter(
              method -> {
                return methodSignatures.add(
                    MethodSignature.forComponentMethod(method, componentType, types));
              })
          .forEach(
              method ->
                  generatedComponent.addMethod(
                      emptyComponentMethod(componentElement, method.methodElement())));

      return Optional.of(generatedComponent);
    }
  }

  private MethodSpec emptyComponentMethod(TypeElement typeElement, ExecutableElement baseMethod) {
    return MethodSpec.overriding(baseMethod, MoreTypes.asDeclared(typeElement.asType()), types)
        .build();
  }

  private MethodSpec privateConstructor() {
    return constructorBuilder().addModifiers(PRIVATE).build();
  }

  /**
   * Returns the {@link ComponentRequirement}s for a component that does not have a {@link
   * ComponentDescriptor#builderSpec()}.
   */
  private Stream<ComponentRequirement> componentRequirements(ComponentDescriptor component) {
    checkArgument(component.kind().isTopLevel());
    return Stream.concat(
        component.dependencies().stream(),
        component
            .transitiveModules()
            .stream()
            .filter(module -> !module.moduleElement().getModifiers().contains(ABSTRACT))
            .map(module -> ComponentRequirement.forModule(module.moduleElement().asType())));
  }

  private boolean hasBindsInstanceMethods(ComponentDescriptor componentDescriptor) {
    return componentDescriptor.builderSpec().isPresent()
        && methodsIn(
                elements.getAllMembers(
                    componentDescriptor.builderSpec().get().builderDefinitionType()))
            .stream()
            .anyMatch(method -> isAnnotationPresent(method, BindsInstance.class));
  }

  private MethodSpec builderInstanceMethod(
      TypeElement componentRequirement, ClassName builderClass) {
    String simpleName =
        UPPER_CAMEL.to(LOWER_CAMEL, componentRequirement.getSimpleName().toString());
    return MethodSpec.methodBuilder(simpleName)
        .addModifiers(PUBLIC)
        .addParameter(ClassName.get(componentRequirement), simpleName)
        .returns(builderClass)
        .build();
  }

  private MethodSpec builderBuildMethod(ComponentDescriptor component) {
    return MethodSpec.methodBuilder("build")
        .addModifiers(PUBLIC)
        .returns(ClassName.get(component.componentDefinitionType()))
        .build();
  }

  private MethodSpec staticBuilderMethod(TypeName builderMethodReturnType) {
    return MethodSpec.methodBuilder("builder")
        .addModifiers(PUBLIC, STATIC)
        .returns(builderMethodReturnType)
        .build();
  }

  private MethodSpec createMethod(ComponentDescriptor componentDescriptor) {
    return MethodSpec.methodBuilder("create")
        .addModifiers(PUBLIC, STATIC)
        .returns(ClassName.get(componentDescriptor.componentDefinitionType()))
        .build();
  }
}

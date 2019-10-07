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
import static com.google.common.base.Predicates.in;
import static com.google.common.collect.Sets.union;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static dagger.internal.codegen.ComponentGenerator.componentName;
import static dagger.internal.codegen.base.ComponentAnnotation.rootComponentAnnotations;
import static dagger.internal.codegen.binding.ComponentCreatorAnnotation.rootComponentCreatorAnnotations;
import static dagger.internal.codegen.binding.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.javapoet.TypeSpecs.addSupertype;
import static java.util.Collections.disjoint;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.BindsInstance;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentCreatorKind;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptorFactory;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.MethodSignature;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.validation.ComponentCreatorValidator;
import dagger.internal.codegen.validation.ComponentValidator;
import dagger.internal.codegen.validation.ComponentValidator.ComponentValidationReport;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.producers.internal.CancellationListener;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
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
 * normal step. Method bodies are omitted as Turbine ignores them entirely.
 */
final class ComponentHjarProcessingStep extends TypeCheckingProcessingStep<TypeElement> {
  private final SourceVersion sourceVersion;
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final Filer filer;
  private final Messager messager;
  private final ComponentValidator componentValidator;
  private final ComponentCreatorValidator creatorValidator;
  private final ComponentDescriptorFactory componentDescriptorFactory;
  private final KotlinMetadataUtil metadataUtil;
  private ImmutableMap<Element, ValidationReport<TypeElement>> creatorReportsByComponent;

  @Inject
  ComponentHjarProcessingStep(
      SourceVersion sourceVersion,
      DaggerElements elements,
      DaggerTypes types,
      Filer filer,
      Messager messager,
      ComponentValidator componentValidator,
      ComponentCreatorValidator creatorValidator,
      ComponentDescriptorFactory componentDescriptorFactory,
      KotlinMetadataUtil metadataUtil) {
    super(MoreElements::asType);
    this.sourceVersion = sourceVersion;
    this.elements = elements;
    this.types = types;
    this.filer = filer;
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.creatorValidator = creatorValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.metadataUtil = metadataUtil;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return union(rootComponentAnnotations(), rootComponentCreatorAnnotations());
  }

  // TODO(ronshapiro): Validation might not even be necessary. We should measure it and figure out
  // if it's worth seeing if removing it will still work. We could potentially add a new catch
  // clause for any exception that's not TypeNotPresentException and ignore the component entirely
  // in that case.
  @Override
  public ImmutableSet<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    creatorReportsByComponent = creatorReportsByComponent(elementsByAnnotation);
    return super.process(elementsByAnnotation);
  }

  @Override
  protected void process(
      TypeElement element, ImmutableSet<Class<? extends Annotation>> annotations) {
    // Skip creator validation because those have already been validated.
    if (!disjoint(annotations, rootComponentCreatorAnnotations())) {
      return;
    }
    // Skip component validation if its creator validation already failed.
    if (creatorReportsByComponent.get(element) != null
            && !creatorReportsByComponent.get(element).isClean()) {
      return;
    }
    ComponentValidationReport validationReport =
        componentValidator.validate(element, ImmutableSet.of(), ImmutableSet.of());
    validationReport.report().printMessagesTo(messager);
    if (validationReport.report().isClean()) {
      new EmptyComponentGenerator(filer, elements, sourceVersion)
          .generate(componentDescriptorFactory.rootComponentDescriptor(element), messager);
    }
  }

  private ImmutableMap<Element, ValidationReport<TypeElement>> creatorReportsByComponent(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet<Element> creatorElements =
        ImmutableSet.copyOf(
            Multimaps.filterKeys(elementsByAnnotation, in(rootComponentCreatorAnnotations()))
                .values());
    // Can't use an ImmutableMap.Builder here because a component may have (invalidly) more than one
    // builder type, and that would make ImmutableMap.Builder throw.
    Map<Element, ValidationReport<TypeElement>> reports = new HashMap<>();
    for (Element element : creatorElements) {
      ValidationReport<TypeElement> report =
          creatorValidator.validate(MoreElements.asType(element));
      report.printMessagesTo(messager);
      reports.put(element.getEnclosingElement(), report);
    }
    return ImmutableMap.copyOf(reports);
  }

  private final class EmptyComponentGenerator extends SourceFileGenerator<ComponentDescriptor> {
    EmptyComponentGenerator(Filer filer, DaggerElements elements, SourceVersion sourceVersion) {
      super(filer, elements, sourceVersion);
    }

    @Override
    public ClassName nameGeneratedType(ComponentDescriptor input) {
      return componentName(input.typeElement());
    }

    @Override
    public Element originatingElement(ComponentDescriptor input) {
      return input.typeElement();
    }

    @Override
    public Optional<TypeSpec.Builder> write(ComponentDescriptor componentDescriptor) {
      ClassName generatedTypeName = nameGeneratedType(componentDescriptor);
      TypeSpec.Builder generatedComponent =
          TypeSpec.classBuilder(generatedTypeName)
              .addModifiers(FINAL)
              .addMethod(privateConstructor());
      if (componentDescriptor.typeElement().getModifiers().contains(PUBLIC)) {
        generatedComponent.addModifiers(PUBLIC);
      }

      TypeElement componentElement = componentDescriptor.typeElement();
      addSupertype(generatedComponent, componentElement);

      TypeName builderMethodReturnType;
      ComponentCreatorKind creatorKind;
      boolean noArgFactoryMethod;
      if (componentDescriptor.creatorDescriptor().isPresent()) {
        ComponentCreatorDescriptor creatorDescriptor =
            componentDescriptor.creatorDescriptor().get();
        builderMethodReturnType = ClassName.get(creatorDescriptor.typeElement());
        creatorKind = creatorDescriptor.kind();
        noArgFactoryMethod = creatorDescriptor.factoryParameters().isEmpty();
      } else {
        TypeSpec.Builder builder =
            TypeSpec.classBuilder("Builder")
                .addModifiers(STATIC, FINAL)
                .addMethod(privateConstructor());
        if (componentDescriptor.typeElement().getModifiers().contains(PUBLIC)) {
          builder.addModifiers(PUBLIC);
        }

        ClassName builderClassName = generatedTypeName.nestedClass("Builder");
        builderMethodReturnType = builderClassName;
        creatorKind = BUILDER;
        noArgFactoryMethod = true;
        componentRequirements(componentDescriptor)
            .map(requirement -> builderSetterMethod(requirement.typeElement(), builderClassName))
            .forEach(builder::addMethod);
        builder.addMethod(builderBuildMethod(componentDescriptor));
        generatedComponent.addType(builder.build());
      }

      generatedComponent.addMethod(staticCreatorMethod(builderMethodReturnType, creatorKind));

      if (noArgFactoryMethod
          && !hasBindsInstanceMethods(componentDescriptor)
          && componentRequirements(componentDescriptor)
              .noneMatch(
                  requirement ->
                      requirement.requiresAPassedInstance(elements, types, metadataUtil))) {
        generatedComponent.addMethod(createMethod(componentDescriptor));
      }

      DeclaredType componentType = MoreTypes.asDeclared(componentElement.asType());
      // TODO(ronshapiro): unify with ComponentImplementationBuilder
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

      if (componentDescriptor.isProduction()) {
        generatedComponent
            .addSuperinterface(ClassName.get(CancellationListener.class))
            .addMethod(onProducerFutureCancelledMethod());
      }

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
   * ComponentDescriptor#creatorDescriptor()}.
   */
  private Stream<ComponentRequirement> componentRequirements(ComponentDescriptor component) {
    checkArgument(!component.isSubcomponent());
    return Stream.concat(
        component.dependencies().stream(),
        component.modules().stream()
            .filter(module -> !module.moduleElement().getModifiers().contains(ABSTRACT))
            .map(module -> ComponentRequirement.forModule(module.moduleElement().asType())));
  }

  private boolean hasBindsInstanceMethods(ComponentDescriptor componentDescriptor) {
    return componentDescriptor.creatorDescriptor().isPresent()
        && elements
            .getUnimplementedMethods(componentDescriptor.creatorDescriptor().get().typeElement())
            .stream()
            .anyMatch(method -> isBindsInstance(method));
  }

  private static boolean isBindsInstance(ExecutableElement method) {
    if (isAnnotationPresent(method, BindsInstance.class)) {
      return true;
    }

    if (method.getParameters().size() == 1) {
      return isAnnotationPresent(method.getParameters().get(0), BindsInstance.class);
    }

    return false;
  }

  private MethodSpec builderSetterMethod(
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
        .returns(ClassName.get(component.typeElement()))
        .build();
  }

  private MethodSpec staticCreatorMethod(
      TypeName creatorMethodReturnType, ComponentCreatorKind creatorKind) {
    return MethodSpec.methodBuilder(Ascii.toLowerCase(creatorKind.typeName()))
        .addModifiers(PUBLIC, STATIC)
        .returns(creatorMethodReturnType)
        .build();
  }

  private MethodSpec createMethod(ComponentDescriptor componentDescriptor) {
    return MethodSpec.methodBuilder("create")
        .addModifiers(PUBLIC, STATIC)
        .returns(ClassName.get(componentDescriptor.typeElement()))
        .build();
  }

  private MethodSpec onProducerFutureCancelledMethod() {
    return MethodSpec.methodBuilder("onProducerFutureCancelled")
        .addModifiers(PUBLIC)
        .addParameter(TypeName.BOOLEAN, "mayInterruptIfRunning")
        .build();
  }
}

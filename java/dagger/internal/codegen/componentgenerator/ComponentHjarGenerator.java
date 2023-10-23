/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.internal.codegen.componentgenerator;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static dagger.internal.codegen.base.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.javapoet.TypeSpecs.addSupertype;
import static dagger.internal.codegen.langmodel.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.writing.ComponentNames.getTopLevelClassName;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.base.ComponentCreatorKind;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.ComponentCreatorDescriptor;
import dagger.internal.codegen.binding.ComponentDescriptor;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.binding.MethodSignature;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.MethodSpecs;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * A component generator that emits only API, without any actual implementation.
 *
 * <p>When compiling a header jar (hjar), Bazel needs to run annotation processors that generate
 * API, like Dagger, to see what code they might output. Full binding graph analysis is costly and
 * unnecessary from the perspective of the header compiler; it's sole goal is to pass along a
 * slimmed down version of what will be the jar for a particular compilation, whether or not that
 * compilation succeeds. If it does not, the compilation pipeline will fail, even if header
 * compilation succeeded.
 *
 * <p>The components emitted by this processing step include all of the API elements exposed by the
 * normal step. Method bodies are omitted as Turbine ignores them entirely.
 */
final class ComponentHjarGenerator extends SourceFileGenerator<ComponentDescriptor> {
  private final XProcessingEnv processingEnv;
  private final CompilerOptions compilerOptions;

  @Inject
  ComponentHjarGenerator(
      XFiler filer, XProcessingEnv processingEnv, CompilerOptions compilerOptions) {
    super(filer, processingEnv);
    this.processingEnv = processingEnv;
    this.compilerOptions = compilerOptions;
  }

  @Override
  public XElement originatingElement(ComponentDescriptor input) {
    return input.typeElement();
  }

  @Override
  public ImmutableList<TypeSpec.Builder> topLevelTypes(ComponentDescriptor componentDescriptor) {
    ClassName generatedTypeName = getTopLevelClassName(componentDescriptor);
    TypeSpec.Builder generatedComponent =
        TypeSpec.classBuilder(generatedTypeName)
            .addModifiers(FINAL)
            .addMethod(privateConstructor());
    if (componentDescriptor.typeElement().isPublic()) {
      generatedComponent.addModifiers(PUBLIC);
    }

    XTypeElement componentElement = componentDescriptor.typeElement();
    if (compilerOptions.generatedClassExtendsComponent()) {
      addSupertype(generatedComponent, componentElement);
    }

    TypeName builderMethodReturnType;
    ComponentCreatorKind creatorKind;
    boolean noArgFactoryMethod;
    if (componentDescriptor.creatorDescriptor().isPresent()) {
      ComponentCreatorDescriptor creatorDescriptor = componentDescriptor.creatorDescriptor().get();
      builderMethodReturnType = creatorDescriptor.typeElement().getClassName();
      creatorKind = creatorDescriptor.kind();
      noArgFactoryMethod = creatorDescriptor.factoryParameters().isEmpty();
    } else {
      TypeSpec.Builder builder =
          TypeSpec.classBuilder("Builder")
              .addModifiers(STATIC, FINAL)
              .addMethod(privateConstructor());
      if (componentDescriptor.typeElement().isPublic()) {
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
            .noneMatch(ComponentRequirement::requiresAPassedInstance)) {
      generatedComponent.addMethod(createMethod(componentDescriptor));
    }

    if (compilerOptions.generatedClassExtendsComponent()) {
      XType componentType = componentElement.getType();
      // TODO(ronshapiro): unify with ComponentImplementationBuilder
      Set<MethodSignature> methodSignatures =
          Sets.newHashSetWithExpectedSize(componentDescriptor.componentMethods().size());
      componentDescriptor.componentMethods().stream()
          .filter(
              method ->
                  methodSignatures.add(
                      MethodSignature.forComponentMethod(method, componentType, processingEnv)))
          .forEach(
              method ->
                  generatedComponent.addMethod(
                      emptyComponentMethod(componentElement, method.methodElement())));

      if (componentDescriptor.isProduction()) {
        generatedComponent
            .addSuperinterface(TypeNames.CANCELLATION_LISTENER)
            .addMethod(onProducerFutureCancelledMethod());
      }
    }

    return ImmutableList.of(generatedComponent);
  }

  private MethodSpec emptyComponentMethod(XTypeElement typeElement, XMethodElement baseMethod) {
    return MethodSpecs.overriding(baseMethod, typeElement.getType()).build();
  }

  private static MethodSpec privateConstructor() {
    return constructorBuilder().addModifiers(PRIVATE).build();
  }

  /**
   * Returns the {@link ComponentRequirement}s for a component that does not have a {@link
   * ComponentDescriptor#creatorDescriptor()}.
   */
  private static Stream<ComponentRequirement> componentRequirements(ComponentDescriptor component) {
    // TODO(b/152802759): See if you can merge logics that normal component processing and hjar
    // component processing use. So that there would't be a duplicated logic (like the lines below)
    // everytime we modify the generated code for the component.
    checkArgument(!component.isSubcomponent());
    return Stream.concat(
        component.dependencies().stream(),
        component.modules().stream()
            .filter(
                module ->
                    !module.moduleElement().isAbstract()
                        && isElementAccessibleFrom(
                            module.moduleElement(),
                            component.typeElement().getClassName().packageName()))
            .map(module -> ComponentRequirement.forModule(module.moduleElement().getType()))
            // If the user hasn't defined an explicit creator/builder then we need to prune out the
            // module requirements that don't require a module instance to match the non-hjar
            // implementation.
            .filter(
                requirement ->
                    component.creatorDescriptor().isPresent()
                        || requirement.requiresModuleInstance()));
  }

  private boolean hasBindsInstanceMethods(ComponentDescriptor componentDescriptor) {
    return componentDescriptor.creatorDescriptor().isPresent()
        && getAllUnimplementedMethods(componentDescriptor.creatorDescriptor().get().typeElement())
            .stream()
            .anyMatch(method -> isBindsInstance(method));
  }

  private static boolean isBindsInstance(XMethodElement method) {
    return method.hasAnnotation(TypeNames.BINDS_INSTANCE)
        || (method.getParameters().size() == 1
            && getOnlyElement(method.getParameters()).hasAnnotation(TypeNames.BINDS_INSTANCE));
  }

  private static MethodSpec builderSetterMethod(
      XTypeElement componentRequirement, ClassName builderClass) {
    String simpleName = UPPER_CAMEL.to(LOWER_CAMEL, getSimpleName(componentRequirement));
    return MethodSpec.methodBuilder(simpleName)
        .addModifiers(PUBLIC)
        .addParameter(componentRequirement.getClassName(), simpleName)
        .returns(builderClass)
        .build();
  }

  private static MethodSpec builderBuildMethod(ComponentDescriptor component) {
    return MethodSpec.methodBuilder("build")
        .addModifiers(PUBLIC)
        .returns(component.typeElement().getClassName())
        .build();
  }

  private static MethodSpec staticCreatorMethod(
      TypeName creatorMethodReturnType, ComponentCreatorKind creatorKind) {
    return MethodSpec.methodBuilder(Ascii.toLowerCase(creatorKind.typeName()))
        .addModifiers(PUBLIC, STATIC)
        .returns(creatorMethodReturnType)
        .build();
  }

  private static MethodSpec createMethod(ComponentDescriptor componentDescriptor) {
    return MethodSpec.methodBuilder("create")
        .addModifiers(PUBLIC, STATIC)
        .returns(componentDescriptor.typeElement().getClassName())
        .build();
  }

  private static MethodSpec onProducerFutureCancelledMethod() {
    return MethodSpec.methodBuilder("onProducerFutureCancelled")
        .addModifiers(PUBLIC)
        .addParameter(TypeName.BOOLEAN, "mayInterruptIfRunning")
        .build();
  }
}

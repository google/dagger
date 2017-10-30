/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.common.MoreTypes;
import com.google.common.base.CaseFormat;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/**
 * Creates the nested implementation class for a subcomponent.
 */
final class SubcomponentWriter extends AbstractComponentWriter {

  private final AbstractComponentWriter parent;

  /**
   * The parent's factory method to create this subcomponent, or {@link Optional#empty()} if the
   * subcomponent was added via {@link dagger.Module#subcomponents()}.
   */
  private final Optional<ComponentMethodDescriptor> subcomponentFactoryMethod;

  SubcomponentWriter(
      AbstractComponentWriter parent,
      Optional<ComponentMethodDescriptor> subcomponentFactoryMethod,
      BindingGraph subgraph) {
    super(parent, subcomponentName(parent, subgraph), subgraph);
    this.parent = parent;
    this.subcomponentFactoryMethod = subcomponentFactoryMethod;
  }

  private static ClassName subcomponentName(AbstractComponentWriter parent, BindingGraph subgraph) {
    return parent.name.nestedClass(
        parent.subcomponentNames.get(subgraph.componentDescriptor()) + "Impl");
  }

  @Override
  public CodeBlock getReferenceReleasingProviderManagerExpression(Scope scope) {
    return parent.getReferenceReleasingProviderManagerExpression(scope);
  }

  @Override
  protected boolean requiresReleasableReferences(Scope scope) {
    return parent.requiresReleasableReferences(scope);
  }

  private ExecutableType resolvedSubcomponentFactoryMethod() {
    checkState(
        subcomponentFactoryMethod.isPresent(),
        "%s does not have a factory method for %s",
        parent.graph.componentType(),
        graph.componentType());
    return MoreTypes.asExecutable(
        types.asMemberOf(
            MoreTypes.asDeclared(parent.graph.componentType().asType()),
            subcomponentFactoryMethod.get().methodElement()));
  }

  @Override
  protected void decorateComponent() {
    component.addModifiers(PRIVATE, FINAL);
    addSupertype(
        component,
        MoreTypes.asTypeElement(
            graph.componentDescriptor().builderSpec().isPresent()
                ? graph.componentDescriptor().builderSpec().get().componentType()
                : resolvedSubcomponentFactoryMethod().getReturnType()));
  }

  @Override
  protected void addBuilderClass(TypeSpec builder) {
    parent.component.addType(builder);
  }

  @Override
  protected void addFactoryMethods() {
    if (!subcomponentFactoryMethod.isPresent()
        || !subcomponentFactoryMethod.get().kind().isSubcomponentKind()) {
      // subcomponent builder methods are implemented in
      // AbstractComponentWriter.implementInterfaceMethods
      return;
    }
    MethodSpec.Builder componentMethod =
        methodBuilder(subcomponentFactoryMethod.get().methodElement().getSimpleName().toString())
            .addModifiers(PUBLIC)
            .addAnnotation(Override.class);
    ExecutableType resolvedMethod = resolvedSubcomponentFactoryMethod();
    componentMethod.returns(ClassName.get(resolvedMethod.getReturnType()));
    writeSubcomponentWithoutBuilder(componentMethod, resolvedMethod);
    parent.interfaceMethods.add(componentMethod.build());
  }

  private void writeSubcomponentWithoutBuilder(
      MethodSpec.Builder componentMethod, ExecutableType resolvedMethod) {
    ImmutableList.Builder<CodeBlock> subcomponentConstructorParameters = ImmutableList.builder();
    List<? extends VariableElement> params =
        subcomponentFactoryMethod.get().methodElement().getParameters();
    List<? extends TypeMirror> paramTypes = resolvedMethod.getParameterTypes();
    for (int i = 0; i < params.size(); i++) {
      VariableElement moduleVariable = params.get(i);
      TypeElement moduleTypeElement = MoreTypes.asTypeElement(paramTypes.get(i));
      ComponentRequirement componentRequirement =
          ComponentRequirement.forModule(moduleTypeElement.asType());
      TypeName moduleType = TypeName.get(paramTypes.get(i));
      componentMethod.addParameter(moduleType, moduleVariable.getSimpleName().toString());
      if (!componentRequirementFields.contains(componentRequirement)) {
        String preferredModuleName =
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleTypeElement.getSimpleName().toString());
        FieldSpec contributionField =
            componentField(ClassName.get(moduleTypeElement), preferredModuleName)
                .addModifiers(PRIVATE, FINAL)
                .build();
        component.addField(contributionField);

        constructor
            .addParameter(moduleType, contributionField.name)
            .addStatement(
                "this.$1N = $2T.checkNotNull($1N)", contributionField, Preconditions.class);

        componentRequirementFields.add(
            ComponentRequirementField.componentField(
                componentRequirement, contributionField, name));
        subcomponentConstructorParameters.add(
            CodeBlock.of("$L", moduleVariable.getSimpleName()));
      }
    }

    Set<ComponentRequirement> uninitializedModules =
        Sets.filter(
            graph.componentRequirements(),
            Predicates.not(componentRequirementFields::contains));

    for (ComponentRequirement componentRequirement : uninitializedModules) {
      checkState(componentRequirement.kind().equals(ComponentRequirement.Kind.MODULE));
      TypeElement moduleType = componentRequirement.typeElement();
      String preferredModuleName =
          CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleType.getSimpleName().toString());
      FieldSpec contributionField =
          componentField(ClassName.get(moduleType), preferredModuleName)
              .addModifiers(PRIVATE, FINAL)
              .build();
      component.addField(contributionField);
      constructor.addStatement("this.$N = new $T()", contributionField, ClassName.get(moduleType));
      componentRequirementFields.add(
          ComponentRequirementField.componentField(
              componentRequirement, contributionField, name));
    }

    componentMethod.addStatement("return new $T($L)",
        name, makeParametersCodeBlock(subcomponentConstructorParameters.build()));
  }
}

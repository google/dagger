/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.MoreTypes;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Sets.difference;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.UNINITIALIZED;
import static dagger.internal.codegen.MemberSelect.localField;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Creates the nested implementation class for a subcomponent.
 */
final class SubcomponentWriter extends AbstractComponentWriter {

  private AbstractComponentWriter parent;
  private ExecutableElement subcomponentFactoryMethod;

  public SubcomponentWriter(
      AbstractComponentWriter parent,
      ExecutableElement subcomponentFactoryMethod,
      BindingGraph subgraph) {
    super(
        parent.types,
        parent.elements,
        parent.keyFactory,
        parent.nullableValidationType,
        subcomponentName(parent, subgraph),
        subgraph,
        parent.subcomponentImplNames);
    this.parent = parent;
    this.subcomponentFactoryMethod = subcomponentFactoryMethod;
  }

  private static ClassName subcomponentName(AbstractComponentWriter parent, BindingGraph subgraph) {
    return parent.name.nestedClassNamed(
        parent.subcomponentImplNames.get(subgraph.componentDescriptor()));
  }

  @Override
  protected InitializationState getInitializationState(BindingKey bindingKey) {
    InitializationState initializationState = super.getInitializationState(bindingKey);
    return initializationState.equals(UNINITIALIZED)
        ? parent.getInitializationState(bindingKey)
        : initializationState;
  }

  @Override
  protected Optional<Snippet> getOrCreateComponentContributionFieldSnippet(
      TypeElement contributionType) {
    return super.getOrCreateComponentContributionFieldSnippet(contributionType)
        .or(parent.getOrCreateComponentContributionFieldSnippet(contributionType));
  }

  @Override
  protected MemberSelect getMemberSelect(BindingKey key) {
    MemberSelect memberSelect = super.getMemberSelect(key);
    return memberSelect == null ? parent.getMemberSelect(key) : memberSelect;
  }

  @Override
  protected Optional<MemberSelect> getMultibindingContributionSnippet(ContributionBinding binding) {
    return super.getMultibindingContributionSnippet(binding)
        .or(parent.getMultibindingContributionSnippet(binding));
  }

  private ExecutableType resolvedSubcomponentFactoryMethod() {
    return MoreTypes.asExecutable(
        types.asMemberOf(
            MoreTypes.asDeclared(parent.componentDefinitionType().asType()),
            subcomponentFactoryMethod));
  }

  @Override
  protected ClassWriter createComponentClass() {
    ClassWriter componentWriter = parent.componentWriter.addNestedClass(name.simpleName());
    componentWriter.addModifiers(PRIVATE, FINAL);
    componentWriter.setSupertype(
        MoreTypes.asTypeElement(
            graph.componentDescriptor().builderSpec().isPresent()
                ? graph
                    .componentDescriptor()
                    .builderSpec()
                    .get()
                    .componentType()
                : resolvedSubcomponentFactoryMethod().getReturnType()));
    return componentWriter;
  }

  @Override
  protected void addBuilder() {
    // Only write subcomponent builders if there is a spec.
    if (graph.componentDescriptor().builderSpec().isPresent()) {
      super.addBuilder();
    }
  }

  @Override
  protected ClassWriter createBuilder() {
    // Only write subcomponent builders if there is a spec.
    verify(graph.componentDescriptor().builderSpec().isPresent());
    return parent.componentWriter.addNestedClass(
        componentDefinitionTypeName().simpleName() + "Builder");
  }

  @Override
  protected void addFactoryMethods() {
    MethodWriter componentMethod;
    if (graph.componentDescriptor().builderSpec().isPresent()) {
      BuilderSpec spec = graph.componentDescriptor().builderSpec().get();
      componentMethod =
          parent.componentWriter.addMethod(
              spec.builderDefinitionType().asType(),
              subcomponentFactoryMethod.getSimpleName().toString());
      componentMethod.body().addSnippet("return new %s();", builderName.get());
    } else {
      ExecutableType resolvedMethod = resolvedSubcomponentFactoryMethod();
      componentMethod =
          parent.componentWriter.addMethod(
              resolvedMethod.getReturnType(), subcomponentFactoryMethod.getSimpleName().toString());
      writeSubcomponentWithoutBuilder(componentMethod, resolvedMethod);
    }
    componentMethod.addModifiers(PUBLIC);
    componentMethod.annotate(Override.class);
  }

  private void writeSubcomponentWithoutBuilder(
      MethodWriter componentMethod, ExecutableType resolvedMethod) {
    ImmutableList.Builder<Snippet> subcomponentConstructorParameters = ImmutableList.builder();
    List<? extends VariableElement> params = subcomponentFactoryMethod.getParameters();
    List<? extends TypeMirror> paramTypes = resolvedMethod.getParameterTypes();
    for (int i = 0; i < params.size(); i++) {
      VariableElement moduleVariable = params.get(i);
      TypeElement moduleTypeElement = MoreTypes.asTypeElement(paramTypes.get(i));
      TypeName moduleType = TypeNames.forTypeMirror(paramTypes.get(i));
      componentMethod.addParameter(moduleType, moduleVariable.getSimpleName().toString());
      if (!componentContributionFields.containsKey(moduleTypeElement)) {
        String preferredModuleName =
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleTypeElement.getSimpleName().toString());
        FieldWriter contributionField =
            componentWriter.addField(moduleTypeElement, preferredModuleName);
        contributionField.addModifiers(PRIVATE, FINAL);
        String actualModuleName = contributionField.name();
        constructorWriter.addParameter(moduleType, actualModuleName);
        constructorWriter.body()
            .addSnippet("if (%s == null) {", actualModuleName)
            .addSnippet("  throw new NullPointerException();")
            .addSnippet("}");
        constructorWriter.body().addSnippet("this.%1$s = %1$s;", actualModuleName);
        MemberSelect moduleSelect = localField(name, actualModuleName);
        componentContributionFields.put(moduleTypeElement, moduleSelect);
        subcomponentConstructorParameters.add(Snippet.format("%s", moduleVariable.getSimpleName()));
      }
    }

    Set<TypeElement> uninitializedModules =
        difference(graph.componentRequirements(), componentContributionFields.keySet());

    for (TypeElement moduleType : uninitializedModules) {
      String preferredModuleName =
          CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleType.getSimpleName().toString());
      FieldWriter contributionField = componentWriter.addField(moduleType, preferredModuleName);
      contributionField.addModifiers(PRIVATE, FINAL);
      String actualModuleName = contributionField.name();
      constructorWriter.body().addSnippet("this.%s = new %s();",
          actualModuleName, ClassName.fromTypeElement(moduleType));
      MemberSelect moduleSelect = localField(name, actualModuleName);
      componentContributionFields.put(moduleType, moduleSelect);
    }

    componentMethod.body().addSnippet("return new %s(%s);",
        name, Snippet.makeParametersSnippet(subcomponentConstructorParameters.build()));
  }
}

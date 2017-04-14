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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeName.VOID;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.Accessibility.isElementAccessibleFrom;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.RAWTYPES;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.AnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.TypeNames.membersInjectorOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.MembersInjector;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.producers.Producer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

/**
 * Generates {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class MembersInjectorGenerator extends SourceFileGenerator<MembersInjectionBinding> {
  private final InjectValidator injectValidator;

  MembersInjectorGenerator(Filer filer, Elements elements, InjectValidator injectValidator) {
    super(filer, elements);
    this.injectValidator = injectValidator;
  }

  @Override
  ClassName nameGeneratedType(MembersInjectionBinding binding) {
    return membersInjectorNameForType(binding.membersInjectedType());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(MembersInjectionBinding binding) {
    return Optional.of(binding.membersInjectedType());
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, MembersInjectionBinding binding) {
    // Empty members injection bindings are special and don't need source files.
    if (binding.injectionSites().isEmpty()) {
      return Optional.empty();
    }
    if (!injectValidator.isValidType(binding.key().type())) {
      return Optional.empty();
    }
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkState(
        !binding.unresolved().isPresent(),
        "tried to generate a MembersInjector for a binding of a resolved generic type: %s",
        binding);

    ImmutableList<TypeVariableName> typeParameters = bindingTypeElementTypeVariableNames(binding);
    TypeSpec.Builder injectorTypeBuilder =
        classBuilder(generatedTypeName)
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariables(typeParameters);

    TypeName injectedTypeName = TypeName.get(binding.key().type());
    TypeName implementedType = membersInjectorOf(injectedTypeName);
    injectorTypeBuilder.addSuperinterface(implementedType);

    MethodSpec.Builder injectMembersBuilder =
        methodBuilder("injectMembers")
            .returns(VOID)
            .addModifiers(PUBLIC)
            .addAnnotation(Override.class)
            .addParameter(injectedTypeName, "instance")
            .addCode("if (instance == null) {")
            .addStatement(
                "throw new $T($S)",
                NullPointerException.class,
                "Cannot inject members into a null reference")
            .addCode("}");

    ImmutableMap<BindingKey, FrameworkField> fields = generateBindingFieldsForDependencies(binding);

    ImmutableMap.Builder<BindingKey, FieldSpec> dependencyFieldsBuilder = ImmutableMap.builder();

    MethodSpec.Builder constructorBuilder = constructorBuilder().addModifiers(PUBLIC);

    // We use a static create method so that generated components can avoid having
    // to refer to the generic types of the factory.
    // (Otherwise they may have visibility problems referring to the types.)
    MethodSpec.Builder createMethodBuilder =
        methodBuilder("create")
            .returns(implementedType)
            .addModifiers(PUBLIC, STATIC)
            .addTypeVariables(typeParameters);

    createMethodBuilder.addCode(
        "return new $T(", parameterizedGeneratedTypeNameForBinding(binding));
    ImmutableList.Builder<CodeBlock> constructorInvocationParameters = ImmutableList.builder();

    boolean usesRawFrameworkTypes = false;
    UniqueNameSet fieldNames = new UniqueNameSet();
    for (Entry<BindingKey, FrameworkField> fieldEntry : fields.entrySet()) {
      BindingKey dependencyBindingKey = fieldEntry.getKey();
      FrameworkField bindingField = fieldEntry.getValue();

      // If the dependency type is not visible to this members injector, then use the raw framework
      // type for the field.
      boolean useRawFrameworkType =
          !isTypeAccessibleFrom(dependencyBindingKey.key().type(), generatedTypeName.packageName());

      String fieldName = fieldNames.getUniqueName(bindingField.name());
      TypeName fieldType = useRawFrameworkType ? bindingField.type().rawType : bindingField.type();
      FieldSpec.Builder fieldBuilder = FieldSpec.builder(fieldType, fieldName, PRIVATE, FINAL);
      ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(fieldType, fieldName);

      // If we're using the raw type for the field, then suppress the injectMembers method's
      // unchecked-type warning and the field's and the constructor and create-method's
      // parameters' raw-type warnings.
      if (useRawFrameworkType) {
        usesRawFrameworkTypes = true;
        fieldBuilder.addAnnotation(suppressWarnings(RAWTYPES));
        parameterBuilder.addAnnotation(suppressWarnings(RAWTYPES));
      }
      constructorBuilder.addParameter(parameterBuilder.build());
      createMethodBuilder.addParameter(parameterBuilder.build());

      FieldSpec field = fieldBuilder.build();
      injectorTypeBuilder.addField(field);
      constructorBuilder.addStatement("assert $N != null", field);
      constructorBuilder.addStatement("this.$N = $N", field, field);
      dependencyFieldsBuilder.put(dependencyBindingKey, field);
      constructorInvocationParameters.add(CodeBlock.of("$N", field));
    }

    createMethodBuilder.addCode(
        constructorInvocationParameters.build().stream().collect(toParametersCodeBlock()));
    createMethodBuilder.addCode(");");

    injectorTypeBuilder.addMethod(constructorBuilder.build());
    injectorTypeBuilder.addMethod(createMethodBuilder.build());

    Set<String> delegateMethods = new HashSet<>();
    ImmutableMap<BindingKey, FieldSpec> dependencyFields = dependencyFieldsBuilder.build();
    List<MethodSpec> injectMethodsForSubclasses = new ArrayList<>();
    for (InjectionSite injectionSite : binding.injectionSites()) {
      injectMembersBuilder.addCode(
          isElementAccessibleFrom(injectionSite.element(), generatedTypeName.packageName())
              ? directInjectMemberCodeBlock(binding, dependencyFields, injectionSite)
              : delegateInjectMemberCodeBlock(dependencyFields, injectionSite));
      if (!injectionSite.element().getModifiers().contains(PUBLIC)
          && injectionSite.element().getEnclosingElement().equals(binding.membersInjectedType())
          && delegateMethods.add(injectionSiteDelegateMethodName(injectionSite.element()))) {
        injectMethodsForSubclasses.add(
            injectorMethodForSubclasses(
                dependencyFields,
                typeParameters,
                injectedTypeName,
                injectionSite.element(),
                injectionSite.dependencies()));
      }
    }

    if (usesRawFrameworkTypes) {
      injectMembersBuilder.addAnnotation(suppressWarnings(UNCHECKED));
    }

    injectorTypeBuilder.addMethod(injectMembersBuilder.build());
    injectMethodsForSubclasses.forEach(injectorTypeBuilder::addMethod);

    return Optional.of(injectorTypeBuilder);
  }

  /** Returns a code block that directly injects the instance's field or method. */
  private CodeBlock directInjectMemberCodeBlock(
      MembersInjectionBinding binding,
      ImmutableMap<BindingKey, FieldSpec> dependencyFields,
      InjectionSite injectionSite) {
    return CodeBlock.of(
        injectionSite.element().getKind().isField() ? "$L.$L = $L;" : "$L.$L($L);",
        getInstanceCodeBlockWithPotentialCast(
            injectionSite.element().getEnclosingElement(), binding.membersInjectedType()),
        injectionSite.element().getSimpleName(),
        makeParametersCodeBlock(
            parameterCodeBlocks(dependencyFields, injectionSite.dependencies(), true)));
  }

  /**
   * Returns a code block that injects the instance's field or method by calling a static method on
   * the parent MembersInjector class.
   */
  private CodeBlock delegateInjectMemberCodeBlock(
      ImmutableMap<BindingKey, FieldSpec> dependencyFields, InjectionSite injectionSite) {
    return CodeBlock.of(
        "$L.$L($L);",
        membersInjectorNameForType(
            MoreElements.asType(injectionSite.element().getEnclosingElement())),
        injectionSiteDelegateMethodName(injectionSite.element()),
        makeParametersCodeBlock(
            new ImmutableList.Builder<CodeBlock>()
                .add(CodeBlock.of("instance"))
                .addAll(parameterCodeBlocks(dependencyFields, injectionSite.dependencies(), false))
                .build()));
  }

  /**
   * Returns the parameters for injecting a member.
   *
   * @param passValue if {@code true}, each parameter code block will be the result of converting
   *     the field from the framework type ({@link Provider}, {@link Producer}, etc.) to the real
   *     value; if {@code false}, each parameter code block will be just the field
   */
  private ImmutableList<CodeBlock> parameterCodeBlocks(
      ImmutableMap<BindingKey, FieldSpec> dependencyFields,
      ImmutableSet<DependencyRequest> dependencies,
      boolean passValue) {
    ImmutableList.Builder<CodeBlock> parameters = ImmutableList.builder();
    for (DependencyRequest dependency : dependencies) {
      CodeBlock fieldCodeBlock =
          CodeBlock.of("$L", dependencyFields.get(dependency.bindingKey()).name);
      parameters.add(
          passValue
              ? frameworkTypeUsageStatement(fieldCodeBlock, dependency.kind())
              : fieldCodeBlock);
    }
    return parameters.build();
  }

  private CodeBlock getInstanceCodeBlockWithPotentialCast(
      Element injectionSiteElement, Element bindingElement) {
    if (injectionSiteElement.equals(bindingElement)) {
      return CodeBlock.of("instance");
    }
    TypeName injectionSiteName = TypeName.get(injectionSiteElement.asType());
    if (injectionSiteName instanceof ParameterizedTypeName) {
      injectionSiteName = ((ParameterizedTypeName) injectionSiteName).rawType;
    }
    return CodeBlock.of("(($T) instance)", injectionSiteName);
  }

  private static String injectionSiteDelegateMethodName(Element injectionSiteElement) {
    return "inject"
        + CaseFormat.LOWER_CAMEL.to(
            CaseFormat.UPPER_CAMEL, injectionSiteElement.getSimpleName().toString());
  }

  private MethodSpec injectorMethodForSubclasses(
      ImmutableMap<BindingKey, FieldSpec> dependencyFields,
      List<TypeVariableName> typeParameters,
      TypeName injectedTypeName,
      Element injectionElement,
      ImmutableSet<DependencyRequest> dependencies) {
    MethodSpec.Builder methodBuilder =
        methodBuilder(injectionSiteDelegateMethodName(injectionElement))
            .addModifiers(PUBLIC, STATIC)
            .addParameter(injectedTypeName, "instance")
            .addTypeVariables(typeParameters);
    ImmutableList.Builder<CodeBlock> providedParameters = ImmutableList.builder();
    Set<String> parameterNames = new HashSet<>();
    for (DependencyRequest dependency : dependencies) {
      FieldSpec field = dependencyFields.get(dependency.bindingKey());
      ParameterSpec parameter =
          ParameterSpec.builder(
                  field.type,
                  staticInjectMethodDependencyParameterName(parameterNames, dependency, field))
              .build();
      methodBuilder.addParameter(parameter);
      providedParameters.add(
          frameworkTypeUsageStatement(CodeBlock.of("$N", parameter), dependency.kind()));
    }
    if (injectionElement.getKind().isField()) {
      methodBuilder.addStatement(
          "instance.$L = $L",
          injectionElement.getSimpleName(),
          getOnlyElement(providedParameters.build()));
    } else {
      methodBuilder.addStatement(
          "instance.$L($L)",
          injectionElement.getSimpleName(),
          makeParametersCodeBlock(providedParameters.build()));
    }
    return methodBuilder.build();
  }

  /**
   * Returns the static inject method parameter name for a dependency.
   *
   * @param parameterNames the parameter names used so far
   * @param dependency the dependency
   * @param field the field used to hold the framework type for the dependency
   */
  private String staticInjectMethodDependencyParameterName(
      Set<String> parameterNames, DependencyRequest dependency, FieldSpec field) {
    StringBuilder parameterName =
        new StringBuilder(dependency.requestElement().get().getSimpleName().toString());
    switch (dependency.kind()) {
      case LAZY:
      case INSTANCE:
      case FUTURE:
        String suffix = ((ParameterizedTypeName) field.type).rawType.simpleName();
        if (parameterName.length() <= suffix.length()
            || !parameterName.substring(parameterName.length() - suffix.length()).equals(suffix)) {
          parameterName.append(suffix);
        }
        break;

      default:
        break;
    }
    int baseLength = parameterName.length();
    for (int i = 2; !parameterNames.add(parameterName.toString()); i++) {
      parameterName.replace(baseLength, parameterName.length(), String.valueOf(i));
    }
    return parameterName.toString();
  }
}

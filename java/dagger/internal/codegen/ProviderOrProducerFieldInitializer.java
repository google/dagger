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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.Kind.INJECTION;
import static dagger.internal.codegen.GeneratedComponentModel.TypeSpecKind.COMPONENT_PROVISION_FACTORY;
import static dagger.internal.codegen.MapKeys.getMapKeyExpression;
import static dagger.internal.codegen.MoreAnnotationMirrors.getTypeValue;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.mapFactoryClassName;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.SourceFiles.setFactoryClassName;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.TypeNames.INSTANCE_FACTORY;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTORS;
import static dagger.internal.codegen.TypeNames.REFERENCE_RELEASING_PROVIDER;
import static dagger.internal.codegen.TypeNames.SINGLE_CHECK;
import static dagger.internal.codegen.TypeNames.TYPED_RELEASABLE_REFERENCE_MANAGER_DECORATOR;
import static dagger.internal.codegen.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.TypeNames.producerOf;
import static dagger.internal.codegen.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.InstanceFactory;
import dagger.internal.TypedReleasableReferenceManagerDecorator;
import dagger.model.Key;
import dagger.model.Scope;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.releasablereferences.ForReleasableReferences;
import dagger.releasablereferences.ReleasableReferenceManager;
import dagger.releasablereferences.TypedReleasableReferenceManager;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

/**
 * An initializer for {@link Provider} or {@link Producer} fields other than {@link Producer} fields
 * that {@linkplain ProducerFromProviderFieldInitializer adapt provision bindings}.
 */
// TODO(dpb): Split this up by binding kind.
final class ProviderOrProducerFieldInitializer extends FrameworkFieldInitializer {
  private final SubcomponentNames subcomponentNames;
  private final ComponentRequirementFields componentRequirementFields;
  // TODO(ronshapiro): add Binding.bindingKey() and use that instead of taking a ResolvedBindings
  private final ResolvedBindings resolvedBindings;
  private final CompilerOptions compilerOptions;
  private final BindingGraph graph;
  private final OptionalFactories optionalFactories;
  private final ReferenceReleasingManagerFields referenceReleasingManagerFields;

  ProviderOrProducerFieldInitializer(
      ResolvedBindings resolvedBindings,
      SubcomponentNames subcomponentNames,
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions,
      ComponentRequirementFields componentRequirementFields,
      ReferenceReleasingManagerFields referenceReleasingManagerFields,
      CompilerOptions compilerOptions,
      BindingGraph graph,
      OptionalFactories optionalFactories) {
    super(generatedComponentModel, componentBindingExpressions, resolvedBindings);
    checkArgument(resolvedBindings.contributionBindings().size() == 1);
    this.subcomponentNames = checkNotNull(subcomponentNames);
    this.componentRequirementFields = checkNotNull(componentRequirementFields);
    this.referenceReleasingManagerFields = checkNotNull(referenceReleasingManagerFields);
    this.resolvedBindings = checkNotNull(resolvedBindings);
    this.compilerOptions = checkNotNull(compilerOptions);
    this.graph = checkNotNull(graph);
    this.optionalFactories = checkNotNull(optionalFactories);
  }

  @Override
  protected CodeBlock getFieldInitialization() {
    ContributionBinding contributionBinding = resolvedBindings.contributionBinding();
    switch (contributionBinding.factoryCreationStrategy()) {
      case DELEGATE:
        CodeBlock delegatingCodeBlock =
            CodeBlock.of(
                "($T) $L",
                contributionBinding.bindingType().frameworkClass(),
                getDependencyExpression(
                    getOnlyElement(contributionBinding.frameworkDependencies())));
        return decorateForScope(delegatingCodeBlock, contributionBinding.scope());
      case SINGLETON_INSTANCE:
        checkState(contributionBinding.scope().isPresent());
        // fall through
      case CLASS_CONSTRUCTOR:
        return factoryForContributionBindingInitialization(contributionBinding);

      default:
        throw new AssertionError();
    }
  }

  private CodeBlock factoryForContributionBindingInitialization(ContributionBinding binding) {
    TypeName bindingKeyTypeName = TypeName.get(binding.key().type());
    switch (binding.bindingKind()) {
      case COMPONENT:
        // This bindingKeyTypeName type parameter can be removed when we drop java 7 source support
        return CodeBlock.of("$T.<$T>create(this)", INSTANCE_FACTORY, bindingKeyTypeName);

      case COMPONENT_DEPENDENCY:
        return CodeBlock.of(
            "$T.create($L)",
            INSTANCE_FACTORY,
            componentRequirementFields.getExpressionDuringInitialization(
                ComponentRequirement.forDependency(binding.key().type()),
                generatedComponentModel.name()));

      case COMPONENT_PROVISION:
        {
          ComponentRequirement dependency = dependencyForBinding(binding);
          String componentMethod = binding.bindingElement().get().getSimpleName().toString();
          // TODO(sameb): The Provider.get() throws a very vague NPE.  The stack trace doesn't
          // help to figure out what the method or return type is.  If we include a string
          // of the return type or method name in the error message, that can defeat obfuscation.
          // We can easily include the raw type (no generics) + annotation type (no values),
          // using .class & String.format -- but that wouldn't be the whole story.
          // What should we do?
          CodeBlock invocation =
              ComponentProvisionBindingExpression.maybeCheckForNull(
                  (ProvisionBinding) binding,
                  compilerOptions,
                  CodeBlock.of("$L.$L()", dependency.variableName(), componentMethod));
          ClassName dependencyClassName = ClassName.get(dependency.typeElement());
          String factoryName =
              dependencyClassName.toString().replace('.', '_') + "_" + componentMethod;
          MethodSpec.Builder getMethod =
              methodBuilder("get")
                  .addAnnotation(Override.class)
                  .addModifiers(PUBLIC)
                  .returns(bindingKeyTypeName)
                  .addStatement("return $L", invocation);
          if (binding.nullableType().isPresent()) {
            getMethod.addAnnotation(
                ClassName.get(MoreTypes.asTypeElement(binding.nullableType().get())));
          }
          generatedComponentModel.addType(
              COMPONENT_PROVISION_FACTORY,
              TypeSpec.classBuilder(factoryName)
                  .addSuperinterface(providerOf(bindingKeyTypeName))
                  .addModifiers(PRIVATE, STATIC)
                  .addField(dependencyClassName, dependency.variableName(), PRIVATE, FINAL)
                  .addMethod(
                      constructorBuilder()
                          .addParameter(dependencyClassName, dependency.variableName())
                          .addStatement("this.$1L = $1L", dependency.variableName())
                          .build())
                  .addMethod(getMethod.build())
                  .build());
          setFieldTypeReplacement(generatedComponentModel.name().nestedClass(factoryName));
          return CodeBlock.of(
              "new $L($L)",
              factoryName,
              componentRequirementFields.getExpressionDuringInitialization(
                  dependency, generatedComponentModel.name()));
        }

      case SUBCOMPONENT_BUILDER:
        String subcomponentName =
            subcomponentNames.get(
                graph
                    .componentDescriptor()
                    .subcomponentsByBuilderType()
                    .get(MoreTypes.asTypeElement(binding.key().type())));
        return CodeBlock.of(
            "$L",
            anonymousClassBuilder("")
                .superclass(providerOf(bindingKeyTypeName))
                .addMethod(
                    methodBuilder("get")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(bindingKeyTypeName)
                        .addStatement("return new $LBuilder()", subcomponentName)
                        .build())
                .build());

      case BOUND_INSTANCE:
        return CodeBlock.of(
            "$T.$L($L)",
            InstanceFactory.class,
            binding.nullableType().isPresent() ? "createNullable" : "create",
            componentRequirementFields.getExpressionDuringInitialization(
                ComponentRequirement.forBoundInstance(binding), generatedComponentModel.name()));

      case INJECTION:
      case PROVISION:
        {
          List<CodeBlock> arguments =
              Lists.newArrayListWithCapacity(binding.explicitDependencies().size() + 1);
          if (binding.requiresModuleInstance()) {
            arguments.add(
                componentRequirementFields.getExpressionDuringInitialization(
                    ComponentRequirement.forModule(binding.contributingModule().get().asType()),
                    generatedComponentModel.name()));
          }
          arguments.addAll(getBindingDependencyExpressions(binding));

          CodeBlock factoryCreate =
              CodeBlock.of(
                  "$T.create($L)",
                  generatedClassNameForBinding(binding),
                  makeParametersCodeBlock(arguments));

          // If scoping a parameterized factory for an @Inject class, Java 7 cannot always infer the
          // type properly, so cast to a raw framework type before scoping.
          if (binding.bindingKind().equals(INJECTION)
              && binding.unresolved().isPresent()
              && binding.scope().isPresent()) {
            factoryCreate =
                CodeBlock.of("($T) $L", binding.bindingType().frameworkClass(), factoryCreate);
          } else if (!binding.scope().isPresent()) {
            setFieldTypeReplacement(generatedClassNameForBinding(binding));
          }
          return decorateForScope(factoryCreate, binding.scope());
        }

      case COMPONENT_PRODUCTION:
        {
          ComponentRequirement dependency = dependencyForBinding(binding);
          FieldSpec dependencyField =
              FieldSpec.builder(
                      ClassName.get(dependency.typeElement()),
                      dependency.variableName(),
                      PRIVATE,
                      FINAL)
                  .initializer(
                      componentRequirementFields.getExpressionDuringInitialization(
                          dependency, generatedComponentModel.name()))
                  .build();
          // TODO(b/70395982): Explore using a private static type instead of an anonymous class.
          return CodeBlock.of(
              "$L",
              anonymousClassBuilder("")
                  .superclass(producerOf(bindingKeyTypeName))
                  .addField(dependencyField)
                  .addMethod(
                      methodBuilder("get")
                          .addAnnotation(Override.class)
                          .addModifiers(PUBLIC)
                          .returns(listenableFutureOf(bindingKeyTypeName))
                          .addStatement(
                              "return $N.$L()",
                              dependencyField,
                              binding.bindingElement().get().getSimpleName())
                          .build())
                  .build());
        }

      case PRODUCTION:
        {
          List<CodeBlock> arguments =
              Lists.newArrayListWithCapacity(binding.dependencies().size() + 2);
          if (binding.requiresModuleInstance()) {
            arguments.add(
                componentRequirementFields.getExpressionDuringInitialization(
                    ComponentRequirement.forModule(binding.contributingModule().get().asType()),
                    generatedComponentModel.name()));
          }
          arguments.addAll(getBindingDependencyExpressions(binding));

          setFieldTypeReplacement(generatedClassNameForBinding(binding));
          return CodeBlock.of(
              "new $T($L)",
              generatedClassNameForBinding(binding),
              makeParametersCodeBlock(arguments));
        }

      case SYNTHETIC_MULTIBOUND_SET:
        return factoryForSetMultibindingInitialization(binding);

      case SYNTHETIC_MULTIBOUND_MAP:
        return factoryForMapMultibindingInitialization(binding);

      case SYNTHETIC_RELEASABLE_REFERENCE_MANAGER:
        return factoryForSyntheticReleasableReferenceManagerBindingInitialization(binding);

      case SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS:
        return factoryForSyntheticSetOfReleasableReferenceManagersInitialization(binding);

      case SYNTHETIC_OPTIONAL_BINDING:
        return factoryForSyntheticOptionalBindingInitialization(binding);

      case MEMBERS_INJECTOR:
        return factoryForSyntheticMembersInjectorBinding(binding);

      default:
        throw new AssertionError(binding);
    }
  }

  /**
   * Maybe wraps the given creation code block in single/double check or reference releasing
   * providers.
   */
  private CodeBlock decorateForScope(CodeBlock factoryCreate, Optional<Scope> maybeScope) {
    if (!maybeScope.isPresent()) {
      return factoryCreate;
    }
    Scope scope = maybeScope.get();
    if (referenceReleasingManagerFields.requiresReleasableReferences(scope)) {
      return CodeBlock.of(
          "$T.create($L, $L)",
          REFERENCE_RELEASING_PROVIDER,
          factoryCreate,
          referenceReleasingManagerFields.getExpression(scope, generatedComponentModel.name()));
    } else {
      return CodeBlock.of(
          "$T.provider($L)", scope.isReusable() ? SINGLE_CHECK : DOUBLE_CHECK, factoryCreate);
    }
  }

  private ComponentRequirement dependencyForBinding(ContributionBinding binding) {
    return graph
        .componentDescriptor()
        .dependenciesByDependencyMethod()
        .get(binding.bindingElement().get());
  }

  private CodeBlock factoryForSetMultibindingInitialization(ContributionBinding binding) {
    CodeBlock.Builder builder = CodeBlock.builder().add("$T.", setFactoryClassName(binding));
    boolean useRawTypes = useRawType();
    if (!useRawTypes) {
      SetType setType = SetType.from(binding.key());
      builder.add(
          "<$T>",
          setType.elementsAreTypeOf(Produced.class)
              ? setType.unwrappedElementType(Produced.class)
              : setType.elementType());
    }
    int individualProviders = 0;
    int setProviders = 0;
    CodeBlock.Builder builderMethodCalls = CodeBlock.builder();
    for (FrameworkDependency frameworkDependency : binding.frameworkDependencies()) {
      ContributionType contributionType =
          graph.contributionBindings().get(frameworkDependency.key()).contributionType();
      String methodName;
      String methodNameSuffix = frameworkDependency.frameworkClass().getSimpleName();
      switch (contributionType) {
        case SET:
          individualProviders++;
          methodName = "add" + methodNameSuffix;
          break;
        case SET_VALUES:
          setProviders++;
          methodName = "addCollection" + methodNameSuffix;
          break;
        default:
          throw new AssertionError(frameworkDependency + " is not a set multibinding");
      }

      builderMethodCalls.add(
          ".$L($L)",
          methodName,
          potentiallyCast(
              useRawTypes,
              frameworkDependency.frameworkClass(),
              getDependencyExpression(frameworkDependency)));
    }
    builder.add("builder($L, $L)", individualProviders, setProviders);
    builder.add(builderMethodCalls.build());
    return builder.add(".build()").build();
  }

  private CodeBlock factoryForMapMultibindingInitialization(ContributionBinding binding) {
    ImmutableList<FrameworkDependency> frameworkDependencies = binding.frameworkDependencies();

    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    MapType mapType = MapType.from(binding.key().type());
    CodeBlock.Builder builderCall = CodeBlock.builder().add("$T.", mapFactoryClassName(binding));
    boolean useRawTypes = useRawType();
    if (!useRawTypes) {
      // TODO(ronshapiro): either inline this into mapFactoryClassName, or add a
      // mapType.unwrappedValueType() method that doesn't require a framework type
      TypeMirror valueType = mapType.valueType();
      for (Class<?> frameworkClass :
          ImmutableSet.of(Provider.class, Producer.class, Produced.class)) {
        if (mapType.valuesAreTypeOf(frameworkClass)) {
          valueType = mapType.unwrappedValueType(frameworkClass);
          break;
        }
      }
      builderCall.add("<$T, $T>", mapType.keyType(), valueType);
    }

    if (binding.bindingType().equals(BindingType.PROVISION)) {
      builderCall.add("builder($L)", frameworkDependencies.size());
    } else {
      builderCall.add("builder()");
    }
    codeBlocks.add(builderCall.build());

    for (FrameworkDependency frameworkDependency : frameworkDependencies) {
      ContributionBinding contributionBinding =
          graph.contributionBindings().get(frameworkDependency.key()).contributionBinding();
      CodeBlock value =
          potentiallyCast(
              useRawTypes,
              frameworkDependency.frameworkClass(),
              getDependencyExpression(frameworkDependency));
      codeBlocks.add(
          CodeBlock.of(
              ".put($L, $L)",
              getMapKeyExpression(contributionBinding, generatedComponentModel.name()),
              value));
    }
    codeBlocks.add(CodeBlock.of(".build()"));

    return CodeBlocks.concat(codeBlocks.build());
  }

  // TODO(ronshapiro): Use functionality from Expression
  private CodeBlock potentiallyCast(boolean shouldCast, Class<?> classToCast, CodeBlock notCasted) {
    if (!shouldCast) {
      return notCasted;
    }
    return CodeBlock.of("($T) $L", classToCast, notCasted);
  }

  private boolean useRawType() {

    return !isTypeAccessibleFrom(
        resolvedBindings.key().type(), generatedComponentModel.name().packageName());
  }

  /**
   * Initializes the factory for a {@link
   * ContributionBinding.Kind#SYNTHETIC_RELEASABLE_REFERENCE_MANAGER} binding.
   *
   * <p>The {@code get()} method just returns the component field with the {@link
   * dagger.internal.ReferenceReleasingProviderManager} object.
   */
  private CodeBlock factoryForSyntheticReleasableReferenceManagerBindingInitialization(
      ContributionBinding binding) {
    // The scope is the value of the @ForReleasableReferences annotation.
    Scope scope = forReleasableReferencesAnnotationValue(binding.key().qualifier().get());

    CodeBlock managerExpression;
    if (MoreTypes.isTypeOf(TypedReleasableReferenceManager.class, binding.key().type())) {
      /* The key's type is TypedReleasableReferenceManager<M>, so return
       * new TypedReleasableReferenceManager(field, metadata). */
      TypeMirror metadataType =
          MoreTypes.asDeclared(binding.key().type()).getTypeArguments().get(0);
      managerExpression =
          typedReleasableReferenceManagerDecoratorExpression(
              referenceReleasingManagerFields.getExpression(scope, generatedComponentModel.name()),
              scope.releasableReferencesMetadata(metadataType).get());
    } else {
      // The key's type is ReleasableReferenceManager, so return the field as is.
      managerExpression =
          referenceReleasingManagerFields.getExpression(scope, generatedComponentModel.name());
    }

    TypeName keyType = TypeName.get(binding.key().type());
    return CodeBlock.of(
        "$L",
        anonymousClassBuilder("")
            .addSuperinterface(providerOf(keyType))
            .addMethod(
                methodBuilder("get")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(keyType)
                    .addCode("return $L;", managerExpression)
                    .build())
            .build());
  }

  /**
   * Initializes the factory for a {@link
   * ContributionBinding.Kind#SYNTHETIC_RELEASABLE_REFERENCE_MANAGERS} binding.
   *
   * <p>A binding for {@code Set<ReleasableReferenceManager>} will include managers for all
   * reference-releasing scopes. A binding for {@code Set<TypedReleasableReferenceManager<M>>} will
   * include managers for all reference-releasing scopes whose metadata type is {@code M}.
   */
  private CodeBlock factoryForSyntheticSetOfReleasableReferenceManagersInitialization(
      ContributionBinding binding) {
    Key key = binding.key();
    SetType keyType = SetType.from(key);
    ImmutableList.Builder<CodeBlock> managerExpressions = ImmutableList.builder();
    for (Scope scope : graph.scopesRequiringReleasableReferenceManagers()) {
      CodeBlock releasableReferenceManagerExpression =
          referenceReleasingManagerFields.getExpression(scope, generatedComponentModel.name());

      if (keyType.elementsAreTypeOf(ReleasableReferenceManager.class)) {
        managerExpressions.add(releasableReferenceManagerExpression);
      } else if (keyType.elementsAreTypeOf(TypedReleasableReferenceManager.class)) {
        TypeMirror metadataType =
            keyType.unwrappedElementType(TypedReleasableReferenceManager.class);
        Optional<AnnotationMirror> metadata = scope.releasableReferencesMetadata(metadataType);
        if (metadata.isPresent()) {
          managerExpressions.add(
              typedReleasableReferenceManagerDecoratorExpression(
                  releasableReferenceManagerExpression, metadata.get()));
        }
      } else {
        throw new IllegalArgumentException("inappropriate key: " + binding);
      }
    }
    TypeName keyTypeName = TypeName.get(key.type());
    return CodeBlock.of(
        "$L",
        anonymousClassBuilder("")
            .addSuperinterface(providerOf(keyTypeName))
            .addMethod(
                methodBuilder("get")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(keyTypeName)
                    .addCode(
                        "return new $T($T.asList($L));",
                        HashSet.class,
                        Arrays.class,
                        makeParametersCodeBlock(managerExpressions.build()))
                    .build())
            .build());
  }

  /**
   * Returns an expression that evaluates to a {@link TypedReleasableReferenceManagerDecorator} that
   * decorates the {@code managerExpression} to supply {@code metadata}.
   */
  private CodeBlock typedReleasableReferenceManagerDecoratorExpression(
      CodeBlock managerExpression, AnnotationMirror metadata) {
    return CodeBlock.of(
        "new $T<$T>($L, $L)",
        TYPED_RELEASABLE_REFERENCE_MANAGER_DECORATOR,
        metadata.getAnnotationType(),
        managerExpression,
        new AnnotationExpression(metadata).getAnnotationInstanceExpression());
  }

  private Scope forReleasableReferencesAnnotationValue(AnnotationMirror annotation) {
    checkArgument(
        MoreTypes.isTypeOf(ForReleasableReferences.class, annotation.getAnnotationType()));
    return Scopes.scope(
        MoreElements.asType(MoreTypes.asDeclared(getTypeValue(annotation, "value")).asElement()));
  }

  /**
   * Returns an expression that initializes a {@link Provider} or {@link Producer} for an optional
   * binding.
   */
  private CodeBlock factoryForSyntheticOptionalBindingInitialization(ContributionBinding binding) {
    if (binding.explicitDependencies().isEmpty()) {
      verify(
          binding.bindingType().equals(BindingType.PROVISION),
          "Absent optional bindings should be provisions: %s",
          binding);
      return optionalFactories.absentOptionalProvider(binding);
    } else {
      return optionalFactories.presentOptionalFactory(
          binding, getDependencyExpression(getOnlyElement(binding.frameworkDependencies())));
    }
  }

  /**
   * Returns an expression that initializes a {@code Provider<MembersInjector<T>>} for a {@link
   * ContributionBinding.Kind#MEMBERS_INJECTOR} binding.
   */
  private CodeBlock factoryForSyntheticMembersInjectorBinding(ContributionBinding binding) {
    TypeMirror membersInjectedType =
        getOnlyElement(MoreTypes.asDeclared(binding.key().type()).getTypeArguments());

    CodeBlock membersInjector =
        ((ProvisionBinding) binding).injectionSites().isEmpty()
            ? CodeBlock.of("$T.<$T>noOp()", MEMBERS_INJECTORS, membersInjectedType)
            : CodeBlock.of(
                "$T.create($L)",
                membersInjectorNameForType(MoreTypes.asTypeElement(membersInjectedType)),
                makeParametersCodeBlock(getBindingDependencyExpressions(binding)));

    // TODO(ronshapiro): consider adding a MembersInjectorBindingExpression to return this directly
    // (as it's rarely requested as a Provider).
    return CodeBlock.of("$T.create($L)", INSTANCE_FACTORY, membersInjector);
  }
}

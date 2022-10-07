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

package dagger.internal.codegen.processingstep;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedFactoryMethods;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectedConstructors;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.INSTANCE_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static dagger.internal.codegen.langmodel.Accessibility.accessibleTypeName;
import static dagger.internal.codegen.xprocessing.MethodSpecs.overriding;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XMethodElements.hasTypeParameters;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.isPreJava8SourceVersion;
import static dagger.internal.codegen.xprocessing.XTypeElements.typeVariableNames;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.base.SourceFileGenerationException;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedFactoryMetadata;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedParameter;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/** An annotation processor for {@link dagger.assisted.AssistedFactory}-annotated types. */
final class AssistedFactoryProcessingStep extends TypeCheckingProcessingStep<XTypeElement> {
  private final XProcessingEnv processingEnv;
  @SuppressWarnings("HidingField")
  private final XMessager messager;
  private final XFiler filer;
  private final BindingFactory bindingFactory;
  private final MethodSignatureFormatter methodSignatureFormatter;
  @SuppressWarnings("HidingField")
  private final SuperficialValidator superficialValidator;

  @Inject
  AssistedFactoryProcessingStep(
      XProcessingEnv processingEnv,
      XMessager messager,
      XFiler filer,
      BindingFactory bindingFactory,
      MethodSignatureFormatter methodSignatureFormatter,
      SuperficialValidator superficialValidator) {
    this.processingEnv = processingEnv;
    this.messager = messager;
    this.filer = filer;
    this.bindingFactory = bindingFactory;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.superficialValidator = superficialValidator;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.ASSISTED_FACTORY);
  }

  @Override
  protected void process(XTypeElement factory, ImmutableSet<ClassName> annotations) {
    ValidationReport report = new AssistedFactoryValidator().validate(factory);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      try {
        ProvisionBinding binding = bindingFactory.assistedFactoryBinding(factory, Optional.empty());
        new AssistedFactoryImplGenerator().generate(binding);
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }
  }

  private final class AssistedFactoryValidator {
    ValidationReport validate(XTypeElement factory) {
      ValidationReport.Builder report = ValidationReport.about(factory);

      if (!factory.isAbstract()) {
        return report
            .addError(
                "The @AssistedFactory-annotated type must be either an abstract class or "
                    + "interface.",
                factory)
            .build();
      }

      if (factory.isNested() && !factory.isStatic()) {
        report.addError("Nested @AssistedFactory-annotated types must be static. ", factory);
      }

      ImmutableSet<XMethodElement> abstractFactoryMethods = assistedFactoryMethods(factory);

      if (abstractFactoryMethods.isEmpty()) {
        report.addError(
            "The @AssistedFactory-annotated type is missing an abstract, non-default method "
                + "whose return type matches the assisted injection type.",
            factory);
      }

      for (XMethodElement method : abstractFactoryMethods) {
        XType returnType = method.asMemberOf(factory.getType()).getReturnType();
        // The default superficial validation only applies to the @AssistedFactory-annotated
        // element, so we have to manually check the superficial validation  of the @AssistedInject
        // element before using it to ensure it's ready for processing.
        if (isDeclared(returnType)) {
          superficialValidator.throwIfNearestEnclosingTypeNotValid(returnType.getTypeElement());
        }
        if (!isAssistedInjectionType(returnType)) {
          report.addError(
              String.format(
                  "Invalid return type: %s. An assisted factory's abstract method must return a "
                      + "type with an @AssistedInject-annotated constructor.",
                  XTypes.toStableString(returnType)),
              method);
        }
        if (hasTypeParameters(method)) {
          report.addError(
              "@AssistedFactory does not currently support type parameters in the creator "
                  + "method. See https://github.com/google/dagger/issues/2279",
              method);
        }
      }

      if (abstractFactoryMethods.size() > 1) {
        report.addError(
            "The @AssistedFactory-annotated type should contain a single abstract, non-default"
                + " method but found multiple: "
                + abstractFactoryMethods.stream()
                    .map(methodSignatureFormatter::formatWithoutReturnType)
                    .collect(toImmutableList()),
            factory);
      }

      if (!report.build().isClean()) {
        return report.build();
      }

      AssistedFactoryMetadata metadata = AssistedFactoryMetadata.create(factory.getType());

      // Note: We check uniqueness of the @AssistedInject constructor parameters in
      // AssistedInjectProcessingStep. We need to check uniqueness for here too because we may
      // have resolved some type parameters that were not resolved in the @AssistedInject type.
      Set<AssistedParameter> uniqueAssistedParameters = new HashSet<>();
      for (AssistedParameter assistedParameter : metadata.assistedFactoryAssistedParameters()) {
        if (!uniqueAssistedParameters.add(assistedParameter)) {
          report.addError(
              "@AssistedFactory method has duplicate @Assisted types: " + assistedParameter,
              assistedParameter.element());
        }
      }

      if (!ImmutableSet.copyOf(metadata.assistedInjectAssistedParameters())
          .equals(ImmutableSet.copyOf(metadata.assistedFactoryAssistedParameters()))) {
        report.addError(
            String.format(
                "The parameters in the factory method must match the @Assisted parameters in %s."
                    + "\n    Actual: %s#%s(%s)"
                    + "\n  Expected: %s#%s(%s)",
                XTypes.toStableString(metadata.assistedInjectType()),
                metadata.factory().getQualifiedName(),
                getSimpleName(metadata.factoryMethod()),
                metadata.factoryMethod().getParameters().stream()
                    .map(XExecutableParameterElement::getType)
                    .map(XTypes::toStableString)
                    .collect(joining(", ")),
                metadata.factory().getQualifiedName(),
                getSimpleName(metadata.factoryMethod()),
                metadata.assistedInjectAssistedParameters().stream()
                    .map(AssistedParameter::type)
                    .map(XTypes::toStableString)
                    .collect(joining(", "))),
            metadata.factoryMethod());
      }

      return report.build();
    }

    private boolean isAssistedInjectionType(XType type) {
      return isDeclared(type)
          && AssistedInjectionAnnotations.isAssistedInjectionType(type.getTypeElement());
    }
  }

  /** Generates an implementation of the {@link dagger.assisted.AssistedFactory}-annotated class. */
  private final class AssistedFactoryImplGenerator extends SourceFileGenerator<ProvisionBinding> {
    AssistedFactoryImplGenerator() {
      super(filer, processingEnv);
    }

    @Override
    public XElement originatingElement(ProvisionBinding binding) {
      return binding.bindingElement().get();
    }

    // For each @AssistedFactory-annotated type, we generates a class named "*_Impl" that implements
    // that type.
    //
    // Note that this class internally delegates to the @AssistedInject generated class, which
    // contains the actual implementation logic for creating the @AssistedInject type. The reason we
    // need both of these generated classes is because while the @AssistedInject generated class
    // knows how to create the @AssistedInject type, it doesn't know about all of the
    // @AssistedFactory interfaces that it needs to extend when it's generated. Thus, the role of
    // the @AssistedFactory generated class is purely to implement the @AssistedFactory type.
    // Furthermore, while we could have put all of the logic into the @AssistedFactory generated
    // class and not generate the @AssistedInject generated class, having the @AssistedInject
    // generated class ensures we have proper accessibility to the @AssistedInject type, and reduces
    // duplicate logic if there are multiple @AssistedFactory types for the same @AssistedInject
    // type.
    //
    // Example:
    // public class FooFactory_Impl implements FooFactory {
    //   private final Foo_Factory delegateFactory;
    //
    //   FooFactory_Impl(Foo_Factory delegateFactory) {
    //     this.delegateFactory = delegateFactory;
    //   }
    //
    //   @Override
    //   public Foo createFoo(AssistedDep assistedDep) {
    //     return delegateFactory.get(assistedDep);
    //   }
    //
    //   public static Provider<FooFactory> create(Foo_Factory delegateFactory) {
    //     return InstanceFactory.create(new FooFactory_Impl(delegateFactory));
    //   }
    // }
    @Override
    public ImmutableList<TypeSpec.Builder> topLevelTypes(ProvisionBinding binding) {
      XTypeElement factory = asTypeElement(binding.bindingElement().get());

      ClassName name = generatedClassNameForBinding(binding);
      TypeSpec.Builder builder =
          TypeSpec.classBuilder(name)
              .addModifiers(PUBLIC, FINAL)
              .addTypeVariables(typeVariableNames(factory));

      if (factory.isInterface()) {
        builder.addSuperinterface(factory.getType().getTypeName());
      } else {
        builder.superclass(factory.getType().getTypeName());
      }

      AssistedFactoryMetadata metadata = AssistedFactoryMetadata.create(factory.getType());
      ParameterSpec delegateFactoryParam =
          ParameterSpec.builder(
                  delegateFactoryTypeName(metadata.assistedInjectType()), "delegateFactory")
              .build();
      builder
          .addField(
              FieldSpec.builder(delegateFactoryParam.type, delegateFactoryParam.name)
                  .addModifiers(PRIVATE, FINAL)
                  .build())
          .addMethod(
              MethodSpec.constructorBuilder()
                  .addParameter(delegateFactoryParam)
                  .addStatement("this.$1N = $1N", delegateFactoryParam)
                  .build())
          .addMethod(
              overriding(metadata.factoryMethod(), metadata.factoryType())
                  .addStatement(
                      "return $N.get($L)",
                      delegateFactoryParam,
                      // Use the order of the parameters from the @AssistedInject constructor but
                      // use the parameter names of the @AssistedFactory method.
                      metadata.assistedInjectAssistedParameters().stream()
                          .map(metadata.assistedFactoryAssistedParametersMap()::get)
                          .map(param -> CodeBlock.of("$L", getSimpleName(param)))
                          .collect(toParametersCodeBlock()))
                  .build())
          .addMethod(
              MethodSpec.methodBuilder("create")
                  .addModifiers(PUBLIC, STATIC)
                  .addParameter(delegateFactoryParam)
                  .addTypeVariables(typeVariableNames(metadata.assistedInjectElement()))
                  .returns(providerOf(factory.getType().getTypeName()))
                  .addStatement(
                      "return $T.$Lcreate(new $T($N))",
                      INSTANCE_FACTORY,
                      // Java 7 type inference requires the method call provide the exact type here.
                      isPreJava8SourceVersion(processingEnv)
                          ? CodeBlock.of(
                              "<$T>",
                              accessibleTypeName(metadata.factoryType(), name, processingEnv))
                          : CodeBlock.of(""),
                      name,
                      delegateFactoryParam)
                  .build());
      return ImmutableList.of(builder);
    }

    /** Returns the generated factory {@link TypeName type} for an @AssistedInject constructor. */
    private TypeName delegateFactoryTypeName(XType assistedInjectType) {
      // The name of the generated factory for the assisted inject type,
      // e.g. an @AssistedInject Foo(...) {...} constructor will generate a Foo_Factory class.
      ClassName generatedFactoryClassName =
          generatedClassNameForBinding(
              bindingFactory.injectionBinding(
                  getOnlyElement(assistedInjectedConstructors(assistedInjectType.getTypeElement())),
                  Optional.empty()));

      // Return the factory type resolved with the same type parameters as the assisted inject type.
      return assistedInjectType.getTypeArguments().isEmpty()
          ? generatedFactoryClassName
          : ParameterizedTypeName.get(
              generatedFactoryClassName,
              assistedInjectType.getTypeArguments().stream()
                  .map(XType::getTypeName)
                  .collect(toImmutableList())
                  .toArray(new TypeName[0]));
    }
  }
}

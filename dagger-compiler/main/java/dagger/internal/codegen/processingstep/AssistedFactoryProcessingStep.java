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
import static dagger.internal.codegen.xprocessing.Accessibility.accessibleTypeName;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static dagger.internal.codegen.xprocessing.XFunSpecs.overriding;
import static dagger.internal.codegen.xprocessing.XMethodElements.hasTypeParameters;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.isPreJava8SourceVersion;
import static dagger.internal.codegen.xprocessing.XTypeElements.typeVariableNames;
import static dagger.internal.codegen.xprocessing.XTypeNames.daggerProviderOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.javaxProviderOf;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.XTypeSpec;
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
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.AssistedFactoryBinding;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedFactoryMetadata;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedParameter;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.xprocessing.XPropertySpecs;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
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
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.of(XTypeNames.ASSISTED_FACTORY);
  }

  @Override
  protected void process(XTypeElement factory, ImmutableSet<XClassName> annotations) {
    ValidationReport report = new AssistedFactoryValidator().validate(factory);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      new AssistedFactoryImplGenerator()
          .generate(bindingFactory.assistedFactoryBinding(factory, Optional.empty()));
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
  private final class AssistedFactoryImplGenerator
      extends SourceFileGenerator<AssistedFactoryBinding> {
    AssistedFactoryImplGenerator() {
      super(filer, processingEnv);
    }

    @Override
    public XElement originatingElement(AssistedFactoryBinding binding) {
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
    public ImmutableList<XTypeSpec> topLevelTypes(AssistedFactoryBinding binding) {
      XTypeElement factory = asTypeElement(binding.bindingElement().get());

      XClassName generatedClassName = generatedClassNameForBinding(binding);
      XTypeSpecs.Builder builder =
          XTypeSpecs.classBuilder(generatedClassName)
              .addModifiers(PUBLIC, FINAL)
              .addTypeVariableNames(typeVariableNames(factory));

      if (factory.isInterface()) {
        builder.addSuperinterface(factory.getType().asTypeName());
      } else {
        builder.superclass(factory.getType().asTypeName());
      }

      AssistedFactoryMetadata metadata = AssistedFactoryMetadata.create(factory.getType());
      String delegateName = "delegateFactory";
      XTypeName delegateType = delegateFactoryTypeName(metadata.assistedInjectType());
      builder
          .addProperty(XPropertySpecs.of(delegateName, delegateType, PRIVATE, FINAL))
          .addFunction(
              constructorBuilder()
                  .addParameter(delegateName, delegateType)
                  .addStatement("this.%1N = %1N", delegateName)
                  .build())
          .addFunction(createMethod(metadata, delegateName))
          // In a future release, we should delete this javax method. This will still be a breaking
          // change, but keeping compatibility for a while should reduce the likelihood of breakages
          // as it would require components built at much older versions using factories built at
          // newer versions to break.
          .addFunction(
              staticCreateMethod(
                  /* methodName= */"create",
                  /* returnType= */ javaxProviderOf(accessibleFactoryTypeName(factory)),
                  generatedClassName,
                  metadata,
                  delegateName,
                  delegateType))
          // Normally we would have called this just "create", but because of backwards
          // compatibility we can't have two methods with the same name/arguments returning
          // different Provider types.
          .addFunction(
              staticCreateMethod(
                  /* methodName= */ "createFactoryProvider",
                  /* returnType= */ daggerProviderOf(accessibleFactoryTypeName(factory)),
                  generatedClassName,
                  metadata,
                  delegateName,
                  delegateType));
      return ImmutableList.of(builder.build());
    }

    private XFunSpec staticCreateMethod(
        String methodName,
        XTypeName returnType,
        XClassName generatedClassName,
        AssistedFactoryMetadata metadata,
        String delegateName,
        XTypeName delegateType) {
      return methodBuilder(methodName)
          .addModifiers(PUBLIC, STATIC)
          .addParameter(delegateName, delegateType)
          .addTypeVariableNames(typeVariableNames(metadata.assistedInjectElement()))
          .returns(returnType)
          .addStatement(
              "return %T.%Lcreate(%L)",
              XTypeNames.INSTANCE_FACTORY,
              // Java 7 type inference requires the method call provide the exact type here.
              isPreJava8SourceVersion(processingEnv)
                  ? XCodeBlock.of(
                      "<%T>",
                      accessibleTypeName(metadata.factoryType(), generatedClassName, processingEnv))
                  : XCodeBlock.of(""),
              XCodeBlock.ofNewInstance(generatedClassName, "%N", delegateName))
          .build();
    }

    private XTypeName accessibleFactoryTypeName(XTypeElement factory) {
      return factory.getType().asTypeName();
    }

    // @Override
    // public Foo createFoo(AssistedDep assistedDep) {
    //   return delegateFactory.get(assistedDep);
    // }
    private XFunSpec createMethod(AssistedFactoryMetadata metadata, String delegateName) {
      XCodeBlock instance =
          XCodeBlock.of(
              "%N.get(%L)",
              delegateName,
              // Use the order of the parameters from the @AssistedInject constructor but
              // use the parameter names of the @AssistedFactory method.
              metadata.assistedInjectAssistedParameters().stream()
                  .map(metadata.assistedFactoryAssistedParametersMap()::get)
                  .map(param -> XCodeBlock.of("%N", param.getJvmName()))
                  .collect(toParametersCodeBlock()));
      return overriding(metadata.factoryMethod(), metadata.factoryType(), compilerOptions)
          .addStatement("return %L", instance)
          .build();
    }

    /** Returns the generated factory {@link XTypeName type} for an @AssistedInject constructor. */
    private XTypeName delegateFactoryTypeName(XType assistedInjectType) {
      AssistedInjectionBinding binding =
          bindingFactory.assistedInjectionBinding(
              getOnlyElement(assistedInjectedConstructors(assistedInjectType.getTypeElement())),
              Optional.empty());

      // The name of the generated factory for the assisted inject type,
      // e.g. an @AssistedInject Foo(...) {...} constructor will generate a Foo_Factory class.
      XClassName generatedFactoryClassName = generatedClassNameForBinding(binding);

      // Return the factory type resolved with the same type parameters as the assisted inject type.
      return assistedInjectType.getTypeArguments().isEmpty()
          ? generatedFactoryClassName
          : generatedFactoryClassName.parametrizedBy(
                assistedInjectType.getTypeArguments().stream()
                    .map(XType::asTypeName)
                    .collect(toImmutableList())
                    .toArray(new XTypeName[0]));
    }
  }
}

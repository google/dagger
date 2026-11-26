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
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
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
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.codegen.XCodeBlock;
import androidx.room3.compiler.codegen.XFunSpec;
import androidx.room3.compiler.codegen.XTypeName;
import androidx.room3.compiler.codegen.XTypeSpec;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XExecutableParameterElement;
import androidx.room3.compiler.processing.XFiler;
import androidx.room3.compiler.processing.XMessager;
import androidx.room3.compiler.processing.XMethodElement;
import androidx.room3.compiler.processing.XProcessingEnv;
import androidx.room3.compiler.processing.XType;
import androidx.room3.compiler.processing.XTypeArgument;
import androidx.room3.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.ValidationReport;
import dagger.internal.codegen.binding.AssistedFactoryBinding;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedFactoryMetadata;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedParameter;
import dagger.internal.codegen.binding.AssistedInjectionBinding;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.validation.InjectValidator;
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
  private final DaggerSuperficialValidation superficialValidation;
  private final InjectValidator injectValidator;

  @Inject
  AssistedFactoryProcessingStep(
      XProcessingEnv processingEnv,
      XMessager messager,
      XFiler filer,
      BindingFactory bindingFactory,
      MethodSignatureFormatter methodSignatureFormatter,
      DaggerSuperficialValidation superficialValidation,
      InjectValidator injectValidator) {
    this.processingEnv = processingEnv;
    this.messager = messager;
    this.filer = filer;
    this.bindingFactory = bindingFactory;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.superficialValidation = superficialValidation;
    this.injectValidator = injectValidator;
  }

  @Override
  protected boolean requiresPreValidation() {
    return false;
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
      AssistedFactoryBinding binding =
          bindingFactory.assistedFactoryBinding(factory, Optional.empty());
      new AssistedFactoryImplGenerator().generate(binding);
      if (binding.assistedFactoryMetadata().needsToGenerateProxy()) {
        new AssistedFactoryProxyGenerator().generate(binding);
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
        superficialValidation.validateElement(method);
        XType returnType = method.asMemberOf(factory.getType()).getReturnType();
        // The default superficial validation only applies to the @AssistedFactory-annotated
        // element, so we have to manually check the superficial validation  of the @AssistedInject
        // element before using it to ensure it's ready for processing.
        if (isDeclared(returnType)) {
          ValidationReport injectReport = injectValidator.validate(returnType.getTypeElement());
          if (!injectReport.isClean()) {
            report.addSubreport(injectReport);
          }
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

    /**
     * Generates a class named {@code *_Impl} that implements each
     * {@code @AssistedFactory}-annotated type.
     *
     * <p>Note that this class internally delegates to the {@code @AssistedInject} generated class,
     * which contains the actual implementation logic for creating the {@code @AssistedInject} type.
     * The reason we need both of these generated classes is because while the
     * {@code @AssistedInject} generated class knows how to create the {@code @AssistedInject} type,
     * it doesn't know about all of the {@code @AssistedFactory} interfaces that it needs to extend
     * when it's generated. Thus, the role of the {@code @AssistedFactory} generated class is purely
     * to implement the {@code @AssistedFactory} type. Furthermore, while we could have put all of
     * the logic into the {@code @AssistedFactory} generated class and not generate the
     * {@code @AssistedInject} generated class, having the {@code @AssistedInject} generated class
     * ensures we have proper accessibility to the {@code @AssistedInject} type, and reduces
     * duplicate logic if there are multiple {@code @AssistedFactory} types for the same
     * {@code @AssistedInject} type.
     *
     * <p>Example:
     *
     * <pre><code>
     * public class FooFactory_Impl implements FooFactory {
     *   private final Foo_Factory delegateFactory;
     *
     *   FooFactory_Impl(Foo_Factory delegateFactory) {
     *     this.delegateFactory = delegateFactory;
     *   }
     *
     *   {@literal @}Override
     *   public Foo createFoo(AssistedDep assistedDep) {
     *     return delegateFactory.get(assistedDep);
     *   }
     *
     *   public static Provider&lt;FooFactory&gt; create(Foo_Factory delegateFactory) {
     *     return InstanceFactory.create(new FooFactory_Impl(delegateFactory));
     *   }
     * }
     * </code></pre>
     */
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

      AssistedFactoryMetadata metadata = binding.assistedFactoryMetadata();
      AssistedInjectionBinding assistedInjectionBinding =
          bindingFactory.assistedInjectionBinding(
              getOnlyElement(assistedInjectedConstructors(metadata.assistedInjectElement())),
              Optional.empty());
      String delegateName = "delegateFactory";
      XTypeName delegateType =
          delegateFactoryTypeName(assistedInjectionBinding, metadata.assistedInjectType());
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
                  /* methodName= */ "create",
                  /* returnType= */ javaxProviderOf(accessibleFactoryTypeName(factory)),
                  generatedClassName,
                  metadata,
                  assistedInjectionBinding,
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
                  assistedInjectionBinding,
                  delegateName,
                  delegateType));
      return ImmutableList.of(builder.build());
    }

    private XFunSpec staticCreateMethod(
        String methodName,
        XTypeName returnType,
        XClassName generatedClassName,
        AssistedFactoryMetadata metadata,
        AssistedInjectionBinding assistedInjectionBinding,
        String delegateName,
        XTypeName delegateType) {
      return methodBuilder(methodName)
          .addModifiers(PUBLIC, STATIC)
          .addParameter(delegateName, delegateType)
          .addTypeVariableNames(bindingTypeElementTypeVariableNames(assistedInjectionBinding))
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

    /**
     * Generates the factory method implementation, e.g.:
     *
     * <pre><code>
     * {@literal @}Override
     * public Foo createFoo(AssistedDep assistedDep) {
     *   return delegateFactory.get(assistedDep);
     * }
     * </code></pre>
     */
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
    private XTypeName delegateFactoryTypeName(
        AssistedInjectionBinding assistedInjectionBinding, XType assistedInjectType) {
      // The name of the generated factory for the assisted inject type,
      // e.g. an @AssistedInject Foo(...) {...} constructor will generate a Foo_Factory class.
      XClassName generatedFactoryClassName = generatedClassNameForBinding(assistedInjectionBinding);

      // Return the factory type resolved with the same type parameters as the assisted inject type.
      return bindingTypeElementTypeVariableNames(assistedInjectionBinding).isEmpty()
              || assistedInjectType.getTypeArguments().isEmpty()
          ? generatedFactoryClassName
          : generatedFactoryClassName.parametrizedBy(
              assistedInjectType.getTypeArguments().stream()
                  .map(XTypeArgument::asTypeName)
                  .collect(toImmutableList())
                  .toArray(new XTypeName[0]));
    }
  }

  /**
   * Generates a proxy interface of the {@link dagger.assisted.AssistedFactory}-annotated class to
   * bypass visibility issues.
   */
  private final class AssistedFactoryProxyGenerator
      extends SourceFileGenerator<AssistedFactoryBinding> {
    private static final String CREATE_FACTORY_METHOD_NAME = "createFactory";

    AssistedFactoryProxyGenerator() {
      super(filer, processingEnv);
    }

    @Override
    public XElement originatingElement(AssistedFactoryBinding binding) {
      return binding.bindingElement().get();
    }

    /**
     * Generates a proxy interface for the {@code @AssistedFactory}-annotated class to bypass
     * visibility issues.
     *
     * <p>Example:
     *
     * <pre><code>
     * public interface FooFactory_Proxy {
     *   Foo create(String publicParam, Object packagePrivateParam);
     *
     *   public static Object createFactory(FooFactory_Proxy proxy) {
     *     return new FooFactory() {
     *       {@literal @}Override
     *       public Foo create(String publicParam, PackagePrivateParam packagePrivateParam) {
     *         return (Foo) proxy.create(publicParam, packagePrivateParam);
     *       }
     *     };
     *   }
     * }
     * </code></pre>
     */
    @Override
    public ImmutableList<XTypeSpec> topLevelTypes(AssistedFactoryBinding binding) {
      AssistedFactoryMetadata metadata = binding.assistedFactoryMetadata();
      return ImmutableList.of(
          XTypeSpecs.functionalInterfaceBuilder(metadata.proxyClassName())
              .addModifiers(PUBLIC)
              .addFunction(abstractProxyMethod(metadata))
              .addFunctions(createFactoryMethods(metadata))
              .build());
    }

    /**
     * Generates the abstract proxy method, e.g.:
     *
     * <pre>{@code
     * public abstract Foo create(String publicParam, Object packagePrivateParam);
     * }</pre>
     */
    private XFunSpec abstractProxyMethod(AssistedFactoryMetadata metadata) {
      return methodBuilder(metadata.proxyMethodName())
          .addModifiers(PUBLIC, ABSTRACT)
          .returns(metadata.proxyReturnType())
          .addParameters(metadata.proxyParameterSpecs(compilerOptions))
          .build();
    }

    /**
     * Generates the static factory creation method(s), e.g.:
     *
     * <pre>{@code
     * public static <T> Object createFactory(FooFactory_Proxy proxy) {
     *   return new FooFactory<T>() { ... };
     * }
     * }</pre>
     *
     */
    private ImmutableList<XFunSpec> createFactoryMethods(AssistedFactoryMetadata metadata) {
      // Copy the factory's type variables (with their bounds) onto the method rather than resolving
      // them to concrete types. Some bounds (e.g. self-referencing or intersection bounds) have no
      // accessible concrete instantiation.
      ImmutableList<XTypeName> typeVariableNames = typeVariableNames(metadata.factory());

      return ImmutableList.of(
          methodBuilder(CREATE_FACTORY_METHOD_NAME)
              .addModifiers(PUBLIC, STATIC)
              .addTypeVariableNames(typeVariableNames)
              .returns(XTypeName.ANY_OBJECT)
              .addParameter(metadata.proxyInstanceName(), metadata.proxyClassName())
              .addStatement("return %L", anonymousFactoryImpl(metadata))
              .build());
    }

    /**
     * Generates the anonymous factory implementation, e.g.:
     *
     * <pre><code>
     * new FooFactory&lt;T&gt;() {
     *   {@literal @}Override
     *   public Foo&lt;T&gt; create(T publicParam, PackagePrivateParam packagePrivateParam) {
     *     return (Foo&lt;T&gt;) proxy.create(publicParam, packagePrivateParam);
     *   }
     * }
     * </code></pre>
     */
    private XTypeSpec anonymousFactoryImpl(AssistedFactoryMetadata metadata) {
      XTypeName factoryTypeName = metadata.factoryType().asTypeName();
      XTypeSpecs.Builder builder = XTypeSpecs.anonymousClassBuilder();
      if (metadata.factory().isInterface()) {
        builder.addSuperinterface(factoryTypeName);
      } else {
        builder.superclass(factoryTypeName);
      }
      return builder.addFunction(factoryMethodImpl(metadata)).build();
    }

    /**
     * Generates the overridden factory method on the anonymous factory, e.g.:
     *
     * <pre><code>
     * {@literal @}Override
     * public Foo create(String publicParam, PackagePrivateParam packagePrivateParam) {
     *   return (Foo) proxy.create(publicParam, packagePrivateParam);
     * }
     * </code></pre>
     */
    private XFunSpec factoryMethodImpl(AssistedFactoryMetadata metadata) {
      XTypeName returnTypeName = metadata.factoryMethodType().getReturnType().asTypeName();
      XCodeBlock args =
          metadata.factoryMethod().getParameters().stream()
              .map(param -> XCodeBlock.of("%N", param.getJvmName()))
              .collect(toParametersCodeBlock());
      return overriding(metadata.factoryMethod(), metadata.factoryType(), compilerOptions)
          .addStatement(
              "return %L",
              returnTypeName.equals(metadata.proxyReturnType())
                  ? XCodeBlock.of(
                      "%N.%N(%L)", metadata.proxyInstanceName(), metadata.proxyMethodName(), args)
                  : XCodeBlock.ofCast(
                      returnTypeName,
                      XCodeBlock.of(
                          "%N.%N(%L)",
                          metadata.proxyInstanceName(), metadata.proxyMethodName(), args)))
          .build();
    }
  }
}

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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectedConstructors;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.INSTANCE_FACTORY;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.MoreElements;
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
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.internal.codegen.base.SourceFileGenerationException;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.BindingFactory;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** An annotation processor for {@link dagger.assisted.AssistedFactory}-annotated types. */
final class AssistedFactoryProcessingStep extends TypeCheckingProcessingStep<TypeElement> {
  private final Messager messager;
  private final Filer filer;
  private final SourceVersion sourceVersion;
  private final DaggerElements elements;
  private final DaggerTypes types;
  private final BindingFactory bindingFactory;

  @Inject
  AssistedFactoryProcessingStep(
      Messager messager,
      Filer filer,
      SourceVersion sourceVersion,
      DaggerElements elements,
      DaggerTypes types,
      BindingFactory bindingFactory) {
    super(MoreElements::asType);
    this.messager = messager;
    this.filer = filer;
    this.sourceVersion = sourceVersion;
    this.elements = elements;
    this.types = types;
    this.bindingFactory = bindingFactory;
  }

  @Override
  public ImmutableSet<Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(AssistedFactory.class);
  }

  @Override
  protected void process(
      TypeElement factory, ImmutableSet<Class<? extends Annotation>> annotations) {
    ValidationReport<TypeElement> report = new AssistedFactoryValidator().validate(factory);
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
    ValidationReport<TypeElement> validate(TypeElement factory) {
      ValidationReport.Builder<TypeElement> report = ValidationReport.about(factory);

      if (!factory.getModifiers().contains(ABSTRACT)) {
        return report
            .addError(
                "The @AssistedFactory-annotated type must be either an abstract class or "
                    + "interface.",
                factory)
            .build();
      }

      if (factory.getNestingKind().isNested() && !factory.getModifiers().contains(STATIC)) {
        report.addError("Nested @AssistedFactory-annotated types must be static. ", factory);
      }

      ImmutableSet<ExecutableElement> abstractFactoryMethods =
          AssistedInjectionAnnotations.assistedFactoryMethods(factory, elements, types);

      if (abstractFactoryMethods.isEmpty()) {
        report.addError(
            "The @AssistedFactory-annotated type is missing an abstract, non-default method "
                + "whose return type matches the assisted injection type.",
            factory);
      }

      for (ExecutableElement method : abstractFactoryMethods) {
        ExecutableType methodType = types.resolveExecutableType(method, factory.asType());
        if (!isAssistedInjectionType(methodType.getReturnType())) {
          report.addError(
              String.format(
                  "Invalid return type: %s. An assisted factory's abstract method must return a "
                      + "type with an @AssistedInject-annotated constructor.",
                  methodType.getReturnType()),
              method);
        }
        if (!method.getTypeParameters().isEmpty()) {
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
                + abstractFactoryMethods,
            factory);
      }

      if (!report.build().isClean()) {
        return report.build();
      }

      // Given the previous checks, we can be sure we have a
      // single factory method with a valid return type.
      ExecutableElement factoryMethod = getOnlyElement(abstractFactoryMethods);
      ExecutableType factoryMethodType =
          types.resolveExecutableType(factoryMethod, factory.asType());
      DeclaredType returnType = asDeclared(factoryMethodType.getReturnType());

      ImmutableList<TypeMirror> assistedParameterTypes =
          assistedInjectConstructorParameterMap(returnType).entrySet().stream()
              .filter(e -> isAnnotationPresent(e.getKey(), Assisted.class))
              .map(Map.Entry::getValue)
              .collect(toImmutableList());

      ImmutableList<TypeMirror> factoryMethodParameterTypes =
          ImmutableList.copyOf(factoryMethodType.getParameterTypes());

      if (!typesAssignableTo(factoryMethodParameterTypes, assistedParameterTypes)) {
        report.addError(
            String.format(
                "The parameters of the factory method must be assignable to the list of "
                    + "@Assisted parameters in %s."
                    + "\n      Actual: %s#%s"
                    + "\n    Expected: %s#%s(%s)",
                returnType,
                factory.getQualifiedName(),
                factoryMethod,
                factory.getQualifiedName(),
                factoryMethod.getSimpleName(),
                assistedParameterTypes.stream().map(Object::toString).collect(joining(", "))),
            factoryMethod);
      }

      return report.build();
    }

    private boolean isAssistedInjectionType(TypeMirror type) {
      return type.getKind() == TypeKind.DECLARED
          && AssistedInjectionAnnotations.isAssistedInjectionType(asTypeElement(type));
    }

    /** Returns {@code true} if {@code types1} are all assignable to {@code types2}. */
    private boolean typesAssignableTo(
        ImmutableList<TypeMirror> types1, ImmutableList<TypeMirror> types2) {
      if (types1.size() != types2.size()) {
        return false;
      }

      for (int i = 0; i < types1.size(); i++) {
        if (!types.isAssignable(types1.get(i), types2.get(i))) {
          return false;
        }
      }
      return true;
    }

    private ImmutableMap<VariableElement, TypeMirror> assistedInjectConstructorParameterMap(
        DeclaredType assistedType) {
      // We keep track of the constructor both as an ExecutableElement to access @Assisted
      // parameters and as an ExecutableType to access the resolved parameter types.
      ExecutableElement assistedConstructor =
          getOnlyElement(assistedInjectedConstructors(asTypeElement(assistedType)));
      ExecutableType assistedConstructorType =
          asExecutable(types.asMemberOf(assistedType, assistedConstructor));

      ImmutableMap.Builder<VariableElement, TypeMirror> builder = ImmutableMap.builder();
      for (int i = 0; i < assistedConstructor.getParameters().size(); i++) {
        builder.put(
            assistedConstructor.getParameters().get(i),
            assistedConstructorType.getParameterTypes().get(i));
      }
      return builder.build();
    }
  }

  /** Generates an implementation of the {@link dagger.assisted.AssistedFactory}-annotated class. */
  private final class AssistedFactoryImplGenerator extends SourceFileGenerator<ProvisionBinding> {
    AssistedFactoryImplGenerator() {
      super(filer, elements, sourceVersion);
    }

    @Override
    public ClassName nameGeneratedType(ProvisionBinding binding) {
      return generatedClassNameForBinding(binding);
    }

    @Override
    public Element originatingElement(ProvisionBinding binding) {
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
    public Optional<TypeSpec.Builder> write(ProvisionBinding binding) {
      TypeElement factory = asType(binding.bindingElement().get());

      ClassName name = nameGeneratedType(binding);
      TypeSpec.Builder builder =
          TypeSpec.classBuilder(name)
              .addModifiers(PUBLIC, FINAL)
              .addTypeVariables(
                  factory.getTypeParameters().stream()
                      .map(TypeVariableName::get)
                      .collect(toImmutableList()));

      if (factory.getKind() == ElementKind.INTERFACE) {
        builder.addSuperinterface(factory.asType());
      } else {
        builder.superclass(factory.asType());
      }

      // Define all types associated with the @AssistedFactory before generating the implementation.
      DeclaredType factoryType = asDeclared(binding.key().type());
      ExecutableElement factoryMethod =
          AssistedInjectionAnnotations.assistedFactoryMethod(factory, elements, types);
      ExecutableType factoryMethodType = asExecutable(types.asMemberOf(factoryType, factoryMethod));
      DeclaredType returnType = asDeclared(factoryMethodType.getReturnType());
      TypeElement returnElement = asTypeElement(returnType);
      ParameterSpec delegateFactoryParam =
          ParameterSpec.builder(delegateFactoryTypeName(returnType), "delegateFactory").build();
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
              MethodSpec.overriding(factoryMethod, asDeclared(factory.asType()), types)
                  .addStatement(
                      "return $N.get($L)",
                      delegateFactoryParam,
                      factoryMethod.getParameters().stream()
                          .map(param -> CodeBlock.of("$L", param.getSimpleName()))
                          .collect(toParametersCodeBlock()))
                  .build())
          .addMethod(
              MethodSpec.methodBuilder("create")
                  .addModifiers(PUBLIC, STATIC)
                  .addParameter(delegateFactoryParam)
                  .addTypeVariables(
                      returnElement.getTypeParameters().stream()
                          .map(TypeVariableName::get)
                          .collect(toImmutableList()))
                  .returns(providerOf(TypeName.get(factory.asType())))
                  .addStatement(
                      "return $T.$Lcreate(new $T($N))",
                      INSTANCE_FACTORY,
                      // Java 7 type inference requires the method call provide the exact type here.
                      sourceVersion.compareTo(SourceVersion.RELEASE_7) <= 0
                          ? CodeBlock.of("<$T>", types.accessibleType(factoryType, name))
                          : CodeBlock.of(""),
                      name,
                      delegateFactoryParam)
                  .build());
      return Optional.of(builder);
    }

    /** Returns the generated factory {@link TypeName type} for an @AssistedInject constructor. */
    private TypeName delegateFactoryTypeName(DeclaredType assistedInjectType) {
      // The name of the generated factory for the assisted inject type,
      // e.g. an @AssistedInject Foo(...) {...} constructor will generate a Foo_Factory class.
      ClassName generatedFactoryClassName =
          generatedClassNameForBinding(
              bindingFactory.injectionBinding(
                  getOnlyElement(assistedInjectedConstructors(asTypeElement(assistedInjectType))),
                  Optional.empty()));

      // Return the factory type resolved with the same type parameters as the assisted inject type.
      return assistedInjectType.getTypeArguments().isEmpty()
          ? generatedFactoryClassName
          : ParameterizedTypeName.get(
              generatedFactoryClassName,
              assistedInjectType.getTypeArguments().stream()
                  .map(TypeName::get)
                  .collect(toImmutableList())
                  .toArray(new TypeName[0]));
    }
  }
}

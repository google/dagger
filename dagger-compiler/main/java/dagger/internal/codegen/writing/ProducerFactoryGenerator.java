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

package dagger.internal.codegen.writing;

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.FUTURE_RETURN_VALUE_IGNORED;
import static dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.javapoet.CodeBlocks.parameterNames;
import static dagger.internal.codegen.writing.GwtCompatibility.gwtIncompatibleAnnotation;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypeNames.isFutureType;
import static dagger.internal.codegen.xprocessing.XTypeNames.listOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.listenableFutureOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.producedOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.ProductionBinding;
import dagger.internal.codegen.binding.SourceFiles;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.AnnotationSpecs;
import dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Generates {@code Producer} implementations from {@link ProductionBinding} instances. */
public final class ProducerFactoryGenerator extends SourceFileGenerator<ProductionBinding> {
  private final CompilerOptions compilerOptions;
  private final SourceFiles sourceFiles;

  @Inject
  ProducerFactoryGenerator(
      XFiler filer,
      XProcessingEnv processingEnv,
      CompilerOptions compilerOptions,
      SourceFiles sourceFiles) {
    super(filer, processingEnv);
    this.compilerOptions = compilerOptions;
    this.sourceFiles = sourceFiles;
  }

  @Override
  public XElement originatingElement(ProductionBinding binding) {
    // we only create factories for bindings that have a binding element
    return binding.bindingElement().get();
  }

  @Override
  public ImmutableList<TypeSpec.Builder> topLevelTypes(ProductionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkArgument(!binding.unresolved().isPresent());
    checkArgument(binding.bindingElement().isPresent());

    FactoryFields factoryFields = FactoryFields.create(binding);
    TypeSpec.Builder factoryBuilder =
        classBuilder(toJavaPoet(generatedClassNameForBinding(binding)))
            .superclass(
                ParameterizedTypeName.get(
                    toJavaPoet(XTypeNames.ABSTRACT_PRODUCES_METHOD_PRODUCER),
                    callProducesMethodParameter(binding).type,
                    toJavaPoet(binding.contributedType().asTypeName())))
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariables(
                bindingTypeElementTypeVariableNames(binding).stream()
                    .map(typeName -> (TypeVariableName) toJavaPoet(typeName))
                    .collect(toImmutableList()))
            .addFields(
                factoryFields.getAll().stream()
                    // The executor and monitor fields are owned by the superclass so they are not
                    // included as fields in the generated factory subclass.
                    .filter(field -> !field.equals(factoryFields.executorField))
                    .filter(field -> !field.equals(factoryFields.monitorField))
                    .collect(toImmutableList()))
            .addMethod(constructorMethod(binding, factoryFields))
            .addMethod(staticCreateMethod(binding, factoryFields))
            .addMethod(collectDependenciesMethod(binding, factoryFields))
            .addMethod(callProducesMethod(binding, factoryFields));

    gwtIncompatibleAnnotation(binding).ifPresent(factoryBuilder::addAnnotation);

    return ImmutableList.of(factoryBuilder);
  }

  // private FooModule_ProducesFooFactory(
  //     FooModule module,
  //     Provider<Executor> executorProvider,
  //     Provider<ProductionComponentMonitor> productionComponentMonitorProvider,
  //     Producer<Foo> fooProducer,
  //     Producer<Bar> barProducer) {
  //   super(
  //       productionComponentMonitorProvider,
  //       ProducerToken.create(FooModule_ProducesFooFactory.class),
  //       executorProvider);
  //   this.module = module;
  //   this.fooProducer = Producers.nonCancellationPropagatingViewOf(fooProducer);
  //   this.barProducer = Producers.nonCancellationPropagatingViewOf(barProducer);
  // }
  private MethodSpec constructorMethod(ProductionBinding binding, FactoryFields factoryFields) {
    MethodSpec.Builder constructorBuilder = constructorBuilder().addModifiers(PRIVATE);
    constructorBuilder.addStatement(
        "super($N, $L, $N)",
        factoryFields.monitorField,
        producerTokenConstruction(generatedClassNameForBinding(binding), binding),
        factoryFields.executorField);
    factoryFields.getAll()
        .forEach(
            field -> {
              constructorBuilder.addParameter(field.type, field.name);
              // The executor and monitor fields belong to the super class so they don't need a
              // field assignment here.
              if (!field.equals(factoryFields.executorField)
                      && !field.equals(factoryFields.monitorField)) {
                if (XTypeNames.rawJavaTypeName(field.type)
                        .equals(toJavaPoet(XTypeNames.PRODUCER))) {
                  constructorBuilder.addStatement(
                      "this.$1N = $2T.nonCancellationPropagatingViewOf($1N)",
                      field,
                      toJavaPoet(XTypeNames.PRODUCERS));
                } else {
                  constructorBuilder.addStatement("this.$1N = $1N", field);
                }
              }
            });
    return constructorBuilder.build();
  }

  // public static FooModule_ProducesFooFactory create(
  //     FooModule module,
  //     Provider<Executor> executorProvider,
  //     Provider<ProductionComponentMonitor> productionComponentMonitorProvider,
  //     Producer<Foo> fooProducer,
  //     Producer<Bar> barProducer) {
  //   return new FooModule_ProducesFooFactory(
  //       module, executorProvider, productionComponentMonitorProvider, fooProducer, barProducer);
  // }
  private MethodSpec staticCreateMethod(
      ProductionBinding binding, FactoryFields factoryFields) {
    List<ParameterSpec> params = constructorMethod(binding, factoryFields).parameters;
    return MethodSpec.methodBuilder("create")
        .addModifiers(PUBLIC, STATIC)
        .returns(toJavaPoet(parameterizedGeneratedTypeNameForBinding(binding)))
        .addTypeVariables(
            bindingTypeElementTypeVariableNames(binding).stream()
                .map(typeName -> (TypeVariableName) toJavaPoet(typeName))
                .collect(toImmutableList()))
        .addParameters(params)
        .addStatement(
            "return new $T($L)",
            toJavaPoet(parameterizedGeneratedTypeNameForBinding(binding)),
            parameterNames(params))
        .build();
  }

  // Example 1: No async dependencies.
  // protected ListenableFuture<Void> collectDependencies() {
  //   return Futures.<Void>immediateFuture(null);
  // }
  //
  // Example 2: Single async dependency, "fooProducer".
  // protected ListenableFuture<Foo> collectDependencies() {
  //   return fooProducer.get();
  // }
  //
  // Example 3: Multiple async dependencies, "fooProducer" and "barProducer".
  // protected ListenableFuture<List<Object>> collectDependencies() {
  //   ListenableFuture<Foo> fooFuture = fooProducer.get();
  //   ListenableFuture<Bar> barFuture = barProducer.get();
  //   return Futures.<Object>allAsList(fooFuture, barFuture);
  // }
  public MethodSpec collectDependenciesMethod(
      ProductionBinding binding, FactoryFields factoryFields) {
    MethodSpec.Builder methodBuilder =
        methodBuilder("collectDependencies")
            .addAnnotation(Override.class)
            .addModifiers(PROTECTED);
    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    switch (asyncDependencies.size()) {
      case 0:
        return methodBuilder
            .returns(toJavaPoet(listenableFutureOf(XTypeNames.UNIT_VOID_CLASS)))
            .addStatement(
                "return $T.<$T>immediateFuture(null)",
                toJavaPoet(XTypeNames.FUTURES),
                toJavaPoet(XTypeNames.UNIT_VOID_CLASS))
            .build();
      case 1: {
        DependencyRequest asyncDependency = getOnlyElement(asyncDependencies);
        FieldSpec asyncDependencyField = factoryFields.get(asyncDependency);
        return methodBuilder
            .returns(toJavaPoet(listenableFutureOf(asyncDependencyType(asyncDependency))))
            .addStatement("return $L", producedCodeBlock(asyncDependency, asyncDependencyField))
            .build();
      }
      default:
        CodeBlock.Builder argAssignments = CodeBlock.builder();
        ImmutableList.Builder<CodeBlock> argNames = ImmutableList.builder();
        for (DependencyRequest asyncDependency : asyncDependencies) {
          FieldSpec asyncDependencyField = factoryFields.get(asyncDependency);
          argNames.add(CodeBlock.of("$L", dependencyFutureName(asyncDependency)));
          argAssignments.addStatement(
              "$T $L = $L",
              toJavaPoet(listenableFutureOf(asyncDependencyType(asyncDependency))),
              dependencyFutureName(asyncDependency),
              producedCodeBlock(asyncDependency, asyncDependencyField));
        }
        return methodBuilder
            .returns(toJavaPoet(listenableFutureOf(listOf(XTypeName.ANY_OBJECT))))
            .addCode(argAssignments.build())
            .addStatement(
                "return $T.<$T>allAsList($L)",
                toJavaPoet(XTypeNames.FUTURES),
                toJavaPoet(XTypeName.ANY_OBJECT),
                makeParametersCodeBlock(argNames.build()))
            .build();
    }
  }

  private CodeBlock producedCodeBlock(DependencyRequest request, FieldSpec field) {
    return request.kind() == RequestKind.PRODUCED
        ? CodeBlock.of("$T.createFutureProduced($N.get())", toJavaPoet(XTypeNames.PRODUCERS), field)
        : CodeBlock.of("$N.get()", field);
  }

  // Example 1: No async dependencies.
  // @Override
  // public ListenableFuture<Foo> callProducesMethod(Void ignoredVoidArg) {
  //   return module.producesFoo();
  // }
  //
  // Example 2: Single async dependency.
  // @Override
  // public ListenableFuture<Foo> callProducesMethod(Bar bar) {
  //   return module.producesFoo(bar);
  // }
  //
  // Example 3: Multiple async dependencies.
  // @Override
  // @SuppressWarnings("unchecked")
  // public ListenableFuture<Foo> callProducesMethod(List<Object> args) {
  //   return module.producesFoo((Bar) args.get(0), (Baz) args.get(1));
  // }
  private MethodSpec callProducesMethod(ProductionBinding binding, FactoryFields factoryFields) {
    XTypeName contributedTypeName = binding.contributedType().asTypeName();
    ParameterSpec parameter = callProducesMethodParameter(binding);
    MethodSpec.Builder methodBuilder =
        methodBuilder("callProducesMethod")
            .returns(toJavaPoet(listenableFutureOf(contributedTypeName)))
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addExceptions(
                asMethod(binding.bindingElement().get()).getThrownTypes().stream()
                    .map(XType::getTypeName)
                    .collect(toImmutableList()))
            .addParameter(parameter);
    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    ImmutableList.Builder<CodeBlock> parameterCodeBlocks = ImmutableList.builder();
    for (DependencyRequest dependency : binding.explicitDependencies()) {
      if (isAsyncDependency(dependency)) {
        if (asyncDependencies.size() > 1) {
          XTypeName dependencyType = asyncDependencyType(dependency);
          int argIndex = asyncDependencies.indexOf(dependency);
          parameterCodeBlocks.add(
              CodeBlock.of("($T) $N.get($L)", toJavaPoet(dependencyType), parameter, argIndex));
        } else {
          parameterCodeBlocks.add(CodeBlock.of("$N", parameter));
        }
      } else {
        parameterCodeBlocks.add(
            sourceFiles.frameworkTypeUsageStatement(
                CodeBlock.of("$N", factoryFields.get(dependency)), dependency.kind()));
      }
    }
    if (asyncDependencies.size() > 1) {
      methodBuilder.addAnnotation(AnnotationSpecs.suppressWarnings(UNCHECKED));
    }

    CodeBlock moduleCodeBlock =
        CodeBlock.of(
            "$L.$L($L)",
            factoryFields.moduleField.isPresent()
                ? factoryFields.moduleField.get().name
                : CodeBlock.of("$T", toJavaPoet(binding.bindingTypeElement().get().asClassName())),
            getSimpleName(binding.bindingElement().get()),
            makeParametersCodeBlock(parameterCodeBlocks.build()));

    switch (ProductionKind.fromProducesMethod(asMethod(binding.bindingElement().get()))) {
      case IMMEDIATE:
        methodBuilder.addStatement(
            "return $T.<$T>immediateFuture($L)",
            toJavaPoet(XTypeNames.FUTURES),
            toJavaPoet(contributedTypeName),
            moduleCodeBlock);
        break;
      case FUTURE:
        methodBuilder.addStatement("return $L", moduleCodeBlock);
        break;
      case SET_OF_FUTURE:
        methodBuilder.addStatement(
            "return $T.allAsSet($L)",
            toJavaPoet(XTypeNames.PRODUCERS),
            moduleCodeBlock);
        break;
    }
    return methodBuilder.build();
  }

  private ParameterSpec callProducesMethodParameter(ProductionBinding binding) {
    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    switch (asyncDependencies.size()) {
      case 0:
        return ParameterSpec.builder(toJavaPoet(XTypeNames.UNIT_VOID_CLASS), "ignoredVoidArg")
            .build();
      case 1:
        DependencyRequest asyncDependency = getOnlyElement(asyncDependencies);
        String argName = getSimpleName(asyncDependency.requestElement().get().xprocessing());
        return ParameterSpec.builder(
                toJavaPoet(asyncDependencyType(asyncDependency)),
                argName.equals("module") ? "moduleArg" : argName)
            .build();
      default:
        return ParameterSpec.builder(
                toJavaPoet(listOf(XTypeName.ANY_OBJECT)),
                "args")
            .build();
    }
  }

  private static ImmutableList<DependencyRequest> asyncDependencies(ProductionBinding binding) {
    return binding.dependencies().stream()
        .filter(ProducerFactoryGenerator::isAsyncDependency)
        .collect(toImmutableList());
  }

  private CodeBlock producerTokenConstruction(
      XClassName generatedTypeName, ProductionBinding binding) {
    CodeBlock producerTokenArgs =
        compilerOptions.writeProducerNameInToken()
            ? CodeBlock.of(
                "$S",
                String.format(
                    "%s#%s",
                    binding.bindingTypeElement().get().getClassName(),
                    getSimpleName(binding.bindingElement().get())))
            : CodeBlock.of("$T.class", toJavaPoet(generatedTypeName));
    return CodeBlock.of("$T.create($L)", toJavaPoet(XTypeNames.PRODUCER_TOKEN), producerTokenArgs);
  }

  /** Returns a name of the variable representing this dependency's future. */
  private static String dependencyFutureName(DependencyRequest dependency) {
    return getSimpleName(dependency.requestElement().get().xprocessing()) + "Future";
  }

  private static boolean isAsyncDependency(DependencyRequest dependency) {
    switch (dependency.kind()) {
      case INSTANCE:
      case PRODUCED:
        return true;
      default:
        return false;
    }
  }

  private static XTypeName asyncDependencyType(DependencyRequest dependency) {
    XTypeName keyName = dependency.key().type().xprocessing().asTypeName();
    switch (dependency.kind()) {
      case INSTANCE:
        return keyName;
      case PRODUCED:
        return producedOf(keyName);
      default:
        throw new AssertionError();
    }
  }

  /** Represents the available fields in the generated factory class. */
  private static final class FactoryFields {
    static FactoryFields create(ProductionBinding binding) {
      UniqueNameSet nameSet = new UniqueNameSet();
      // TODO(bcorso, dpb): Add a test for the case when a Factory parameter is named "module".
      Optional<FieldSpec> moduleField =
          binding.requiresModuleInstance()
              ? Optional.of(
                  createField(
                      binding.bindingTypeElement().get().getType().asTypeName(),
                      nameSet.getUniqueName("module")))
              : Optional.empty();

      ImmutableMap.Builder<DependencyRequest, FieldSpec> builder =
          ImmutableMap.builder();
      generateBindingFieldsForDependencies(binding)
          .forEach(
              (dependency, field) ->
                  builder.put(
                      dependency,
                      createField(field.type(), nameSet.getUniqueName(field.name()))));
      return new FactoryFields(binding, moduleField, builder.buildOrThrow());
    }

    private static FieldSpec createField(XTypeName type, String name) {
      return FieldSpec.builder(toJavaPoet(type), name, PRIVATE, FINAL).build();
    }

    private final Optional<FieldSpec> moduleField;
    private final FieldSpec monitorField;
    private final FieldSpec executorField;
    private final ImmutableMap<DependencyRequest, FieldSpec> frameworkFields;

    private FactoryFields(
        ProductionBinding binding,
        Optional<FieldSpec> moduleField,
        ImmutableMap<DependencyRequest, FieldSpec> frameworkFields) {
      this.moduleField = moduleField;
      this.monitorField = frameworkFields.get(binding.monitorRequest());
      this.executorField = frameworkFields.get(binding.executorRequest());
      this.frameworkFields = frameworkFields;
    }

    FieldSpec get(DependencyRequest request) {
      return frameworkFields.get(request);
    }

    ImmutableList<FieldSpec> getAll() {
      return moduleField.isPresent()
          ? ImmutableList.<FieldSpec>builder()
              .add(moduleField.get())
              .addAll(frameworkFields.values())
              .build()
          : frameworkFields.values().asList();
    }
  }

  @Override
  protected ImmutableSet<Suppression> warningSuppressions() {
    // TODO(beder): examine if we can remove this or prevent subtypes of Future from being produced
    return ImmutableSet.of(FUTURE_RETURN_VALUE_IGNORED);
  }

  /** What kind of object a {@code @Produces}-annotated method returns. */
  private enum ProductionKind {
    /** A value. */
    IMMEDIATE,
    /** A {@code ListenableFuture<T>}. */
    FUTURE,
    /** A {@code Set<ListenableFuture<T>>}. */
    SET_OF_FUTURE;

    /** Returns the kind of object a {@code @Produces}-annotated method returns. */
    static ProductionKind fromProducesMethod(XMethodElement producesMethod) {
      if (isFutureType(producesMethod.getReturnType())) {
        return FUTURE;
      } else if (ContributionType.fromBindingElement(producesMethod)
              .equals(ContributionType.SET_VALUES)
          && isFutureType(SetType.from(producesMethod.getReturnType()).elementType())) {
        return SET_OF_FUTURE;
      } else {
        return IMMEDIATE;
      }
    }
  }
}

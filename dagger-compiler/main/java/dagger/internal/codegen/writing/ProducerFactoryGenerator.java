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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.SourceFiles.bindingTypeElementTypeVariableNames;
import static dagger.internal.codegen.binding.SourceFiles.generateBindingFieldsForDependencies;
import static dagger.internal.codegen.binding.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.binding.SourceFiles.parameterizedGeneratedTypeNameForBinding;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.writing.GwtCompatibility.gwtIncompatibleAnnotation;
import static dagger.internal.codegen.writing.InjectionMethods.copyFrameworkParameter;
import static dagger.internal.codegen.writing.InjectionMethods.copyParameter;
import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFromPublicApi;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.FUTURE_RETURN_VALUE_IGNORED;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.xprocessing.XAnnotationSpecs.suppressWarnings;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
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
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XParameterSpec;
import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.ProductionBinding;
import dagger.internal.codegen.binding.SourceFiles;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.Nullability;
import dagger.internal.codegen.xprocessing.XAnnotationSpecs.Suppression;
import dagger.internal.codegen.xprocessing.XFunSpecs;
import dagger.internal.codegen.xprocessing.XParameterSpecs;
import dagger.internal.codegen.xprocessing.XPropertySpecs;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
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
  public ImmutableList<XTypeSpec> topLevelTypes(ProductionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkArgument(!binding.unresolved().isPresent());
    checkArgument(binding.bindingElement().isPresent());

    FactoryFields factoryFields = FactoryFields.create(binding, compilerOptions);
    XTypeSpecs.Builder factoryBuilder =
        XTypeSpecs.classBuilder(generatedClassNameForBinding(binding))
            .superclass(
                XTypeNames.ABSTRACT_PRODUCES_METHOD_PRODUCER.parametrizedBy(
                    callProducesMethodParameter(binding).getType(),
                    binding.contributedType().asTypeName()))
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariableNames(bindingTypeElementTypeVariableNames(binding))
            .addProperties(
                factoryFields.getAll().stream()
                    // The executor and monitor fields are owned by the superclass so they are not
                    // included as fields in the generated factory subclass.
                    .filter(field -> !field.equals(factoryFields.executorField))
                    .filter(field -> !field.equals(factoryFields.monitorField))
                    .collect(toImmutableList()))
            .addFunction(constructorMethod(binding, factoryFields))
            .addFunction(staticCreateMethod(binding, factoryFields))
            .addFunction(collectDependenciesMethod(binding, factoryFields))
            .addFunction(callProducesMethod(binding, factoryFields));

    gwtIncompatibleAnnotation(binding).ifPresent(factoryBuilder::addAnnotation);

    return ImmutableList.of(factoryBuilder.build());
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
  private XFunSpec constructorMethod(ProductionBinding binding, FactoryFields factoryFields) {
    XFunSpecs.Builder constructorBuilder =
        constructorBuilder()
            .addModifiers(PRIVATE)
            .addParameters(constructorParameters(binding, factoryFields));
    constructorBuilder.addStatement(
        "super(%N, %L, %N)",
        factoryFields.monitorField,
        producerTokenConstruction(generatedClassNameForBinding(binding), binding),
        factoryFields.executorField);
    factoryFields.getAll().stream()
        // The executor and monitor fields belong to the super class so they don't need a field
        // assignment here.
        .filter(field -> !field.equals(factoryFields.executorField))
        .filter(field -> !field.equals(factoryFields.monitorField))
        .forEach(
            field -> {
              if (field.getType().getRawTypeName().equals(XTypeNames.PRODUCER)) {
                constructorBuilder.addStatement(
                    "this.%1N = %2T.nonCancellationPropagatingViewOf(%1N)",
                    field, XTypeNames.PRODUCERS);
              } else {
                constructorBuilder.addStatement("this.%1N = %1N", field);
              }
            });
    return constructorBuilder.build();
  }

  ImmutableList<XParameterSpec> constructorParameters(
      ProductionBinding binding, FactoryFields factoryFields) {
    return factoryFields.getAll().stream()
        .map(
            field ->
                XParameterSpecs.of(field.getName(), field.getType())) // SUPPRESS_GET_NAME_CHECK
        .collect(toImmutableList());
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
  private XFunSpec staticCreateMethod(ProductionBinding binding, FactoryFields factoryFields) {
    XFunSpecs.Builder createMethodBuilder =
        XFunSpecs.methodBuilder("create")
            .addModifiers(PUBLIC, STATIC)
            .returns(parameterizedGeneratedTypeNameForBinding(binding))
            .addTypeVariableNames(bindingTypeElementTypeVariableNames(binding));

    ImmutableList.Builder<XCodeBlock> arguments = ImmutableList.builder();
    factoryFields.moduleField.ifPresent(
        module -> {
          String moduleName = module.getName(); // SUPPRESS_GET_NAME_CHECK
          XType moduleType = binding.bindingTypeElement().get().getType();
          arguments.add(
              copyParameter(
                  createMethodBuilder,
                  moduleName,
                  moduleType.asTypeName(),
                  Nullability.NOT_NULLABLE,
                  /* isTypeNameAccessible= */
                  isTypeAccessibleFromPublicApi(moduleType, compilerOptions),
                  compilerOptions));
        });

    factoryFields.frameworkFields
        .forEach(
            (dependencyRequest, field) -> {
              String parameterName = field.getName(); // SUPPRESS_GET_NAME_CHECK
              XType dependencyType = dependencyRequest.key().type().xprocessing();
              arguments.add(
                  copyFrameworkParameter(
                      createMethodBuilder,
                      parameterName,
                      field.getType(),
                      Nullability.NOT_NULLABLE,
                      /* isTypeNameAccessible= */
                      isTypeAccessibleFromPublicApi(dependencyType, compilerOptions),
                      compilerOptions));
            });

    return createMethodBuilder
        .addStatement(
            "return %L",
            XCodeBlock.ofNewInstance(
                parameterizedGeneratedTypeNameForBinding(binding),
                "%L",
                makeParametersCodeBlock(arguments.build())))
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
  public XFunSpec collectDependenciesMethod(
      ProductionBinding binding, FactoryFields factoryFields) {
    XFunSpecs.Builder methodBuilder =
        methodBuilder("collectDependencies").isOverride(true).addModifiers(PROTECTED);
    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    switch (asyncDependencies.size()) {
      case 0:
        return methodBuilder
            .returns(listenableFutureOf(XTypeNames.UNIT_VOID_CLASS))
            .addStatement(
                "return %T.<%T>immediateFuture(null)",
                XTypeNames.FUTURES, XTypeNames.UNIT_VOID_CLASS)
            .build();
      case 1: {
        DependencyRequest asyncDependency = getOnlyElement(asyncDependencies);
          XPropertySpec asyncDependencyField = factoryFields.get(asyncDependency);
          return methodBuilder
              .returns(listenableFutureOf(asyncDependencyType(asyncDependency)))
              .addStatement("return %L", producedCodeBlock(asyncDependency, asyncDependencyField))
              .build();
      }
      default:
        XCodeBlock.Builder argAssignments = XCodeBlock.builder();
        ImmutableList.Builder<XCodeBlock> argNames = ImmutableList.builder();
        for (DependencyRequest asyncDependency : asyncDependencies) {
          XPropertySpec asyncDependencyField = factoryFields.get(asyncDependency);
          argNames.add(XCodeBlock.of("%N", dependencyFutureName(asyncDependency)));
          argAssignments.addLocalVal(
              /* name= */ dependencyFutureName(asyncDependency),
              /* typeName= */ listenableFutureOf(asyncDependencyType(asyncDependency)),
              /* assignExprFormat= */ "%L",
              /* assignExprArgs...= */ producedCodeBlock(asyncDependency, asyncDependencyField));
        }
        return methodBuilder
            .returns(listenableFutureOf(listOf(XTypeName.ANY_OBJECT)))
            .addCode(argAssignments.build())
            .addStatement(
                "return %T.<%T>allAsList(%L)",
                XTypeNames.FUTURES, XTypeName.ANY_OBJECT, makeParametersCodeBlock(argNames.build()))
            .build();
    }
  }

  private XCodeBlock producedCodeBlock(DependencyRequest request, XPropertySpec field) {
    return request.kind() == RequestKind.PRODUCED
        ? XCodeBlock.of("%T.createFutureProduced(%N.get())", XTypeNames.PRODUCERS, field)
        : XCodeBlock.of("%N.get()", field);
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
  private XFunSpec callProducesMethod(ProductionBinding binding, FactoryFields factoryFields) {
    XTypeName contributedTypeName = binding.contributedType().asTypeName();
    XParameterSpec parameter = callProducesMethodParameter(binding);
    XFunSpecs.Builder methodBuilder =
        methodBuilder("callProducesMethod")
            .returns(listenableFutureOf(contributedTypeName))
            .isOverride(true)
            .addModifiers(PUBLIC)
            .addExceptions(asMethod(binding.bindingElement().get()).getThrownTypes())
            .addParameter(parameter);
    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    ImmutableList.Builder<XCodeBlock> parameterCodeBlocks = ImmutableList.builder();
    for (DependencyRequest dependency : binding.explicitDependencies()) {
      if (isAsyncDependency(dependency)) {
        if (asyncDependencies.size() > 1) {
          XTypeName dependencyType = asyncDependencyType(dependency);
          int argIndex = asyncDependencies.indexOf(dependency);
          parameterCodeBlocks.add(
              XCodeBlock.ofCast(
                  dependencyType,
                  XCodeBlock.of(
                      "%N.get(%L)", parameter.getName(), argIndex))); // SUPPRESS_GET_NAME_CHECK
        } else {
          parameterCodeBlocks.add(
              XCodeBlock.of("%N", parameter.getName())); // SUPPRESS_GET_NAME_CHECK
        }
      } else {
        parameterCodeBlocks.add(
            sourceFiles.frameworkTypeUsageStatement(
                XCodeBlock.of("%N", factoryFields.get(dependency)), dependency.kind()));
      }
    }
    if (asyncDependencies.size() > 1) {
      methodBuilder.addAnnotation(suppressWarnings(UNCHECKED));
    }

    XCodeBlock moduleCodeBlock =
        XCodeBlock.of(
            "%L.%N(%L)",
            factoryFields.moduleField.isPresent()
                ? factoryFields.moduleField.get().getName() // SUPPRESS_GET_NAME_CHECK
                : XCodeBlock.of("%T", binding.bindingTypeElement().get().asClassName()),
            getSimpleName(binding.bindingElement().get()),
            makeParametersCodeBlock(parameterCodeBlocks.build()));

    switch (ProductionKind.fromProducesMethod(asMethod(binding.bindingElement().get()))) {
      case IMMEDIATE:
        methodBuilder.addStatement(
            "return %T.<%T>immediateFuture(%L)",
            XTypeNames.FUTURES, contributedTypeName, moduleCodeBlock);
        break;
      case FUTURE:
        methodBuilder.addStatement("return %L", moduleCodeBlock);
        break;
      case SET_OF_FUTURE:
        methodBuilder.addStatement("return %T.allAsSet(%L)", XTypeNames.PRODUCERS, moduleCodeBlock);
        break;
    }
    return methodBuilder.build();
  }

  private XParameterSpec callProducesMethodParameter(ProductionBinding binding) {
    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    switch (asyncDependencies.size()) {
      case 0:
        return XParameterSpecs.of("ignoredVoidArg", XTypeNames.UNIT_VOID_CLASS);
      case 1:
        DependencyRequest asyncDependency = getOnlyElement(asyncDependencies);
        String argName = getSimpleName(asyncDependency.requestElement().get().xprocessing());
        return XParameterSpecs.of(
            argName.equals("module") ? "moduleArg" : argName,
            asyncDependencyType(asyncDependency));
      default:
        return XParameterSpecs.of("args", listOf(XTypeName.ANY_OBJECT));
    }
  }

  private static ImmutableList<DependencyRequest> asyncDependencies(ProductionBinding binding) {
    return binding.dependencies().stream()
        .filter(ProducerFactoryGenerator::isAsyncDependency)
        .collect(toImmutableList());
  }

  private XCodeBlock producerTokenConstruction(
      XClassName generatedTypeName, ProductionBinding binding) {
    XCodeBlock producerTokenArgs =
        compilerOptions.writeProducerNameInToken()
            ? XCodeBlock.of(
                "%S",
                String.format(
                    "%s#%s",
                    binding.bindingTypeElement().get().getClassName(),
                    getSimpleName(binding.bindingElement().get())))
            : XCodeBlock.of("%T.class", generatedTypeName);
    return XCodeBlock.of("%T.create(%L)", XTypeNames.PRODUCER_TOKEN, producerTokenArgs);
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
    static FactoryFields create(ProductionBinding binding, CompilerOptions compilerOptions) {
      UniqueNameSet nameSet = new UniqueNameSet();
      // TODO(bcorso, dpb): Add a test for the case when a Factory parameter is named "module".
      Optional<XPropertySpec> moduleField =
          binding.requiresModuleInstance()
              ? Optional.of(
                  createField(
                      binding.bindingTypeElement().get().getType().asTypeName(),
                      nameSet.getUniqueName("module")))
              : Optional.empty();

      ImmutableMap.Builder<DependencyRequest, XPropertySpec> builder = ImmutableMap.builder();
      generateBindingFieldsForDependencies(binding, compilerOptions)
          .forEach(
              (dependency, field) ->
                  builder.put(
                      dependency, createField(field.type(), nameSet.getUniqueName(field.name()))));
      return new FactoryFields(binding, moduleField, builder.buildOrThrow());
    }

    private static XPropertySpec createField(XTypeName type, String name) {
      return XPropertySpecs.of(name, type, PRIVATE, FINAL);
    }

    private final Optional<XPropertySpec> moduleField;
    private final XPropertySpec monitorField;
    private final XPropertySpec executorField;
    private final ImmutableMap<DependencyRequest, XPropertySpec> frameworkFields;

    private FactoryFields(
        ProductionBinding binding,
        Optional<XPropertySpec> moduleField,
        ImmutableMap<DependencyRequest, XPropertySpec> frameworkFields) {
      this.moduleField = moduleField;
      this.monitorField = frameworkFields.get(binding.monitorRequest());
      this.executorField = frameworkFields.get(binding.executorRequest());
      this.frameworkFields = frameworkFields;
    }

    XPropertySpec get(DependencyRequest request) {
      return frameworkFields.get(request);
    }

    ImmutableList<XPropertySpec> getAll() {
      return moduleField.isPresent()
          ? ImmutableList.<XPropertySpec>builder()
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

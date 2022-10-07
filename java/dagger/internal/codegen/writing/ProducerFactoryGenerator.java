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
import static com.google.common.base.Verify.verifyNotNull;
import static com.squareup.javapoet.ClassName.OBJECT;
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
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.javapoet.TypeNames.FUTURES;
import static dagger.internal.codegen.javapoet.TypeNames.PRODUCERS;
import static dagger.internal.codegen.javapoet.TypeNames.PRODUCER_TOKEN;
import static dagger.internal.codegen.javapoet.TypeNames.VOID_CLASS;
import static dagger.internal.codegen.javapoet.TypeNames.listOf;
import static dagger.internal.codegen.javapoet.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.javapoet.TypeNames.producedOf;
import static dagger.internal.codegen.writing.GwtCompatibility.gwtIncompatibleAnnotation;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.UniqueNameSet;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.binding.FrameworkField;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.binding.ProductionBinding;
import dagger.internal.codegen.binding.SourceFiles;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javapoet.AnnotationSpecs;
import dagger.internal.codegen.javapoet.AnnotationSpecs.Suppression;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.producers.Producer;
import dagger.producers.internal.AbstractProducesMethodProducer;
import dagger.producers.internal.Producers;
import dagger.spi.model.DependencyRequest;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import javax.inject.Inject;

/** Generates {@link Producer} implementations from {@link ProductionBinding} instances. */
public final class ProducerFactoryGenerator extends SourceFileGenerator<ProductionBinding> {
  private final CompilerOptions compilerOptions;
  private final KeyFactory keyFactory;
  private final SourceFiles sourceFiles;

  @Inject
  ProducerFactoryGenerator(
      XFiler filer,
      XProcessingEnv processingEnv,
      CompilerOptions compilerOptions,
      KeyFactory keyFactory,
      SourceFiles sourceFiles) {
    super(filer, processingEnv);
    this.compilerOptions = compilerOptions;
    this.keyFactory = keyFactory;
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

    TypeName providedTypeName = binding.contributedType().getTypeName();
    TypeName futureTypeName = listenableFutureOf(providedTypeName);

    ClassName generatedTypeName = generatedClassNameForBinding(binding);
    TypeSpec.Builder factoryBuilder =
        classBuilder(generatedTypeName)
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariables(bindingTypeElementTypeVariableNames(binding));

    UniqueNameSet uniqueFieldNames = new UniqueNameSet();
    ImmutableMap.Builder<DependencyRequest, FieldSpec> fieldsBuilder = ImmutableMap.builder();

    MethodSpec.Builder constructorBuilder = constructorBuilder().addModifiers(PRIVATE);

    Optional<FieldSpec> moduleField =
        binding.requiresModuleInstance()
            ? Optional.of(
                addFieldAndConstructorParameter(
                    factoryBuilder,
                    constructorBuilder,
                    uniqueFieldNames.getUniqueName("module"),
                    binding.bindingTypeElement().get().getType().getTypeName()))
            : Optional.empty();

    List<CodeBlock> frameworkFieldAssignments = new ArrayList<>();

    String executorParameterName = null;
    String monitorParameterName = null;
    ImmutableMap<DependencyRequest, FrameworkField> bindingFieldsForDependencies =
        generateBindingFieldsForDependencies(binding);
    for (Entry<DependencyRequest, FrameworkField> entry : bindingFieldsForDependencies.entrySet()) {
      DependencyRequest dependency = entry.getKey();
      Key key = dependency.key();
      FrameworkField bindingField = entry.getValue();
      String fieldName = uniqueFieldNames.getUniqueName(bindingField.name());
      if (key.equals(keyFactory.forProductionImplementationExecutor())) {
        executorParameterName = fieldName;
        constructorBuilder.addParameter(bindingField.type(), executorParameterName);
      } else if (key.equals(keyFactory.forProductionComponentMonitor())) {
        monitorParameterName = fieldName;
        constructorBuilder.addParameter(bindingField.type(), monitorParameterName);
      } else {
        FieldSpec field =
            addFieldAndConstructorParameter(
                factoryBuilder, constructorBuilder, fieldName, bindingField.type());
        fieldsBuilder.put(dependency, field);
        frameworkFieldAssignments.add(fieldAssignment(field, bindingField.type()));
      }
    }
    ImmutableMap<DependencyRequest, FieldSpec> fields = fieldsBuilder.build();

    constructorBuilder.addStatement(
        "super($N, $L, $N)",
        verifyNotNull(monitorParameterName),
        producerTokenConstruction(generatedTypeName, binding),
        verifyNotNull(executorParameterName));

    if (binding.requiresModuleInstance()) {
      assignField(constructorBuilder, moduleField.get(), null);
    }

    constructorBuilder.addCode(CodeBlock.join(frameworkFieldAssignments, "\n"));

    MethodSpec.Builder collectDependenciesBuilder =
        methodBuilder("collectDependencies").addAnnotation(Override.class).addModifiers(PROTECTED);

    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    for (DependencyRequest dependency : asyncDependencies) {
      TypeName futureType = listenableFutureOf(asyncDependencyType(dependency));
      CodeBlock futureAccess = CodeBlock.of("$N.get()", fields.get(dependency));
      collectDependenciesBuilder.addStatement(
          "$T $L = $L",
          futureType,
          dependencyFutureName(dependency),
          dependency.kind().equals(RequestKind.PRODUCED)
              ? CodeBlock.of("$T.createFutureProduced($L)", PRODUCERS, futureAccess)
              : futureAccess);
    }
    FutureTransform futureTransform = createFutureTransform(fields, binding, asyncDependencies);

    collectDependenciesBuilder
        .returns(listenableFutureOf(futureTransform.applyArgType()))
        .addStatement("return $L", futureTransform.futureCodeBlock());

    MethodSpec.Builder callProducesMethod =
        methodBuilder("callProducesMethod")
            .returns(futureTypeName)
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(futureTransform.applyArgType(), futureTransform.applyArgName())
            .addExceptions(binding.thrownTypes().stream().map(XType::getTypeName).collect(toList()))
            .addCode(
                getInvocationCodeBlock(
                    binding, providedTypeName, futureTransform.parameterCodeBlocks()));
    if (futureTransform.hasUncheckedCast()) {
      callProducesMethod.addAnnotation(AnnotationSpecs.suppressWarnings(UNCHECKED));
    }

    MethodSpec constructor = constructorBuilder.build();
    factoryBuilder
        .superclass(
            ParameterizedTypeName.get(
                ClassName.get(AbstractProducesMethodProducer.class),
                futureTransform.applyArgType(),
                providedTypeName))
        .addMethod(constructor)
        .addMethod(staticFactoryMethod(binding, constructor))
        .addMethod(collectDependenciesBuilder.build())
        .addMethod(callProducesMethod.build());

    gwtIncompatibleAnnotation(binding).ifPresent(factoryBuilder::addAnnotation);

    // TODO(gak): write a sensible toString
    return ImmutableList.of(factoryBuilder);
  }

  private MethodSpec staticFactoryMethod(ProductionBinding binding, MethodSpec constructor) {
    return MethodSpec.methodBuilder("create")
        .addModifiers(PUBLIC, STATIC)
        .returns(parameterizedGeneratedTypeNameForBinding(binding))
        .addTypeVariables(bindingTypeElementTypeVariableNames(binding))
        .addParameters(constructor.parameters)
        .addStatement(
            "return new $T($L)",
            parameterizedGeneratedTypeNameForBinding(binding),
            constructor.parameters.stream()
                .map(p -> CodeBlock.of("$N", p.name))
                .collect(toParametersCodeBlock()))
        .build();
  }

  // TODO(ronshapiro): consolidate versions of these
  private static FieldSpec addFieldAndConstructorParameter(
      TypeSpec.Builder typeBuilder,
      MethodSpec.Builder constructorBuilder,
      String variableName,
      TypeName variableType) {
    FieldSpec field = FieldSpec.builder(variableType, variableName, PRIVATE, FINAL).build();
    typeBuilder.addField(field);
    constructorBuilder.addParameter(field.type, field.name);
    return field;
  }

  private static CodeBlock fieldAssignment(FieldSpec field, ParameterizedTypeName type) {
    CodeBlock.Builder statement = CodeBlock.builder();
    if (type != null && type.rawType.equals(TypeNames.PRODUCER)) {
      statement.addStatement(
          "this.$1N = $2T.nonCancellationPropagatingViewOf($1N)", field, Producers.class);
    } else {
      statement.addStatement("this.$1N = $1N", field);
    }
    return statement.build();
  }

  private static void assignField(
      MethodSpec.Builder constructorBuilder, FieldSpec field, ParameterizedTypeName type) {
    if (type != null && type.rawType.equals(TypeNames.PRODUCER)) {
      constructorBuilder.addStatement(
          "this.$1N = $2T.nonCancellationPropagatingViewOf($1N)", field, Producers.class);
    } else {
      constructorBuilder.addStatement("this.$1N = $1N", field);
    }
  }

  /** Returns a list of dependencies that are generated asynchronously. */
  private static ImmutableList<DependencyRequest> asyncDependencies(Binding binding) {
    return binding.dependencies().stream()
        .filter(ProducerFactoryGenerator::isAsyncDependency)
        .collect(toImmutableList());
  }

  private CodeBlock producerTokenConstruction(
      ClassName generatedTypeName, ProductionBinding binding) {
    CodeBlock producerTokenArgs =
        compilerOptions.writeProducerNameInToken()
            ? CodeBlock.of(
                "$S",
                String.format(
                    "%s#%s",
                    binding.bindingTypeElement().get().getClassName(),
                    getSimpleName(binding.bindingElement().get())))
            : CodeBlock.of("$T.class", generatedTypeName);
    return CodeBlock.of("$T.create($L)", PRODUCER_TOKEN, producerTokenArgs);
  }

  /** Returns a name of the variable representing this dependency's future. */
  private static String dependencyFutureName(DependencyRequest dependency) {
    return getSimpleName(dependency.requestElement().get().xprocessing()) + "Future";
  }

  private FutureTransform createFutureTransform(
        ImmutableMap<DependencyRequest, FieldSpec> fields,
        ProductionBinding binding,
        ImmutableList<DependencyRequest> asyncDependencies) {
      if (asyncDependencies.isEmpty()) {
        return new NoArgFutureTransform(fields, binding);
      } else if (asyncDependencies.size() == 1) {
        return new SingleArgFutureTransform(
            fields, binding, Iterables.getOnlyElement(asyncDependencies));
      } else {
        return new MultiArgFutureTransform(fields, binding, asyncDependencies);
      }
    }

  /** Represents the transformation of an input future by a producer method. */
  abstract class FutureTransform {
    protected final ImmutableMap<DependencyRequest, FieldSpec> fields;
    protected final ProductionBinding binding;

    FutureTransform(ImmutableMap<DependencyRequest, FieldSpec> fields, ProductionBinding binding) {
      this.fields = fields;
      this.binding = binding;
    }

    /** The code block representing the future that should be transformed. */
    abstract CodeBlock futureCodeBlock();

    /** The type of the argument to the apply method. */
    abstract TypeName applyArgType();

    /** The name of the argument to the apply method */
    abstract String applyArgName();

    /** The code blocks to be passed to the produces method itself. */
    abstract ImmutableList<CodeBlock> parameterCodeBlocks();

    /** Whether the transform method has an unchecked cast. */
    boolean hasUncheckedCast() {
      return false;
    }

    CodeBlock frameworkTypeUsageStatement(DependencyRequest dependency) {
      return sourceFiles.frameworkTypeUsageStatement(
          CodeBlock.of("$N", fields.get(dependency)), dependency.kind());
    }
  }

  final class NoArgFutureTransform extends FutureTransform {
    NoArgFutureTransform(
        ImmutableMap<DependencyRequest, FieldSpec> fields, ProductionBinding binding) {
      super(fields, binding);
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlock.of("$T.<$T>immediateFuture(null)", FUTURES, VOID_CLASS);
    }

    @Override
    TypeName applyArgType() {
      return VOID_CLASS;
    }

    @Override
    String applyArgName() {
      return "ignoredVoidArg";
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      return binding.explicitDependencies().stream()
          .map(this::frameworkTypeUsageStatement)
          .collect(toImmutableList());
    }
  }

  final class SingleArgFutureTransform extends FutureTransform {
    private final DependencyRequest asyncDependency;

    SingleArgFutureTransform(
        ImmutableMap<DependencyRequest, FieldSpec> fields,
        ProductionBinding binding,
        DependencyRequest asyncDependency) {
      super(fields, binding);
      this.asyncDependency = asyncDependency;
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlock.of("$L", dependencyFutureName(asyncDependency));
    }

    @Override
    TypeName applyArgType() {
      return asyncDependencyType(asyncDependency);
    }

    @Override
    String applyArgName() {
      String argName = getSimpleName(asyncDependency.requestElement().get().xprocessing());
      if (argName.equals("module")) {
        return "moduleArg";
      }
      return argName;
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      ImmutableList.Builder<CodeBlock> parameterCodeBlocks = ImmutableList.builder();
      for (DependencyRequest dependency : binding.explicitDependencies()) {
        // We really want to compare instances here, because asyncDependency is an element in the
        // set binding.dependencies().
        if (dependency == asyncDependency) {
          parameterCodeBlocks.add(CodeBlock.of("$L", applyArgName()));
        } else {
          parameterCodeBlocks.add(frameworkTypeUsageStatement(dependency));
        }
      }
      return parameterCodeBlocks.build();
    }
  }

  final class MultiArgFutureTransform extends FutureTransform {
    private final ImmutableList<DependencyRequest> asyncDependencies;

    MultiArgFutureTransform(
        ImmutableMap<DependencyRequest, FieldSpec> fields,
        ProductionBinding binding,
        ImmutableList<DependencyRequest> asyncDependencies) {
      super(fields, binding);
      this.asyncDependencies = asyncDependencies;
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlock.of(
          "$T.<$T>allAsList($L)",
          FUTURES,
          OBJECT,
          asyncDependencies
              .stream()
              .map(ProducerFactoryGenerator::dependencyFutureName)
              .collect(joining(", ")));
    }

    @Override
    TypeName applyArgType() {
      return listOf(OBJECT);
    }

    @Override
    String applyArgName() {
      return "args";
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      int argIndex = 0;
      ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
      for (DependencyRequest dependency : binding.explicitDependencies()) {
        if (isAsyncDependency(dependency)) {
          codeBlocks.add(
              CodeBlock.of(
                  "($T) $L.get($L)", asyncDependencyType(dependency), applyArgName(), argIndex));
          argIndex++;
        } else {
          codeBlocks.add(frameworkTypeUsageStatement(dependency));
        }
      }
      return codeBlocks.build();
    }

    @Override
    boolean hasUncheckedCast() {
      return true;
    }
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

  private static TypeName asyncDependencyType(DependencyRequest dependency) {
    TypeName keyName = dependency.key().type().xprocessing().getTypeName();
    switch (dependency.kind()) {
      case INSTANCE:
        return keyName;
      case PRODUCED:
        return producedOf(keyName);
      default:
        throw new AssertionError();
    }
  }

  /**
   * Creates a code block for the invocation of the producer method from the module, which should be
   * used entirely within a method body.
   *
   * @param binding The binding to generate the invocation code block for.
   * @param providedTypeName The type name that should be provided by this producer.
   * @param parameterCodeBlocks The code blocks for all the parameters to the producer method.
   */
  private CodeBlock getInvocationCodeBlock(
      ProductionBinding binding,
      TypeName providedTypeName,
      ImmutableList<CodeBlock> parameterCodeBlocks) {
    CodeBlock moduleCodeBlock =
        CodeBlock.of(
            "$L.$L($L)",
            binding.requiresModuleInstance()
                ? "module"
                : CodeBlock.of("$T", binding.bindingTypeElement().get().getClassName()),
            getSimpleName(binding.bindingElement().get()),
            makeParametersCodeBlock(parameterCodeBlocks));

    final CodeBlock returnCodeBlock;
    switch (binding.productionKind().get()) {
      case IMMEDIATE:
        returnCodeBlock =
            CodeBlock.of("$T.<$T>immediateFuture($L)", FUTURES, providedTypeName, moduleCodeBlock);
        break;
      case FUTURE:
        returnCodeBlock = moduleCodeBlock;
        break;
      case SET_OF_FUTURE:
        returnCodeBlock = CodeBlock.of("$T.allAsSet($L)", PRODUCERS, moduleCodeBlock);
        break;
      default:
        throw new AssertionError();
    }
    return CodeBlock.of("return $L;", returnCodeBlock);
  }

  @Override
  protected ImmutableSet<Suppression> warningSuppressions() {
    // TODO(beder): examine if we can remove this or prevent subtypes of Future from being produced
    return ImmutableSet.of(FUTURE_RETURN_VALUE_IGNORED);
  }
}

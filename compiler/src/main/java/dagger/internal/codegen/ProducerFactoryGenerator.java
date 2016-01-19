/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.ConstructorWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.Produces;
import dagger.producers.Produces.Type;
import dagger.producers.internal.AbstractProducer;
import dagger.producers.internal.Producers;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import java.util.List;
import java.util.concurrent.Executor;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.writer.Snippet.makeParametersSnippet;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates {@link Producer} implementations from {@link ProductionBinding} instances.
 *
 * @author Jesse Beder
 * @since 2.0
 */
final class ProducerFactoryGenerator extends JavaWriterSourceFileGenerator<ProductionBinding> {
  private final DependencyRequestMapper dependencyRequestMapper;

  ProducerFactoryGenerator(
      Filer filer, Elements elements, DependencyRequestMapper dependencyRequestMapper) {
    super(filer, elements);
    this.dependencyRequestMapper = dependencyRequestMapper;
  }

  @Override
  ClassName nameGeneratedType(ProductionBinding binding) {
    return generatedClassNameForBinding(binding);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(ProductionBinding binding) {
    return ImmutableSet.of(binding.bindingElement());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProductionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, ProductionBinding binding) {
    TypeMirror keyType =
        binding.productionType().equals(Type.MAP)
            ? MapType.from(binding.key().type()).unwrappedValueType(Producer.class)
            : binding.key().type();
    TypeName providedTypeName = TypeNames.forTypeMirror(keyType);
    TypeName futureTypeName = ParameterizedTypeName.create(
        ClassName.fromClass(ListenableFuture.class), providedTypeName);
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());

    ClassWriter factoryWriter = writer.addClass(generatedTypeName.simpleName());
    ConstructorWriter constructorWriter = factoryWriter.addConstructor();
    constructorWriter.addModifiers(PUBLIC);

    ImmutableMap<BindingKey, FrameworkField> fields =
        SourceFiles.generateBindingFieldsForDependencies(dependencyRequestMapper, binding);

    constructorWriter
        .body()
        .addSnippet(
            "super(%s, %s.create(%s.class));",
            fields.get(binding.monitorRequest().get().bindingKey()).name(),
            ClassName.fromClass(ProducerToken.class),
            factoryWriter.name());

    if (!binding.bindingElement().getModifiers().contains(STATIC)) {
      factoryWriter.addField(binding.bindingTypeElement(), "module")
          .addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(binding.bindingTypeElement(), "module");
      constructorWriter.body()
          .addSnippet("assert module != null;")
          .addSnippet("this.module = module;");
    }

    factoryWriter.addField(Executor.class, "executor")
        .addModifiers(PRIVATE, FINAL);
    constructorWriter.addParameter(Executor.class, "executor");
    constructorWriter.body()
        .addSnippet("assert executor != null;")
        .addSnippet("this.executor = executor;");

    factoryWriter.addModifiers(PUBLIC);
    factoryWriter.addModifiers(FINAL);
    factoryWriter.setSuperclass(
        ParameterizedTypeName.create(AbstractProducer.class, providedTypeName));

    MethodWriter computeMethodWriter = factoryWriter.addMethod(futureTypeName, "compute");
    computeMethodWriter.annotate(Override.class);
    computeMethodWriter.addModifiers(PROTECTED);
    computeMethodWriter.addParameter(ProducerMonitor.class, "monitor").addModifiers(FINAL);

    for (FrameworkField bindingField : fields.values()) {
      TypeName fieldType = bindingField.frameworkType();
      FieldWriter field = factoryWriter.addField(fieldType, bindingField.name());
      field.addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(field.type(), field.name());
      constructorWriter.body()
          .addSnippet("assert %s != null;", field.name())
          .addSnippet("this.%1$s = %1$s;", field.name());
    }

    boolean returnsFuture =
        binding.bindingKind().equals(ContributionBinding.Kind.FUTURE_PRODUCTION);
    ImmutableList<DependencyRequest> asyncDependencies =
        FluentIterable.from(binding.implicitDependencies())
            .filter(
                new Predicate<DependencyRequest>() {
                  @Override
                  public boolean apply(DependencyRequest dependency) {
                    return isAsyncDependency(dependency);
                  }
                })
            .toList();

    for (DependencyRequest dependency : asyncDependencies) {
      ParameterizedTypeName futureType = ParameterizedTypeName.create(
          ClassName.fromClass(ListenableFuture.class),
          asyncDependencyType(dependency));
      String name = fields.get(dependency.bindingKey()).name();
      Snippet futureAccess = Snippet.format("%s.get()", name);
      computeMethodWriter
          .body()
          .addSnippet(
              "%s %s = %s;",
              futureType,
              dependencyFutureName(dependency),
              dependency.kind().equals(DependencyRequest.Kind.PRODUCED)
                  ? Snippet.format(
                      "%s.createFutureProduced(%s)",
                      ClassName.fromClass(Producers.class),
                      futureAccess)
                  : futureAccess);
    }

    FutureTransform futureTransform = FutureTransform.create(fields, binding, asyncDependencies);
    Snippet transformSnippet =
        Snippet.format(
            Joiner.on('\n')
                .join(
                    "new %1$s<%2$s, %3$s>() {",
                    "  %4$s",
                    "  @Override public %5$s apply(%2$s %6$s) %7$s {",
                    "    %8$s",
                    "  }",
                    "}"),
            ClassName.fromClass(AsyncFunction.class),
            futureTransform.applyArgType(),
            providedTypeName,
            futureTransform.hasUncheckedCast()
                ? "@SuppressWarnings(\"unchecked\")  // safe by specification"
                : "",
            futureTypeName,
            futureTransform.applyArgName(),
            getThrowsClause(binding.thrownTypes()),
            getInvocationSnippet(
                !returnsFuture, binding, providedTypeName, futureTransform.parameterSnippets()));
    computeMethodWriter
        .body()
        .addSnippet(
            "return %s.transform(%s, %s, executor);",
            ClassName.fromClass(Futures.class),
            futureTransform.futureSnippet(),
            transformSnippet);

    // TODO(gak): write a sensible toString
    return ImmutableSet.of(writer);
  }

  /** Returns a name of the variable representing this dependency's future. */
  private static String dependencyFutureName(DependencyRequest dependency) {
    return dependency.requestElement().getSimpleName() + "Future";
  }

  /** Represents the transformation of an input future by a producer method. */
  abstract static class FutureTransform {
    protected final ImmutableMap<BindingKey, FrameworkField> fields;
    protected final ProductionBinding binding;

    FutureTransform(ImmutableMap<BindingKey, FrameworkField> fields, ProductionBinding binding) {
      this.fields = fields;
      this.binding = binding;
    }

    /** The snippet representing the future that should be transformed. */
    abstract Snippet futureSnippet();

    /** The type of the argument to the apply method. */
    abstract TypeName applyArgType();

    /** The name of the argument to the apply method */
    abstract String applyArgName();

    /** The snippets to be passed to the produces method itself. */
    abstract ImmutableList<Snippet> parameterSnippets();

    /** Whether the transform method has an unchecked cast. */
    boolean hasUncheckedCast() {
      return false;
    }

    static FutureTransform create(
        ImmutableMap<BindingKey, FrameworkField> fields,
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
  }

  static final class NoArgFutureTransform extends FutureTransform {
    NoArgFutureTransform(
        ImmutableMap<BindingKey, FrameworkField> fields, ProductionBinding binding) {
      super(fields, binding);
    }

    @Override
    Snippet futureSnippet() {
      return Snippet.format(
          "%s.<%s>immediateFuture(null)",
          ClassName.fromClass(Futures.class),
          ClassName.fromClass(Void.class));
    }

    @Override
    TypeName applyArgType() {
      return ClassName.fromClass(Void.class);
    }

    @Override
    String applyArgName() {
      return "ignoredVoidArg";
    }

    @Override
    ImmutableList<Snippet> parameterSnippets() {
      ImmutableList.Builder<Snippet> parameterSnippets = ImmutableList.builder();
      for (DependencyRequest dependency : binding.dependencies()) {
        parameterSnippets.add(
            frameworkTypeUsageStatement(
                Snippet.format(
                    "%s", fields.get(dependency.bindingKey()).name()), dependency.kind()));
      }
      return parameterSnippets.build();
    }
  }

  static final class SingleArgFutureTransform extends FutureTransform {
    private final DependencyRequest asyncDependency;

    SingleArgFutureTransform(
        ImmutableMap<BindingKey, FrameworkField> fields,
        ProductionBinding binding,
        DependencyRequest asyncDependency) {
      super(fields, binding);
      this.asyncDependency = asyncDependency;
    }

    @Override
    Snippet futureSnippet() {
      return Snippet.format("%s", dependencyFutureName(asyncDependency));
    }

    @Override
    TypeName applyArgType() {
      return asyncDependencyType(asyncDependency);
    }

    @Override
    String applyArgName() {
      return asyncDependency.requestElement().getSimpleName().toString();
    }

    @Override
    ImmutableList<Snippet> parameterSnippets() {
      ImmutableList.Builder<Snippet> parameterSnippets = ImmutableList.builder();
      for (DependencyRequest dependency : binding.dependencies()) {
        // We really want to compare instances here, because asyncDependency is an element in the
        // set binding.dependencies().
        if (dependency == asyncDependency) {
          parameterSnippets.add(Snippet.format("%s", applyArgName()));
        } else {
          parameterSnippets.add(
              frameworkTypeUsageStatement(
                  Snippet.format(
                      "%s", fields.get(dependency.bindingKey()).name()), dependency.kind()));
        }
      }
      return parameterSnippets.build();
    }
  }

  static final class MultiArgFutureTransform extends FutureTransform {
    private final ImmutableList<DependencyRequest> asyncDependencies;

    MultiArgFutureTransform(
        ImmutableMap<BindingKey, FrameworkField> fields,
        ProductionBinding binding,
        ImmutableList<DependencyRequest> asyncDependencies) {
      super(fields, binding);
      this.asyncDependencies = asyncDependencies;
    }

    @Override
    Snippet futureSnippet() {
      return Snippet.format(
          "%s.<%s>allAsList(%s)",
          ClassName.fromClass(Futures.class),
          ClassName.fromClass(Object.class),
          makeParametersSnippet(
              FluentIterable.from(asyncDependencies)
                  .transform(
                      new Function<DependencyRequest, Snippet>() {
                        @Override
                        public Snippet apply(DependencyRequest dependency) {
                          return Snippet.format("%s", dependencyFutureName(dependency));
                        }
                      })));
    }

    @Override
    TypeName applyArgType() {
      return ParameterizedTypeName.create(
          ClassName.fromClass(List.class), ClassName.fromClass(Object.class));
    }

    @Override
    String applyArgName() {
      return "args";
    }

    @Override
    ImmutableList<Snippet> parameterSnippets() {
      return getParameterSnippets(binding, fields, applyArgName());
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
    TypeName keyName = TypeNames.forTypeMirror(dependency.key().type());
    switch (dependency.kind()) {
      case INSTANCE:
        return keyName;
      case PRODUCED:
        return ParameterizedTypeName.create(ClassName.fromClass(Produced.class), keyName);
      default:
        throw new AssertionError();
    }
  }

  private static ImmutableList<Snippet> getParameterSnippets(
      ProductionBinding binding,
      ImmutableMap<BindingKey, FrameworkField> fields,
      String listArgName) {
    int argIndex = 0;
    ImmutableList.Builder<Snippet> snippets = ImmutableList.builder();
    for (DependencyRequest dependency : binding.dependencies()) {
      if (isAsyncDependency(dependency)) {
        snippets.add(Snippet.format(
            "(%s) %s.get(%s)",
            asyncDependencyType(dependency),
            listArgName,
            argIndex));
        argIndex++;
      } else {
        snippets.add(frameworkTypeUsageStatement(
            Snippet.format("%s", fields.get(dependency.bindingKey()).name()), dependency.kind()));
      }
    }
    return snippets.build();
  }

  /**
   * Creates a snippet for the invocation of the producer method from the module, which should be
   * used entirely within a method body.
   *
   * @param wrapWithFuture If true, wraps the result of the call to the producer method
   *        in an immediate future.
   * @param binding The binding to generate the invocation snippet for.
   * @param providedTypeName The type name that should be provided by this producer.
   * @param parameterSnippets The snippets for all the parameters to the producer method.
   */
  private Snippet getInvocationSnippet(
      boolean wrapWithFuture,
      ProductionBinding binding,
      TypeName providedTypeName,
      ImmutableList<Snippet> parameterSnippets) {
     Snippet moduleSnippet = Snippet.format("%s.%s(%s)",
        binding.bindingElement().getModifiers().contains(STATIC)
            ? ClassName.fromTypeElement(binding.bindingTypeElement())
            : "module",
        binding.bindingElement().getSimpleName(),
        makeParametersSnippet(parameterSnippets));

    // NOTE(beder): We don't worry about catching exeptions from the monitor methods themselves
    // because we'll wrap all monitoring in non-throwing monitors before we pass them to the
    // factories.
    ImmutableList.Builder<Snippet> snippets = ImmutableList.builder();
    snippets.add(Snippet.format("monitor.methodStarting();"));

    final Snippet valueSnippet;
    if (binding.productionType().equals(Produces.Type.SET)) {
      if (binding.bindingKind().equals(ContributionBinding.Kind.FUTURE_PRODUCTION)) {
        valueSnippet =
            Snippet.format(
                "%s.createFutureSingletonSet(%s)",
                ClassName.fromClass(Producers.class),
                moduleSnippet);
      } else {
        valueSnippet =
            Snippet.format("%s.of(%s)", ClassName.fromClass(ImmutableSet.class), moduleSnippet);
      }
    } else {
      valueSnippet = moduleSnippet;
    }
    Snippet returnSnippet =
        wrapWithFuture
            ? Snippet.format(
                "%s.<%s>immediateFuture(%s)",
                ClassName.fromClass(Futures.class),
                providedTypeName,
                valueSnippet)
            : valueSnippet;
    return Snippet.format(
        Joiner.on('\n')
            .join(
                "monitor.methodStarting();",
                "try {",
                "  return %s;",
                "} finally {",
                "  monitor.methodFinished();",
                "}"),
        returnSnippet);
  }

  /**
   * Creates a Snippet for the throws clause.
   *
   * @param thrownTypes the list of thrown types.
   */
  private Snippet getThrowsClause(List<? extends TypeMirror> thrownTypes) {
    if (thrownTypes.isEmpty()) {
      return Snippet.format("");
    }
    return Snippet.format("throws %s ",
        Snippet.makeParametersSnippet(FluentIterable
            .from(thrownTypes)
            .transform(new Function<TypeMirror, Snippet>() {
              @Override public Snippet apply(TypeMirror thrownType) {
                return Snippet.format("%s", TypeNames.forTypeMirror(thrownType));
              }
            })
            .toList()));
  }
}

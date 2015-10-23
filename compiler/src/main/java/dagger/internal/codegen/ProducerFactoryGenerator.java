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

import com.google.auto.common.MoreTypes;
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
import dagger.Provides.Type;
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
import dagger.producers.internal.AbstractProducer;
import dagger.producers.internal.Producers;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import static dagger.internal.codegen.SourceFiles.factoryNameForProductionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.writer.Snippet.makeParametersSnippet;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates {@link Producer} implementations from {@link ProductionBinding} instances.
 *
 * @author Jesse Beder
 * @since 2.0
 */
final class ProducerFactoryGenerator extends SourceFileGenerator<ProductionBinding> {
  private final DependencyRequestMapper dependencyRequestMapper;

  ProducerFactoryGenerator(Filer filer, DependencyRequestMapper dependencyRequestMapper) {
    super(filer);
    this.dependencyRequestMapper = dependencyRequestMapper;
  }

  @Override
  ClassName nameGeneratedType(ProductionBinding binding) {
    return factoryNameForProductionBinding(binding);
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
    TypeMirror keyType = binding.productionType().equals(Type.MAP)
        ? Util.getProvidedValueTypeOfMap(MoreTypes.asDeclared(binding.key().type()))
        : binding.key().type();
    TypeName providedTypeName = TypeNames.forTypeMirror(keyType);
    TypeName futureTypeName = ParameterizedTypeName.create(
        ClassName.fromClass(ListenableFuture.class), providedTypeName);
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());

    ClassWriter factoryWriter = writer.addClass(generatedTypeName.simpleName());
    ConstructorWriter constructorWriter = factoryWriter.addConstructor();
    constructorWriter.addModifiers(PUBLIC);

    factoryWriter.addField(binding.bindingTypeElement(), "module")
        .addModifiers(PRIVATE, FINAL);
    constructorWriter.addParameter(binding.bindingTypeElement(), "module");
    constructorWriter.body()
        .addSnippet("assert module != null;")
        .addSnippet("this.module = module;");

    factoryWriter.addField(Executor.class, "executor")
        .addModifiers(PRIVATE, FINAL);
    constructorWriter.addParameter(Executor.class, "executor");
    constructorWriter.body()
        .addSnippet("assert executor != null;")
        .addSnippet("this.executor = executor;");

    factoryWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getName());
    factoryWriter.addModifiers(PUBLIC);
    factoryWriter.addModifiers(FINAL);
    factoryWriter.setSuperclass(
        ParameterizedTypeName.create(AbstractProducer.class, providedTypeName));

    MethodWriter getMethodWriter = factoryWriter.addMethod(futureTypeName, "compute");
    getMethodWriter.annotate(Override.class);
    getMethodWriter.addModifiers(PROTECTED);

    final ImmutableMap<BindingKey, FrameworkField> fields =
        SourceFiles.generateBindingFieldsForDependencies(
            dependencyRequestMapper, binding.dependencies());

    for (FrameworkField bindingField : fields.values()) {
      TypeName fieldType = bindingField.frameworkType();
      FieldWriter field = factoryWriter.addField(fieldType, bindingField.name());
      field.addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(field.type(), field.name());
      constructorWriter.body()
          .addSnippet("assert %s != null;", field.name())
          .addSnippet("this.%1$s = %1$s;", field.name());
    }

    boolean returnsFuture = binding.bindingKind().equals(ProductionBinding.Kind.FUTURE_PRODUCTION);
    ImmutableList<DependencyRequest> asyncDependencies = FluentIterable
        .from(binding.dependencies())
        .filter(new Predicate<DependencyRequest>() {
          @Override public boolean apply(DependencyRequest dependency) {
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
      getMethodWriter.body().addSnippet("%s %sFuture = %s;",
          futureType,
          name,
          dependency.kind().equals(DependencyRequest.Kind.PRODUCED)
              ? Snippet.format("%s.createFutureProduced(%s)",
                  ClassName.fromClass(Producers.class), futureAccess)
              : futureAccess);
    }

    if (asyncDependencies.isEmpty()) {
      ImmutableList.Builder<Snippet> parameterSnippets = ImmutableList.builder();
      for (DependencyRequest dependency : binding.dependencies()) {
        parameterSnippets.add(frameworkTypeUsageStatement(
            Snippet.format(fields.get(dependency.bindingKey()).name()), dependency.kind()));
      }
      final boolean wrapWithFuture = false;  // since submitToExecutor will create the future
      Snippet invocationSnippet = getInvocationSnippet(wrapWithFuture, binding,
          parameterSnippets.build());
      TypeName callableReturnType = returnsFuture ? futureTypeName : providedTypeName;
      Snippet throwsClause = getThrowsClause(binding.thrownTypes());
      Snippet callableSnippet = Snippet.format(Joiner.on('\n').join(
          "new %1$s<%2$s>() {",
          "  @Override public %2$s call() %3$s{",
          "    return %4$s;",
          "  }",
          "}"),
          ClassName.fromClass(Callable.class),
          callableReturnType,
          throwsClause,
          invocationSnippet);
      getMethodWriter.body().addSnippet("%s future = %s.submitToExecutor(%s, executor);",
          ParameterizedTypeName.create(
              ClassName.fromClass(ListenableFuture.class),
              callableReturnType),
          ClassName.fromClass(Producers.class),
          callableSnippet);
      getMethodWriter.body().addSnippet("return %s;",
          returnsFuture
              ? Snippet.format("%s.dereference(future)", ClassName.fromClass(Futures.class))
              : "future");
    } else {
      final Snippet futureSnippet;
      final Snippet transformSnippet;
      if (asyncDependencies.size() == 1) {
        DependencyRequest asyncDependency = Iterables.getOnlyElement(asyncDependencies);
        futureSnippet = Snippet.format("%s",
            fields.get(asyncDependency.bindingKey()).name() + "Future");
        String argName = asyncDependency.requestElement().getSimpleName().toString();
        ImmutableList.Builder<Snippet> parameterSnippets = ImmutableList.builder();
        for (DependencyRequest dependency : binding.dependencies()) {
          // We really want to compare instances here, because asyncDependency is an element in the
          // set binding.dependencies().
          if (dependency == asyncDependency) {
            parameterSnippets.add(Snippet.format("%s", argName));
          } else {
            parameterSnippets.add(frameworkTypeUsageStatement(
                Snippet.format(fields.get(dependency.bindingKey()).name()),
                dependency.kind()));
          }
        }
        boolean wrapWithFuture = !returnsFuture;  // only wrap if we don't already have a future
        Snippet invocationSnippet = getInvocationSnippet(wrapWithFuture, binding,
            parameterSnippets.build());
        Snippet throwsClause = getThrowsClause(binding.thrownTypes());
        transformSnippet = Snippet.format(Joiner.on('\n').join(
            "new %1$s<%2$s, %3$s>() {",
            "  @Override public %4$s apply(%2$s %5$s) %6$s{",
            "    return %7$s;",
            "  }",
            "}"),
            ClassName.fromClass(AsyncFunction.class),
            asyncDependencyType(asyncDependency),
            providedTypeName,
            futureTypeName,
            argName,
            throwsClause,
            invocationSnippet);
      } else {
        futureSnippet = Snippet.format("%s.<%s>allAsList(%s)",
            ClassName.fromClass(Futures.class),
            ClassName.fromClass(Object.class),
            Joiner.on(",").join(FluentIterable
                .from(asyncDependencies)
                .transform(new Function<DependencyRequest, String>() {
                  @Override public String apply(DependencyRequest dependency) {
                    return fields.get(dependency.bindingKey()).name() + "Future";
                  }
                })));
        ImmutableList<Snippet> parameterSnippets = getParameterSnippets(binding, fields, "args");
        boolean wrapWithFuture = !returnsFuture;  // only wrap if we don't already have a future
        Snippet invocationSnippet = getInvocationSnippet(wrapWithFuture, binding,
            parameterSnippets);
        ParameterizedTypeName listOfObject = ParameterizedTypeName.create(
            ClassName.fromClass(List.class), ClassName.fromClass(Object.class));
        Snippet throwsClause = getThrowsClause(binding.thrownTypes());
        transformSnippet = Snippet.format(Joiner.on('\n').join(
            "new %1$s<%2$s, %3$s>() {",
            "  @SuppressWarnings(\"unchecked\")  // safe by specification",
            "  @Override public %4$s apply(%2$s args) %5$s{",
            "    return %6$s;",
            "  }",
            "}"),
            ClassName.fromClass(AsyncFunction.class),
            listOfObject,
            providedTypeName,
            futureTypeName,
            throwsClause,
            invocationSnippet);
      }
      getMethodWriter.body().addSnippet("return %s.%s(%s, %s, executor);",
          ClassName.fromClass(Futures.class),
          "transform",
          futureSnippet,
          transformSnippet);
    }

    // TODO(gak): write a sensible toString
    return ImmutableSet.of(writer);
  }

  private boolean isAsyncDependency(DependencyRequest dependency) {
    switch (dependency.kind()) {
      case INSTANCE:
      case PRODUCED:
        return true;
      default:
        return false;
    }
  }

  private TypeName asyncDependencyType(DependencyRequest dependency) {
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

  private ImmutableList<Snippet> getParameterSnippets(ProductionBinding binding,
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
            Snippet.format(fields.get(dependency.bindingKey()).name()), dependency.kind()));
      }
    }
    return snippets.build();
  }

  /**
   * Creates a Snippet for the invocation of the producer method from the module.
   *
   * @param wrapWithFuture If true, wraps the result of the call to the producer method
   *        in an immediate future.
   * @param binding The binding to generate the invocation snippet for.
   * @param parameterSnippets The snippets for all the parameters to the producer method.
   */
  private Snippet getInvocationSnippet(boolean wrapWithFuture, ProductionBinding binding,
      ImmutableList<Snippet> parameterSnippets) {
    Snippet moduleSnippet = Snippet.format("module.%s(%s)",
        binding.bindingElement().getSimpleName(),
        makeParametersSnippet(parameterSnippets));
    if (wrapWithFuture) {
      moduleSnippet = Snippet.format("%s.immediateFuture(%s)",
          ClassName.fromClass(Futures.class),
          moduleSnippet);
    }
    if (binding.productionType().equals(Produces.Type.SET)) {
      if (binding.bindingKind().equals(ProductionBinding.Kind.FUTURE_PRODUCTION)) {
        return Snippet.format("%s.createFutureSingletonSet(%s)",
            ClassName.fromClass(Producers.class),
            moduleSnippet);
      } else {
        return Snippet.format("%s.of(%s)",
            ClassName.fromClass(ImmutableSet.class),
            moduleSnippet);
      }
    } else {
      return moduleSnippet;
    }
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

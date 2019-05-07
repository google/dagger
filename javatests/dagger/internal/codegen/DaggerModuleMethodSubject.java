/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import javax.tools.JavaFileObject;

/** A {@link Truth} subject for testing Dagger module methods. */
final class DaggerModuleMethodSubject extends Subject<DaggerModuleMethodSubject, String> {

  /** A {@link Truth} subject factory for testing Dagger module methods. */
  static final class Factory implements Subject.Factory<DaggerModuleMethodSubject, String> {

    /** Starts a clause testing a Dagger {@link Module @Module} method. */
    static DaggerModuleMethodSubject assertThatModuleMethod(String method) {
      return assertAbout(daggerModuleMethod())
          .that(method)
          .withDeclaration("@Module abstract class %s { %s }");
    }

    /** Starts a clause testing a Dagger {@link ProducerModule @ProducerModule} method. */
    static DaggerModuleMethodSubject assertThatProductionModuleMethod(String method) {
      return assertAbout(daggerModuleMethod())
          .that(method)
          .withDeclaration("@ProducerModule abstract class %s { %s }");
    }

    /** Starts a clause testing a method in an unannotated class. */
    static DaggerModuleMethodSubject assertThatMethodInUnannotatedClass(String method) {
      return assertAbout(daggerModuleMethod())
          .that(method)
          .withDeclaration("abstract class %s { %s }");
    }

    static Factory daggerModuleMethod() {
      return new Factory();
    }

    private Factory() {}

    @Override
    public DaggerModuleMethodSubject createSubject(FailureMetadata failureMetadata, String that) {
      return new DaggerModuleMethodSubject(failureMetadata, that);
    }
  }

  private final String actual;
  private final ImmutableList.Builder<String> imports =
      new ImmutableList.Builder<String>()
          .add(
              // explicitly import Module so it's not ambiguous with java.lang.Module
              "import dagger.Module;",
              "import dagger.*;",
              "import dagger.multibindings.*;",
              "import dagger.producers.*;",
              "import java.util.*;",
              "import javax.inject.*;");
  private String declaration;
  private ImmutableList<JavaFileObject> additionalSources = ImmutableList.of();

  private DaggerModuleMethodSubject(FailureMetadata failureMetadata, String subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  /**
   * Imports classes and interfaces. Note that all types in the following packages are already
   * imported:<ul>
   * <li>{@code dagger.*}
   * <li>{@code dagger.multibindings.*}
   * <li>(@code dagger.producers.*}
   * <li>{@code java.util.*}
   * <li>{@code javax.inject.*}
   * </ul>
   */
  DaggerModuleMethodSubject importing(Class<?>... imports) {
    return importing(Arrays.asList(imports));
  }

  /**
   * Imports classes and interfaces. Note that all types in the following packages are already
   * imported:<ul>
   * <li>{@code dagger.*}
   * <li>{@code dagger.multibindings.*}
   * <li>(@code dagger.producers.*}
   * <li>{@code java.util.*}
   * <li>{@code javax.inject.*}
   * </ul>
   */
  DaggerModuleMethodSubject importing(List<? extends Class<?>> imports) {
    imports.stream()
        .map(clazz -> String.format("import %s;", clazz.getCanonicalName()))
        .forEachOrdered(this.imports::add);
    return this;
  }

  /**
   * Sets the declaration of the module. Must be a string with two {@code %s} parameters. The first
   * will be replaced with the name of the type, and the second with the method declaration, which
   * must be within paired braces.
   */
  DaggerModuleMethodSubject withDeclaration(String declaration) {
    this.declaration = declaration;
    return this;
  }

  /** Additional source files that must be compiled with the module. */
  DaggerModuleMethodSubject withAdditionalSources(JavaFileObject... sources) {
    this.additionalSources = ImmutableList.copyOf(sources);
    return this;
  }

  /**
   * Fails if compiling the module with the method doesn't report an error at the method
   * declaration whose message contains {@code errorSubstring}.
   */
  void hasError(String errorSubstring) {
    String source = moduleSource();
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule", source);
    Compilation compilation =
        daggerCompiler().compile(FluentIterable.from(additionalSources).append(module));
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(errorSubstring)
        .inFile(module)
        .onLine(methodLine(source));
  }

  private int methodLine(String source) {
    String beforeMethod = source.substring(0, source.indexOf(actual));
    int methodLine = 1;
    for (int nextNewlineIndex = beforeMethod.indexOf('\n');
        nextNewlineIndex >= 0;
        nextNewlineIndex = beforeMethod.indexOf('\n', nextNewlineIndex + 1)) {
      methodLine++;
    }
    return methodLine;
  }

  private String moduleSource() {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    writer.println("package test;");
    writer.println();
    for (String importLine : imports.build()) {
      writer.println(importLine);
    }
    writer.println();
    writer.printf(declaration, "TestModule", "\n" + actual + "\n");
    writer.println();
    return stringWriter.toString();
  }

}

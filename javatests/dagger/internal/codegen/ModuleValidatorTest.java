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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatModuleMethod;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.Module;
import dagger.producers.ProducerModule;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class ModuleValidatorTest {

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(new Object[][] {{ModuleType.MODULE}, {ModuleType.PRODUCER_MODULE}});
  }

  private enum ModuleType {
    MODULE(Module.class),
    PRODUCER_MODULE(ProducerModule.class),
    ;

    private final Class<? extends Annotation> annotation;

    ModuleType(Class<? extends Annotation> annotation) {
      this.annotation = annotation;
    }

    String annotationWithSubcomponent(String subcomponent) {
      return String.format("@%s(subcomponents = %s)", annotation.getSimpleName(), subcomponent);
    }

    String importStatement() {
      return String.format("import %s;", annotation.getName());
    }

    String simpleName() {
      return annotation.getSimpleName();
    }
  }

  private final ModuleType moduleType;

  public ModuleValidatorTest(ModuleType moduleType) {
    this.moduleType = moduleType;
  }

  @Test
  public void moduleSubcomponents_notASubcomponent() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("NotASubcomponent.class"),
            "class TestModule {}");
    JavaFileObject notASubcomponent =
        JavaFileObjects.forSourceLines(
            "test.NotASubcomponent", "package test;", "", "class NotASubcomponent {}");
    assertThat(module, notASubcomponent)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "test.NotASubcomponent is not a @Subcomponent or @ProductionSubcomponent")
        .in(module)
        .onLine(5);
  }

  @Test
  public void moduleSubcomponents_listsSubcomponentBuilder() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("Sub.Builder.class"),
            "class TestModule {}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Sub build();",
            "  }",
            "}");
    assertThat(module, subcomponent)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "test.Sub.Builder is a @Subcomponent.Builder. Did you mean to use test.Sub?")
        .in(module)
        .onLine(5);
  }

  @Test
  public void moduleSubcomponents_listsProductionSubcomponentBuilder() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("Sub.Builder.class"),
            "class TestModule {}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent",
            "interface Sub {",
            "  @ProductionSubcomponent.Builder",
            "  interface Builder {",
            "    Sub build();",
            "  }",
            "}");
    assertThat(module, subcomponent)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "test.Sub.Builder is a @ProductionSubcomponent.Builder. Did you mean to use test.Sub?")
        .in(module)
        .onLine(5);
  }

  @Test
  public void moduleSubcomponents_noSubcomponentBuilder() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("NoBuilder.class"),
            "class TestModule {}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.NoBuilder",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface NoBuilder {}");
    assertThat(module, subcomponent)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "test.NoBuilder doesn't have a @Subcomponent.Builder, which is required when used "
                + "with @"
                + moduleType.simpleName()
                + ".subcomponents")
        .in(module)
        .onLine(5);
  }

  @Test
  public void moduleSubcomponents_noProductionSubcomponentBuilder() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("NoBuilder.class"),
            "class TestModule {}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.NoBuilder",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent",
            "interface NoBuilder {}");
    assertThat(module, subcomponent)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "test.NoBuilder doesn't have a @ProductionSubcomponent.Builder, which is required "
                + "when used with @"
                + moduleType.simpleName()
                + ".subcomponents")
        .in(module)
        .onLine(5);
  }

  @Test
  public void moduleSubcomponentsAreTypes() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(subcomponents = int.class)",
            "class TestModule {}");
    assertThat(module)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("int is not a valid subcomponent type")
        .in(module)
        .onLine(5);
  }

  @Test
  public void tooManyAnnotations() {
    assertThatModuleMethod(
            "@BindsOptionalOf @Multibinds abstract Set<Object> tooManyAnnotations();")
        .hasError("is annotated with more than one of");
  }

  @Test
  public void invalidIncludedModule() {
    JavaFileObject badModule =
        JavaFileObjects.forSourceLines(
            "test.BadModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class BadModule {",
            "  @Binds abstract Object noParameters();",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.IncludesBadModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(includes = BadModule.class)",
            "abstract class IncludesBadModule {}");
    assertThat(daggerCompiler().compile(badModule, module))
        .hadErrorContaining("test.BadModule has errors")
        .inFile(module)
        .onLine(5);
  }

  @Test
  public void scopeOnModule() {
    JavaFileObject badModule =
        JavaFileObjects.forSourceLines(
            "test.BadModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Module",
            "interface BadModule {}");
    Compilation compilation = daggerCompiler().compile(badModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Modules cannot be scoped")
        .inFile(badModule)
        .onLineContaining("@Singleton");
  }

  @Test
  public void moduleIncludesSelfCycle() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "import dagger.Provides;",
            "",
            String.format("@%s(", moduleType.simpleName()),
            "  includes = {",
            "      TestModule.class, // first",
            "      OtherModule.class,",
            "      TestModule.class, // second",
            "  }",
            ")",
            "class TestModule {",
            "  @Provides int i() { return 0; }",
            "}");

    JavaFileObject otherModule =
        JavaFileObjects.forSourceLines(
            "test.OtherModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "class OtherModule {}");

    Compilation compilation = daggerCompiler().compile(module, otherModule);
    assertThat(compilation).failed();
    String error = String.format("@%s cannot include themselves", moduleType.simpleName());
    assertThat(compilation).hadErrorContaining(error).inFile(module).onLineContaining("Module(");
  }
}

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

import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatModuleMethod;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.Source;
import dagger.Module;
import dagger.producers.ProducerModule;
import dagger.testing.compile.CompilerTests;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class ModuleValidationTest {

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

  public ModuleValidationTest(ModuleType moduleType) {
    this.moduleType = moduleType;
  }

  @Test
  public void moduleSubcomponents_notASubcomponent() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("NotASubcomponent.class"),
            "class TestModule {}");
    Source notASubcomponent =
        CompilerTests.javaSource(
            "test.NotASubcomponent",
            "package test;",
            "",
            "class NotASubcomponent {}");
    CompilerTests.daggerCompiler(module, notASubcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.NotASubcomponent is not a @Subcomponent or @ProductionSubcomponent")
                  .onSource(module)
                  .onLine(5);
            });
  }

  @Test
  public void moduleSubcomponents_listsSubcomponentBuilder() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("Sub.Builder.class"),
            "class TestModule {}");
    Source subcomponent =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(module, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.Sub.Builder is a @Subcomponent.Builder. Did you mean to use test.Sub?")
                  .onSource(module)
                  .onLine(5);
            });
  }

  @Test
  public void moduleSubcomponents_listsSubcomponentFactory() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("Sub.Factory.class"),
            "class TestModule {}");
    Source subcomponent =
        CompilerTests.javaSource(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  @Subcomponent.Factory",
            "  interface Factory {",
            "    Sub creator();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(module, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.Sub.Factory is a @Subcomponent.Factory. Did you mean to use test.Sub?")
                  .onSource(module)
                  .onLine(5);
            });
  }

  @Test
  public void moduleSubcomponents_listsProductionSubcomponentBuilder() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("Sub.Builder.class"),
            "class TestModule {}");
    Source subcomponent =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(module, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.Sub.Builder is a @ProductionSubcomponent.Builder. "
                          + "Did you mean to use test.Sub?")
                  .onSource(module)
                  .onLine(5);
            });
  }

  @Test
  public void moduleSubcomponents_listsProductionSubcomponentFactory() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("Sub.Factory.class"),
            "class TestModule {}");
    Source subcomponent =
        CompilerTests.javaSource(
            "test.Sub",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent",
            "interface Sub {",
            "  @ProductionSubcomponent.Factory",
            "  interface Factory {",
            "    Sub create();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(module, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.Sub.Factory is a @ProductionSubcomponent.Factory. "
                          + "Did you mean to use test.Sub?")
                  .onSource(module)
                  .onLine(5);
            });
  }

  @Test
  public void moduleSubcomponents_noSubcomponentCreator() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("NoBuilder.class"),
            "class TestModule {}");
    Source subcomponent =
        CompilerTests.javaSource(
            "test.NoBuilder",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface NoBuilder {}");
    CompilerTests.daggerCompiler(module, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.NoBuilder doesn't have a @Subcomponent.Builder or "
                          + "@Subcomponent.Factory, which is required when used with @"
                          + moduleType.simpleName()
                          + ".subcomponents")
                  .onSource(module)
                  .onLine(5);
            });
  }

  @Test
  public void moduleSubcomponents_noProductionSubcomponentCreator() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            moduleType.importStatement(),
            "",
            moduleType.annotationWithSubcomponent("NoBuilder.class"),
            "class TestModule {}");
    Source subcomponent =
        CompilerTests.javaSource(
            "test.NoBuilder",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent",
            "interface NoBuilder {}");
    CompilerTests.daggerCompiler(module, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.NoBuilder doesn't have a @ProductionSubcomponent.Builder or "
                          + "@ProductionSubcomponent.Factory, which is required when used with @"
                          + moduleType.simpleName()
                          + ".subcomponents")
                  .onSource(module)
                  .onLine(5);
            });
  }

  @Test
  public void moduleSubcomponentsAreTypes() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(subcomponents = int.class)",
            "class TestModule {}");
    CompilerTests.daggerCompiler(module)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  subject.hasErrorContaining("int is not a valid subcomponent type")
                      .onSource(module)
                      .onLine(5);
                  break;
                case KSP:
                  // TODO(b/245954367): Remove this pathway once this bug is fixed.
                  // KSP interprets the int.class type as a boxed type so we get a slightly
                  // different error message for now.
                  subject.hasErrorContaining(
                          "java.lang.Integer is not a @Subcomponent or @ProductionSubcomponent")
                      .onSource(module)
                      .onLine(5);
                  break;
              }
            });
  }

  @Test
  public void tooManyAnnotations() {
    assertThatModuleMethod(
            "@BindsOptionalOf @Multibinds abstract Set<Object> tooManyAnnotations();")
        .hasError("is annotated with more than one of");
  }

  @Test
  public void invalidIncludedModule() {
    Source badModule =
        CompilerTests.javaSource(
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
    Source module =
        CompilerTests.javaSource(
            "test.IncludesBadModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(includes = BadModule.class)",
            "abstract class IncludesBadModule {}");
    CompilerTests.daggerCompiler(badModule, module)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("test.BadModule has errors")
                  .onSource(module)
                  .onLine(5);
              subject.hasErrorContaining(
                      "@Binds methods must have exactly one parameter, whose type is "
                          + "assignable to the return type")
                  .onSource(badModule)
                  .onLine(8);
            });
  }

  @Test
  public void scopeOnModule() {
    Source badModule =
        CompilerTests.javaSource(
            "test.BadModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Module",
            "interface BadModule {}");
    CompilerTests.daggerCompiler(badModule)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("@Modules cannot be scoped")
                  .onSource(badModule)
                  .onLineContaining("@Singleton");
            });
  }

  @Test
  public void moduleIncludesSelfCycle() {
    Source module =
        CompilerTests.javaSource(
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

    Source otherModule =
        CompilerTests.javaSource(
            "test.OtherModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "class OtherModule {}");

    CompilerTests.daggerCompiler(module, otherModule)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              String error =
                  String.format("@%s cannot include themselves", moduleType.simpleName());
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  subject.hasErrorContaining(error).onSource(module).onLineContaining("// first");
                  subject.hasErrorContaining(error).onSource(module).onLineContaining("// second");
                  break;
                case KSP:
                  // TODO(b/381557487): KSP2 reports the error on the wrong line.
                  subject.hasErrorContaining(error)
                      .onSource(module)
                      .onLineContaining("includes = {");
                  break;
              }
            });
  }

  // Regression test for b/264618194.
  @Test
  public void objectModuleInheritsInstanceBindingFails() {
    Source objectModule =
        CompilerTests.kotlinSource(
            "test.ObjectModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "object ObjectModule : ClassModule() {",
            "  @Provides fun provideString(): String = \"\"",
            "}");
    Source classModule =
        CompilerTests.kotlinSource(
            "test.ClassModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "abstract class ClassModule {",
            "  @Provides fun provideInt(): Int = 1",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [ObjectModule::class])",
            "interface TestComponent {",
            "  fun getInt(): Int",
            "  fun getString(): String",
            "}");

    CompilerTests.daggerCompiler(component, objectModule, classModule)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("test.ObjectModule has errors")
                  .onSource(component)
                  .onLineContaining("ObjectModule::class");
              subject.hasErrorContaining(
                      "@Module-annotated Kotlin object cannot inherit instance "
                          + "(i.e. non-abstract, non-JVM static) binding method: "
                          + "@Provides int test.ClassModule.provideInt()")
                  .onSource(objectModule)
                  .onLineContaining(
                      // TODO(b/267223703): KAPT incorrectly reports the error on the annotation.
                      CompilerTests.backend(subject) == XProcessingEnv.Backend.JAVAC
                          ? "@Module"
                          : "object ObjectModule");
            });
  }

  // Regression test for b/264618194.
  @Test
  public void objectModuleInheritsNonInstanceBindingSucceeds() {
    Source objectModule =
        CompilerTests.kotlinSource(
            "test.ObjectModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "object ObjectModule : ClassModule() {",
            "  @Provides fun provideString(): String = \"\"",
            "}");
    Source classModule =
        CompilerTests.javaSource(
            "test.ClassModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public abstract class ClassModule {",
            "  // A non-binding instance method is okay.",
            "  public int nonBindingMethod() {",
            "    return 1;",
            "  }",
            "",
            "  // A static binding method is also okay.",
            "  @Provides",
            "  public static int provideInt() {",
            "    return 1;",
            "  }",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [ObjectModule::class])",
            "interface TestComponent {",
            "  fun getInt(): Int",
            "  fun getString(): String",
            "}");
    CompilerTests.daggerCompiler(component, objectModule, classModule)
        .compile(subject -> subject.hasErrorCount(0));
  }
}

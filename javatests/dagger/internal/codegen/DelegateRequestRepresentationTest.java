/*
 * Copyright (C) 2017 The Dagger Authors.
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

import androidx.room.compiler.processing.util.Source;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DelegateRequestRepresentationTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public DelegateRequestRepresentationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final Source REGULAR_SCOPED =
      CompilerTests.javaSource(
          "test.RegularScoped",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "import javax.inject.Inject;",
          "",
          "@RegularScoped.CustomScope",
          "class RegularScoped {",
          "  @Inject RegularScoped() {}",
          "",
          "  @Scope @interface CustomScope {}",
          "}");

  private static final Source REUSABLE_SCOPED =
      CompilerTests.javaSource(
          "test.ReusableScoped",
          "package test;",
          "",
          "import dagger.Reusable;",
          "import javax.inject.Inject;",
          "",
          "@Reusable",
          "class ReusableScoped {",
          "  @Inject ReusableScoped() {}",
          "}");

  private static final Source UNSCOPED =
      CompilerTests.javaSource(
          "test.Unscoped",
          "package test;",
          "",
          "import javax.inject.Inject;",
          "",
          "class Unscoped {",
          "  @Inject Unscoped() {}",
          "}");

  private static final Source COMPONENT =
      CompilerTests.javaSource(
          "test.TestComponent",
          "package test;",
          "",
          "import dagger.Component;",
          "",
          "@Component(modules = TestModule.class)",
          "@RegularScoped.CustomScope",
          "interface TestComponent {",
          "  @Qualifier(RegularScoped.class)",
          "  Object regular();",
          "",
          "  @Qualifier(ReusableScoped.class)",
          "  Object reusable();",
          "",
          "  @Qualifier(Unscoped.class)",
          "  Object unscoped();",
          "}");

  private static final Source QUALIFIER =
      CompilerTests.javaSource(
          "test.Qualifier",
          "package test;",
          "",
          "@javax.inject.Qualifier",
          "@interface Qualifier {",
          "  Class<?> value();",
          "}");

  @Test
  public void toDoubleCheck() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds @RegularScoped.CustomScope @Qualifier(RegularScoped.class)",
            "  Object regular(RegularScoped delegate);",
            "",
            "  @Binds @RegularScoped.CustomScope @Qualifier(ReusableScoped.class)",
            "  Object reusable(ReusableScoped delegate);",
            "",
            "  @Binds @RegularScoped.CustomScope @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    CompilerTests.daggerCompiler(
            module, COMPONENT, QUALIFIER, REGULAR_SCOPED, REUSABLE_SCOPED, UNSCOPED)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void toSingleCheck() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Reusable;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds @Reusable @Qualifier(RegularScoped.class)",
            "  Object regular(RegularScoped delegate);",
            "",
            "  @Binds @Reusable @Qualifier(ReusableScoped.class)",
            "  Object reusable(ReusableScoped delegate);",
            "",
            "  @Binds @Reusable @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    CompilerTests.daggerCompiler(
            module, COMPONENT, QUALIFIER, REGULAR_SCOPED, REUSABLE_SCOPED, UNSCOPED)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void toUnscoped() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds @Qualifier(RegularScoped.class)",
            "  Object regular(RegularScoped delegate);",
            "",
            "  @Binds @Qualifier(ReusableScoped.class)",
            "  Object reusable(ReusableScoped delegate);",
            "",
            "  @Binds @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    CompilerTests.daggerCompiler(
            module, COMPONENT, QUALIFIER, REGULAR_SCOPED, REUSABLE_SCOPED, UNSCOPED)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void castNeeded_rawTypes_Provider_get() throws Exception {
    Source accessibleSupertype =
        CompilerTests.javaSource(
            "other.Supertype",
            "package other;",
            "",
            // accessible from the component, but the subtype is not
            "public interface Supertype {}");
    Source inaccessibleSubtype =
        CompilerTests.javaSource(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "class Subtype implements Supertype {",
            "  @Inject Subtype() {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "other.SupertypeModule",
            "package other;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "public interface SupertypeModule {",
            "  @Binds Supertype to(Subtype subtype);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = other.SupertypeModule.class)",
            "interface TestComponent {",
            "  other.Supertype supertype();",
            "}");

    CompilerTests.daggerCompiler(accessibleSupertype, inaccessibleSubtype, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void noCast_rawTypes_Provider_get_toInaccessibleType() throws Exception {
    Source supertype =
        CompilerTests.javaSource(
            "other.Supertype",
            "package other;",
            "",
            "interface Supertype {}");
    Source subtype =
        CompilerTests.javaSource(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "class Subtype implements Supertype {",
            "  @Inject Subtype() {}",
            "}");
    Source usesSupertype =
        CompilerTests.javaSource(
            "other.UsesSupertype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class UsesSupertype {",
            "  @Inject UsesSupertype(Supertype supertype) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "other.SupertypeModule",
            "package other;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "public interface SupertypeModule {",
            "  @Binds Supertype to(Subtype subtype);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = other.SupertypeModule.class)",
            "interface TestComponent {",
            "  other.UsesSupertype usesSupertype();",
            "}");

    CompilerTests.daggerCompiler(supertype, subtype, usesSupertype, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void castedToRawType() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String provideString() { return new String(); }",
            "",
            "  @Binds",
            "  CharSequence charSequence(String string);",
            "",
            "  @Binds",
            "  @Named(\"named\")",
            "  String namedString(String string);",
            "}");
    Source component =
         CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Named;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<CharSequence> charSequence();",
            "  CharSequence charSequenceInstance();",
            "",
            "  @Named(\"named\") Provider<String> namedString();",
            "}");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void doubleBinds() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String provideString() { return new String(); }",
            "",
            "  @Binds",
            "  CharSequence charSequence(String string);",
            "",
            "  @Binds",
            "  Object object(CharSequence charSequence);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Named;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<CharSequence> charSequence();",
            "  Provider<Object> object();",
            "}");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void inlineFactoryOfInacessibleType() throws Exception {
    Source supertype =
        CompilerTests.javaSource(
            "other.Supertype",
            "package other;",
            "",
            "public interface Supertype {}");
    Source injectableSubtype =
        CompilerTests.javaSource(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Subtype implements Supertype {",
            // important: this doesn't have any dependencies and therefore the factory will be able
            // to be referenced with an inline Subtype_Factory.create()
            "  @Inject Subtype() {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "other.TestModule",
            "package other;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "public interface TestModule {",
            "  @Binds Supertype to(Subtype subtype);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.RequestsSubtypeAsProvider",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = other.TestModule.class)",
            "interface RequestsSubtypeAsProvider {",
            "  Provider<other.Supertype> supertypeProvider();",
            "}");

    CompilerTests.daggerCompiler(supertype, injectableSubtype, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerRequestsSubtypeAsProvider"));
            });
  }

  @Test
  public void providerWhenBindsScopeGreaterThanDependencyScope() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Reusable;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "public abstract class TestModule {",
            "  @Reusable",
            "  @Provides",
            "  static String provideString() {",
            "    return \"\";",
            "  }",
            "",
            "  @Binds",
            "  @Singleton",
            "  abstract Object bindString(String str);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "import javax.inject.Provider;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Object> object();",
            "}");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }
}

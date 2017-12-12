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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.EXPERIMENTAL_ANDROID_MODE;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DelegateBindingExpressionTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public DelegateBindingExpressionTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final JavaFileObject REGULAR_SCOPED =
      JavaFileObjects.forSourceLines(
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

  private static final JavaFileObject REUSABLE_SCOPED =
      JavaFileObjects.forSourceLines(
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

  private static final JavaFileObject RELEASABLE_SCOPED =
      JavaFileObjects.forSourceLines(
          "test.ReleasableScoped",
          "package test;",
          "",
          "import dagger.releasablereferences.CanReleaseReferences;",
          "import javax.inject.Scope;",
          "import javax.inject.Inject;",
          "",
          "@ReleasableScoped.CustomScope",
          "class ReleasableScoped {",
          "  @Inject ReleasableScoped() {}",
          "",
          "  @CanReleaseReferences",
          "  @Scope @interface CustomScope {}",
          "}");

  private static final JavaFileObject UNSCOPED =
      JavaFileObjects.forSourceLines(
          "test.Unscoped",
          "package test;",
          "",
          "import javax.inject.Inject;",
          "",
          "class Unscoped {",
          "  @Inject Unscoped() {}",
          "}");

  private static final JavaFileObject COMPONENT =
      JavaFileObjects.forSourceLines(
          "test.TestComponent",
          "package test;",
          "",
          "import dagger.Component;",
          "import dagger.releasablereferences.ForReleasableReferences;",
          "import dagger.releasablereferences.ReleasableReferenceManager;",
          "",
          "@Component(modules = TestModule.class)",
          "@RegularScoped.CustomScope",
          "@ReleasableScoped.CustomScope",
          "interface TestComponent {",
          "  @Qualifier(RegularScoped.class)",
          "  Object regular();",
          "",
          "  @Qualifier(ReusableScoped.class)",
          "  Object reusable();",
          "",
          "  @Qualifier(ReleasableScoped.class)",
          "  Object releasable();",
          "",
          "  @Qualifier(Unscoped.class)",
          "  Object unscoped();",
          "",
          // force a reference releasing provider to be created
          "  @ForReleasableReferences(ReleasableScoped.CustomScope.class)",
          "  ReleasableReferenceManager releasableReferenceManager();",
          "}");

  private static final JavaFileObject QUALIFIER =
      JavaFileObjects.forSourceLines(
          "test.Qualifier",
          "package test;",
          "",
          "@javax.inject.Qualifier",
          "@interface Qualifier {",
          "  Class<?> value();",
          "}");

  @Test
  public void toDoubleCheck() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
            "  @Binds @RegularScoped.CustomScope @Qualifier(ReleasableScoped.class)",
            "  Object releasable(ReleasableScoped delegate);",
            "",
            "  @Binds @RegularScoped.CustomScope @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    assertThatCompilationWithModule(module)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GENERATED_ANNOTATION,
                    "public final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    EXPERIMENTAL_ANDROID_MODE,
                    "  private RegularScoped getRegularScoped() {",
                    "    Object local = regularScoped;",
                    "    if (local instanceof MemoizedSentinel) {",
                    "      synchronized (local) {",
                    "        if (local == regularScoped) {",
                    "          regularScoped = new RegularScoped();",
                    "        }",
                    "        local = regularScoped;",
                    "      }",
                    "    }",
                    "    return (RegularScoped) local;",
                    "  }",
                    "",
                    "  private ReusableScoped getReusableScoped() {",
                    "    if (reusableScoped instanceof MemoizedSentinel) {",
                    "      reusableScoped = new ReusableScoped();",
                    "    }",
                    "    return (ReusableScoped) reusableScoped;",
                    "  }",
                    "",
                    "  private Provider<ReusableScoped> getReusableScopedProvider() {",
                    "    return new Provider<ReusableScoped>() {",
                    "      @Override",
                    "      public ReusableScoped get() {",
                    "        return getReusableScoped();",
                    "      }",
                    "    };",
                    "  }",
                    "")
                .addLines(
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(final Builder builder) {")
                .addLinesIn(
                    EXPERIMENTAL_ANDROID_MODE,
                    "    this.reusableProvider = DoubleCheck.provider(",
                    "        (Provider) getReusableScopedProvider());")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    this.regularScopedProvider = ",
                    "        DoubleCheck.provider(RegularScoped_Factory.create());",
                    "    this.reusableScopedProvider = ",
                    "        SingleCheck.provider(ReusableScoped_Factory.create());",
                    "    this.reusableProvider = DoubleCheck.provider(",
                    "        (Provider) reusableScopedProvider);")
                .addLines(
                    "    this.releasableScopedProvider = ",
                    "         ReferenceReleasingProvider.create(",
                    "             ReleasableScoped_Factory.create(), customScopeReferences);",
                    "    this.releasableProvider = DoubleCheck.provider(",
                    "        (Provider) releasableScopedProvider);",
                    "    this.unscopedProvider = DoubleCheck.provider(",
                    "        (Provider) Unscoped_Factory.create());",
                    "    this.forReleasableReferencesReleasableReferenceManagerProvider =",
                    "        new Provider<ReleasableReferenceManager>() {",
                    "          @Override",
                    "          public ReleasableReferenceManager get() {",
                    "            return customScopeReferences;",
                    "          }",
                    "        };",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void toSingleCheck() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
            "  @Binds @Reusable @Qualifier(ReleasableScoped.class)",
            "  Object releasable(ReleasableScoped delegate);",
            "",
            "  @Binds @Reusable @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    assertThatCompilationWithModule(module)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GENERATED_ANNOTATION,
                    "public final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    EXPERIMENTAL_ANDROID_MODE,
                    "  private RegularScoped getRegularScoped() {",
                    "    Object local = regularScoped;",
                    "    if (local instanceof MemoizedSentinel) {",
                    "      synchronized (local) {",
                    "        if (local == regularScoped) {",
                    "          regularScoped = new RegularScoped();",
                    "        }",
                    "        local = regularScoped;",
                    "      }",
                    "    }",
                    "    return (RegularScoped) local;",
                    "  }",
                    "",
                    "  private ReusableScoped getReusableScoped() {",
                    "    if (reusableScoped instanceof MemoizedSentinel) {",
                    "      reusableScoped = new ReusableScoped();",
                    "    }",
                    "    return (ReusableScoped) reusableScoped;",
                    "  }",
                    "")
                .addLines(
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(final Builder builder) {")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    this.regularScopedProvider = ",
                    "        DoubleCheck.provider(RegularScoped_Factory.create());",
                    "    this.reusableScopedProvider = ",
                    "        SingleCheck.provider(ReusableScoped_Factory.create());")
                .addLines(
                    "    this.releasableScopedProvider = ",
                    "         ReferenceReleasingProvider.create(",
                    "             ReleasableScoped_Factory.create(), customScopeReferences);",
                    "    this.releasableProvider = SingleCheck.provider(",
                    "        (Provider) releasableScopedProvider);",
                    "    this.unscopedProvider = SingleCheck.provider(",
                    "        (Provider) Unscoped_Factory.create());",
                    "    this.forReleasableReferencesReleasableReferenceManagerProvider =",
                    "        new Provider<ReleasableReferenceManager>() {",
                    "          @Override",
                    "          public ReleasableReferenceManager get() {",
                    "            return customScopeReferences;",
                    "          }",
                    "        };",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void toReleasableCheck() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds @ReleasableScoped.CustomScope @Qualifier(RegularScoped.class)",
            "  Object regular(RegularScoped delegate);",
            "",
            "  @Binds @ReleasableScoped.CustomScope @Qualifier(ReusableScoped.class)",
            "  Object reusable(ReusableScoped delegate);",
            "",
            "  @Binds @ReleasableScoped.CustomScope @Qualifier(ReleasableScoped.class)",
            "  Object releasable(ReleasableScoped delegate);",
            "",
            "  @Binds @ReleasableScoped.CustomScope @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    assertThatCompilationWithModule(module)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GENERATED_ANNOTATION,
                    "public final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    EXPERIMENTAL_ANDROID_MODE,
                    "  private RegularScoped getRegularScoped() {",
                    "    Object local = regularScoped;",
                    "    if (local instanceof MemoizedSentinel) {",
                    "      synchronized (local) {",
                    "        if (local == regularScoped) {",
                    "          regularScoped = new RegularScoped();",
                    "        }",
                    "        local = regularScoped;",
                    "      }",
                    "    }",
                    "    return (RegularScoped) local;",
                    "  }",
                    "",
                    "  private ReusableScoped getReusableScoped() {",
                    "    if (reusableScoped instanceof MemoizedSentinel) {",
                    "      reusableScoped = new ReusableScoped();",
                    "    }",
                    "    return (ReusableScoped) reusableScoped;",
                    "  }",
                    "")
                .addLines(
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(final Builder builder) {")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    this.regularScopedProvider = ",
                    "        DoubleCheck.provider(RegularScoped_Factory.create());",
                    "    this.reusableScopedProvider = ",
                    "        SingleCheck.provider(ReusableScoped_Factory.create());")
                .addLines(
                    "    this.releasableScopedProvider = ",
                    "         ReferenceReleasingProvider.create(",
                    "             ReleasableScoped_Factory.create(), customScopeReferences);",
                    "    this.unscopedProvider =",
                    "        ReferenceReleasingProvider.create(",
                    "            (Provider) Unscoped_Factory.create(), customScopeReferences);",
                    "    this.forReleasableReferencesReleasableReferenceManagerProvider =",
                    "        new Provider<ReleasableReferenceManager>() {",
                    "          @Override",
                    "          public ReleasableReferenceManager get() {",
                    "            return customScopeReferences;",
                    "          }",
                    "        };",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void toUnscoped() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
            "  @Binds @Qualifier(ReleasableScoped.class)",
            "  Object releasable(ReleasableScoped delegate);",
            "",
            "  @Binds @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    assertThatCompilationWithModule(module)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GENERATED_ANNOTATION,
                    "public final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    EXPERIMENTAL_ANDROID_MODE,
                    "  private RegularScoped getRegularScoped() {",
                    "    Object local = regularScoped;",
                    "    if (local instanceof MemoizedSentinel) {",
                    "      synchronized (local) {",
                    "        if (local == regularScoped) {",
                    "          regularScoped = new RegularScoped();",
                    "        }",
                    "        local = regularScoped;",
                    "      }",
                    "    }",
                    "    return (RegularScoped) local;",
                    "  }",
                    "",
                    "  private ReusableScoped getReusableScoped() {",
                    "    if (reusableScoped instanceof MemoizedSentinel) {",
                    "      reusableScoped = new ReusableScoped();",
                    "    }",
                    "    return (ReusableScoped) reusableScoped;",
                    "  }",
                    "")
                .addLines(
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize(final Builder builder) {")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    this.regularScopedProvider = ",
                    "        DoubleCheck.provider(RegularScoped_Factory.create());",
                    "    this.reusableScopedProvider = ",
                    "        SingleCheck.provider(ReusableScoped_Factory.create());")
                .addLines(
                    "    this.releasableScopedProvider = ",
                    "         ReferenceReleasingProvider.create(",
                    "             ReleasableScoped_Factory.create(), customScopeReferences);",
                    "    this.forReleasableReferencesReleasableReferenceManagerProvider =",
                    "        new Provider<ReleasableReferenceManager>() {",
                    "          @Override",
                    "          public ReleasableReferenceManager get() {",
                    "            return customScopeReferences;",
                    "          }",
                    "        };",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void castNeeded_rawTypes_Provider_get() {
    JavaFileObject accessibleSupertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype",
            "package other;",
            "",
            // accessible from the component, but the subtype is not
            "public interface Supertype {}");
    JavaFileObject inaccessibleSubtype =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().compile(accessibleSupertype, inaccessibleSubtype, module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                "import dagger.internal.DoubleCheck;",
                IMPORT_GENERATED_ANNOTATION,
                "import javax.inject.Provider;",
                "import other.Subtype_Factory;",
                "import other.Supertype;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {",
                "  @SuppressWarnings(\"rawtypes\")",
                "  private Provider subtypeProvider;",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Builder builder) {",
                "    this.subtypeProvider = DoubleCheck.provider(Subtype_Factory.create());",
                "  }",
                "",
                "  @Override",
                "  public Supertype supertype() {",
                "    return (Supertype) subtypeProvider.get();",
                "  }",
                "}"));
  }

  @Test
  public void noCast_rawTypes_Provider_get_toInaccessibleType() {
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype",
            "package other;",
            "",
            "interface Supertype {}");
    JavaFileObject subtype =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject usesSupertype =
        JavaFileObjects.forSourceLines(
            "other.UsesSupertype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class UsesSupertype {",
            "  @Inject UsesSupertype(Supertype supertype) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().compile(supertype, subtype, usesSupertype, module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                "import other.Subtype_Factory;",
                "import other.UsesSupertype;",
                "import other.UsesSupertype_Factory;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {",
                "  @SuppressWarnings(\"rawtypes\")",
                "  private Provider subtypeProvider;",
                "",
                "  @Override",
                "  public UsesSupertype usesSupertype() {",
                //   can't cast the provider.get() to a type that's not accessible
                "    return UsesSupertype_Factory.newUsesSupertype(subtypeProvider.get());",
                "  }",
                "}"));
  }

  @Test
  public void castedToRawType() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "import javax.inject.Singleton;",
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
            "",
            "  @Binds",
            "  @Named(\"named\")",
            "  String namedString(String string);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
            "",
            "  @Named(\"named\") Provider<String> namedString();",
            "}");

    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerTestComponent implements TestComponent {",
                "  @Override",
                "  public Provider<CharSequence> charSequence() {",
                "    return (Provider) TestModule_ProvideStringFactory.create();",
                "  }",
                "",
                "  @Override",
                "  public Provider<Object> object() {",
                //   @Binds used twice, but no need to cast (Provider) (Provider)
                "    return (Provider) TestModule_ProvideStringFactory.create();",
                "  }",
                "",
                "  @Override",
                "  public Provider<String> namedString() {",
                "    return TestModule_ProvideStringFactory.create();",
                "  }",
                "}"));
  }

  @Test
  public void inlineFactoryOfInacessibleType() {
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype", "package other;", "", "public interface Supertype {}");
    JavaFileObject injectableSubtype =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().compile(supertype, injectableSubtype, module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRequestsSubtypeAsProvider")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerRequestsSubtypeAsProvider",
                "package test;",
                "",
                "import other.Subtype_Factory;",
                "import other.Supertype;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerRequestsSubtypeAsProvider ",
                "    implements RequestsSubtypeAsProvider {",
                "  @Override",
                "  public Provider<Supertype> supertypeProvider() {",
                "    return (Provider) Subtype_Factory.create();",
                "  }",
                "}"));
  }

  private CompilationSubject assertThatCompilationWithModule(JavaFileObject module) {
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(
                module,
                COMPONENT,
                QUALIFIER,
                REGULAR_SCOPED,
                REUSABLE_SCOPED,
                RELEASABLE_SCOPED,
                UNSCOPED);
    assertThat(compilation).succeeded();
    return assertThat(compilation);
  }
}

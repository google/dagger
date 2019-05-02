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

package dagger.internal.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SwitchingProviderTest {
  @Test
  public void switchingProviderTest() {
    ImmutableList.Builder<JavaFileObject> javaFileObjects = ImmutableList.builder();
    StringBuilder entryPoints = new StringBuilder();
    for (int i = 0; i <= 100; i++) {
      String bindingName = "Binding" + i;
      javaFileObjects.add(
          JavaFileObjects.forSourceLines(
              "test." + bindingName,
              "package test;",
              "",
              "import javax.inject.Inject;",
              "",
              "final class " + bindingName + " {",
              "  @Inject",
              "  " + bindingName + "() {}",
              "}"));
      entryPoints.append(String.format("  Provider<%1$s> get%1$sProvider();\n", bindingName));
    }

    javaFileObjects.add(
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface TestComponent {",
            entryPoints.toString(),
            "}"));

    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
                "package test;",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    private T get0() {",
                "      switch (id) {",
                "        case 0:  return (T) new Binding0();",
                "        case 1:  return (T) new Binding1();",
                "        case 2:  return (T) new Binding2();",
                "        case 3:  return (T) new Binding3();",
                "        case 4:  return (T) new Binding4();",
                "        case 5:  return (T) new Binding5();",
                "        case 6:  return (T) new Binding6();",
                "        case 7:  return (T) new Binding7();",
                "        case 8:  return (T) new Binding8();",
                "        case 9:  return (T) new Binding9();",
                "        case 10: return (T) new Binding10();",
                "        case 11: return (T) new Binding11();",
                "        case 12: return (T) new Binding12();",
                "        case 13: return (T) new Binding13();",
                "        case 14: return (T) new Binding14();",
                "        case 15: return (T) new Binding15();",
                "        case 16: return (T) new Binding16();",
                "        case 17: return (T) new Binding17();",
                "        case 18: return (T) new Binding18();",
                "        case 19: return (T) new Binding19();",
                "        case 20: return (T) new Binding20();",
                "        case 21: return (T) new Binding21();",
                "        case 22: return (T) new Binding22();",
                "        case 23: return (T) new Binding23();",
                "        case 24: return (T) new Binding24();",
                "        case 25: return (T) new Binding25();",
                "        case 26: return (T) new Binding26();",
                "        case 27: return (T) new Binding27();",
                "        case 28: return (T) new Binding28();",
                "        case 29: return (T) new Binding29();",
                "        case 30: return (T) new Binding30();",
                "        case 31: return (T) new Binding31();",
                "        case 32: return (T) new Binding32();",
                "        case 33: return (T) new Binding33();",
                "        case 34: return (T) new Binding34();",
                "        case 35: return (T) new Binding35();",
                "        case 36: return (T) new Binding36();",
                "        case 37: return (T) new Binding37();",
                "        case 38: return (T) new Binding38();",
                "        case 39: return (T) new Binding39();",
                "        case 40: return (T) new Binding40();",
                "        case 41: return (T) new Binding41();",
                "        case 42: return (T) new Binding42();",
                "        case 43: return (T) new Binding43();",
                "        case 44: return (T) new Binding44();",
                "        case 45: return (T) new Binding45();",
                "        case 46: return (T) new Binding46();",
                "        case 47: return (T) new Binding47();",
                "        case 48: return (T) new Binding48();",
                "        case 49: return (T) new Binding49();",
                "        case 50: return (T) new Binding50();",
                "        case 51: return (T) new Binding51();",
                "        case 52: return (T) new Binding52();",
                "        case 53: return (T) new Binding53();",
                "        case 54: return (T) new Binding54();",
                "        case 55: return (T) new Binding55();",
                "        case 56: return (T) new Binding56();",
                "        case 57: return (T) new Binding57();",
                "        case 58: return (T) new Binding58();",
                "        case 59: return (T) new Binding59();",
                "        case 60: return (T) new Binding60();",
                "        case 61: return (T) new Binding61();",
                "        case 62: return (T) new Binding62();",
                "        case 63: return (T) new Binding63();",
                "        case 64: return (T) new Binding64();",
                "        case 65: return (T) new Binding65();",
                "        case 66: return (T) new Binding66();",
                "        case 67: return (T) new Binding67();",
                "        case 68: return (T) new Binding68();",
                "        case 69: return (T) new Binding69();",
                "        case 70: return (T) new Binding70();",
                "        case 71: return (T) new Binding71();",
                "        case 72: return (T) new Binding72();",
                "        case 73: return (T) new Binding73();",
                "        case 74: return (T) new Binding74();",
                "        case 75: return (T) new Binding75();",
                "        case 76: return (T) new Binding76();",
                "        case 77: return (T) new Binding77();",
                "        case 78: return (T) new Binding78();",
                "        case 79: return (T) new Binding79();",
                "        case 80: return (T) new Binding80();",
                "        case 81: return (T) new Binding81();",
                "        case 82: return (T) new Binding82();",
                "        case 83: return (T) new Binding83();",
                "        case 84: return (T) new Binding84();",
                "        case 85: return (T) new Binding85();",
                "        case 86: return (T) new Binding86();",
                "        case 87: return (T) new Binding87();",
                "        case 88: return (T) new Binding88();",
                "        case 89: return (T) new Binding89();",
                "        case 90: return (T) new Binding90();",
                "        case 91: return (T) new Binding91();",
                "        case 92: return (T) new Binding92();",
                "        case 93: return (T) new Binding93();",
                "        case 94: return (T) new Binding94();",
                "        case 95: return (T) new Binding95();",
                "        case 96: return (T) new Binding96();",
                "        case 97: return (T) new Binding97();",
                "        case 98: return (T) new Binding98();",
                "        case 99: return (T) new Binding99();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    private T get1() {",
                "      switch (id) {",
                "        case 100: return (T) new Binding100();",
                "        default:  throw new AssertionError(id);",
                "      }",
                "    }",
                "",
                "    @Override",
                "    public T get() {",
                "      switch (id / 100) {",
                "        case 0:  return get0();",
                "        case 1:  return get1();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}");

    Compilation compilation = compilerWithAndroidMode().compile(javaFileObjects.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void unscopedBinds() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
            "  static String s() {",
            "    return new String();",
            "  }",
            "",
            "  @Binds CharSequence c(String s);",
            "  @Binds Object o(CharSequence c);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Object> objectProvider();",
            "  Provider<CharSequence> charSequenceProvider();",
            "}");

    Compilation compilation = compilerWithAndroidMode().compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  private volatile Provider<String> sProvider;",
                "",
                "  private Provider<String> getStringProvider() {",
                "    Object local = sProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(0);",
                "      sProvider = (Provider<String>) local;",
                "    }",
                "    return (Provider<String>) local;",
                "  }",
                "",
                "  @Override",
                "  public Provider<Object> objectProvider() {",
                "    return (Provider) getStringProvider();",
                "  }",
                "",
                "  @Override",
                "  public Provider<CharSequence> charSequenceProvider() {",
                "    return (Provider) getStringProvider();",
                "  }",
                "",
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0:",
                "          return (T) TestModule_SFactory.s();",
                "        default:",
                "          throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}"));
  }

  @Test
  public void scopedBinds() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String s() {",
            "    return new String();",
            "  }",
            "",
            "  @Binds @Singleton Object o(CharSequence s);",
            "  @Binds @Singleton CharSequence c(String s);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Object> objectProvider();",
            "  Provider<CharSequence> charSequenceProvider();",
            "}");

    Compilation compilation = compilerWithAndroidMode().compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  private volatile Object charSequence = new MemoizedSentinel();",
                "  private volatile Provider<CharSequence> cProvider;",
                "",
                "  private CharSequence getCharSequence() {",
                "    Object local = charSequence;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = charSequence;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local = TestModule_SFactory.s();",
                "          charSequence = DoubleCheck.reentrantCheck(charSequence, local);",
                "        }",
                "      }",
                "    }",
                "    return (CharSequence) local;",
                "  }",
                "",
                "  @Override",
                "  public Provider<Object> objectProvider() {",
                "    return (Provider) charSequenceProvider();",
                "  }",
                "",
                "  @Override",
                "  public Provider<CharSequence> charSequenceProvider() {",
                "    Object local = cProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(0);",
                "      cProvider = (Provider<CharSequence>) local;",
                "    }",
                "    return (Provider<CharSequence>) local;",
                "  }",
                "",
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0:",
                "          return (T) DaggerTestComponent.this.getCharSequence();",
                "        default:",
                "          throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}"));
  }

  @Test
  public void emptyMultibindings_avoidSwitchProviders() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.multibindings.Multibinds;",
            "import dagger.Module;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Multibinds Set<String> set();",
            "  @Multibinds Map<String, String> map();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Set<String>> setProvider();",
            "  Provider<Map<String, String>> mapProvider();",
            "}");

    Compilation compilation = compilerWithAndroidMode().compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  @Override",
                "  public Provider<Set<String>> setProvider() {",
                "    return SetFactory.<String>empty();",
                "  }",
                "",
                "  @Override",
                "  public Provider<Map<String, String>> mapProvider() {",
                "    return MapFactory.<String, String>emptyMapProvider();",
                "  }",
                "}"));
  }

  @Test
  public void memberInjectors() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "class Foo {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Provider<MembersInjector<Foo>> providerOfMembersInjector();",
            "}");

    Compilation compilation = compilerWithAndroidMode().compile(foo, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  private Provider<MembersInjector<Foo>> fooMembersInjectorProvider;",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.fooMembersInjectorProvider = ",
                "        InstanceFactory.create(MembersInjectors.<Foo>noOp());",
                "  }",
                "",
                "  @Override",
                "  public Provider<MembersInjector<Foo>> providerOfMembersInjector() {",
                "    return fooMembersInjectorProvider;",
                "  }",
                "}"));
  }

  @Test
  public void optionals() {
    JavaFileObject present =
        JavaFileObjects.forSourceLines(
            "test.Present",
            "package test;",
            "",
            "class Present {}");
    JavaFileObject absent =
        JavaFileObjects.forSourceLines(
            "test.Absent",
            "package test;",
            "",
            "class Absent {}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @BindsOptionalOf Present bindOptionalOfPresent();",
            "  @BindsOptionalOf Absent bindOptionalOfAbsent();",
            "",
            "  @Provides static Present p() { return new Present(); }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Optional;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Optional<Present>> providerOfOptionalOfPresent();",
            "  Provider<Optional<Absent>> providerOfOptionalOfAbsent();",
            "}");

    Compilation compilation = compilerWithAndroidMode().compile(present, absent, module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.DaggerTestComponent",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  @SuppressWarnings(\"rawtypes\")",
                "  private static final Provider ABSENT_JDK_OPTIONAL_PROVIDER =",
                "      InstanceFactory.create(Optional.empty());",
                "",
                "  private volatile Provider<Optional<Present>> optionalOfPresentProvider;",
                "",
                "  private Provider<Optional<Absent>> optionalOfAbsentProvider;",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.optionalOfAbsentProvider = absentJdkOptionalProvider();",
                "  }",
                "",
                "  @Override",
                "  public Provider<Optional<Present>> providerOfOptionalOfPresent() {",
                "    Object local = optionalOfPresentProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(0);",
                "      optionalOfPresentProvider = (Provider<Optional<Present>>) local;",
                "    }",
                "    return (Provider<Optional<Present>>) local;",
                "  }",
                "",
                "  @Override",
                "  public Provider<Optional<Absent>> providerOfOptionalOfAbsent() {",
                "    return optionalOfAbsentProvider;",
                "  }",
                "",
                "  private static <T> Provider<Optional<T>> absentJdkOptionalProvider() {",
                "    @SuppressWarnings(\"unchecked\")",
                "    Provider<Optional<T>> provider = ",
                "          (Provider<Optional<T>>) ABSENT_JDK_OPTIONAL_PROVIDER;",
                "    return provider;",
                "  }",
                "",
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: // java.util.Optional<test.Present>",
                "          return (T) Optional.of(TestModule_PFactory.p());",
                "        default:",
                "          throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}"));
  }

  private Compiler compilerWithAndroidMode() {
    return javac()
        .withProcessors(new ComponentProcessor())
        .withOptions(CompilerMode.FAST_INIT_MODE.javacopts());
  }
}

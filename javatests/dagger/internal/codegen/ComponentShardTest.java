/*
 * Copyright (C) 2020 The Dagger Authors.
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
import static dagger.internal.codegen.GeneratedLines.GENERATED_CODE_ANNOTATIONS;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ComponentShardTest {
  private static final int BINDINGS_PER_SHARD = 10;

  @Test
  public void testNewShardCreated() {
    // Create 2N + 1 bindings: N in DaggerTestComponent, N in Shard1, and 1 in Shard2
    int numBindings = 2 * BINDINGS_PER_SHARD + 1;
    ImmutableList.Builder<JavaFileObject> javaFileObjects = ImmutableList.builder();
    ImmutableList.Builder<String> entryPoints = ImmutableList.builder();
    for (int i = 0; i < numBindings; i++) {
      String bindingName = "Binding" + i;
      entryPoints.add(String.format("%1$s get%1$s();", bindingName));
      entryPoints.add(String.format("Provider<%1$s> get%1$sProvider();", bindingName));

      // Add dependencies between main component and shard1: 9 -> 10 -> Provider<9>
      // Add dependencies between shard1 and shard2:        19 -> 20 -> Provider<19>
      switch (i) {
        case 9:
          javaFileObjects.add(createBinding(bindingName, "Binding10 dep"));
          break;
        case 10:
          javaFileObjects.add(createBinding(bindingName, "Provider<Binding9> dep"));
          break;
        case 19:
          javaFileObjects.add(createBinding(bindingName, "Binding20 dep"));
          break;
        case 20:
          javaFileObjects.add(createBinding(bindingName, "Provider<Binding19> dep"));
          break;
        default:
          javaFileObjects.add(createBinding(bindingName));
          break;
      }
    }

    javaFileObjects.add(createComponent(entryPoints.build()));

    // This generated component shows a couple things:
    //   1. Binding locations:
    //     * Binding #9 belongs to DaggerTestComponent
    //     * Binding #10 belongs to Shard1
    //     * Binding #20 belongs to Shard2
    //   2. DaggerTestComponent entry point methods:
    //     * Binding #9 implementation is inlined DaggerTestComponent.
    //     * Binding #10 implementation is delegated to Shard1.
    //     * Binding #20 implementation is delegated to Shard2.
    //   3. Dependencies between component and shard:
    //     * Binding #9 in DaggerTestComponent depends on #10 in Shard1.
    //     * Binding #10 in Shard1 depends on Provider<#9> in DaggerTestComponent.
    //   4. Dependencies between shard and shard:
    //     * Binding #19 in Shard1 depends on #20 in Shard2.
    //     * Binding #20 in Shard2 depends on Provider<#19> in Shard1.
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.DaggerTestComponent",
                "package dagger.internal.codegen;",
            GENERATED_CODE_ANNOTATIONS,
                "final class DaggerTestComponent implements TestComponent {",
                "  private final Shard1 shard1 = new Shard1();",
                "",
                "  private volatile Provider<Binding9> binding9Provider;",
                "",
                "  private volatile Object binding9 = new MemoizedSentinel();",
                "",
                "  @Override",
                "  public Binding9 getBinding9() {",
                "    Object local = binding9;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = binding9;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local = new Binding9(DaggerTestComponent.this.shard1.binding10());",
                "          binding9 = DoubleCheck.reentrantCheck(binding9, local);",
                "        }",
                "      }",
                "    }",
                "    return (Binding9) local;",
                "  }",
                "",
                "  @Override",
                "  public Provider<Binding9> getBinding9Provider() {",
                "    Object local = binding9Provider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(9);",
                "      binding9Provider = (Provider<Binding9>) local;",
                "    }",
                "    return (Provider<Binding9>) local;",
                "  }",
                "",
                "  @Override",
                "  public Binding10 getBinding10() {",
                "    return DaggerTestComponent.this.shard1.binding10();",
                "  }",
                "",
                "  @Override",
                "  public Provider<Binding10> getBinding10Provider() {",
                "    return DaggerTestComponent.this.shard1.binding10Provider();",
                "  }",
                "",
                "  @Override",
                "  public Binding20 getBinding20() {",
                "    return DaggerTestComponent.this.shard2.binding20();",
                "  }",
                "",
                "  @Override",
                "  public Provider<Binding20> getBinding20Provider() {",
                "    return DaggerTestComponent.this.shard2.binding20Provider();",
                "  }",
                "",
                "  private final class Shard1 {",
                "    private volatile Object binding10 = new MemoizedSentinel();",
                "",
                "    private volatile Provider<Binding10> binding10Provider;",
                "",
                "    private volatile Provider<Binding19> binding19Provider;",
                "",
                "    private volatile Object binding19 = new MemoizedSentinel();",
                "",
                "    private Binding10 binding10() {",
                "      Object local = binding10;",
                "      if (local instanceof MemoizedSentinel) {",
                "        synchronized (local) {",
                "          local = binding10;",
                "          if (local instanceof MemoizedSentinel) {",
                "            local = new Binding10(",
                "                DaggerTestComponent.this.getBinding9Provider());",
                "            binding10 = DoubleCheck.reentrantCheck(binding10, local);",
                "          }",
                "        }",
                "      }",
                "      return (Binding10) local;",
                "    }",
                "",
                "    private Provider<Binding10> binding10Provider() {",
                "      Object local = binding10Provider;",
                "      if (local == null) {",
                "        local = new SwitchingProvider<>(10);",
                "        binding10Provider = (Provider<Binding10>) local;",
                "      }",
                "      return (Provider<Binding10>) local;",
                "    }",
                "",
                "    private Provider<Binding19> binding19Provider() {",
                "      Object local = binding19Provider;",
                "      if (local == null) {",
                "        local = new SwitchingProvider<>(19);",
                "        binding19Provider = (Provider<Binding19>) local;",
                "      }",
                "      return (Provider<Binding19>) local;",
                "    }",
                "",
                "    private Binding19 binding19() {",
                "      Object local = binding19;",
                "      if (local instanceof MemoizedSentinel) {",
                "        synchronized (local) {",
                "          local = binding19;",
                "          if (local instanceof MemoizedSentinel) {",
                "            local = new Binding19(DaggerTestComponent.this.shard2.binding20());",
                "            binding19 = DoubleCheck.reentrantCheck(binding19, local);",
                "          }",
                "        }",
                "      }",
                "      return (Binding19) local;",
                "    }",
                "  }",
                "",
                "  private final class Shard2 {",
                "    private volatile Object binding20 = new MemoizedSentinel();",
                "",
                "    private volatile Provider<Binding20> binding20Provider;",
                "",
                "    private Binding20 binding20() {",
                "      Object local = binding20;",
                "      if (local instanceof MemoizedSentinel) {",
                "        synchronized (local) {",
                "          local = binding20;",
                "          if (local instanceof MemoizedSentinel) {",
                "            local = new Binding20(",
                "                DaggerTestComponent.this.shard1.binding19Provider());",
                "            binding20 = DoubleCheck.reentrantCheck(binding20, local);",
                "          }",
                "        }",
                "      }",
                "      return (Binding20) local;",
                "    }",
                "",
                "    private Provider<Binding20> binding20Provider() {",
                "      Object local = binding20Provider;",
                "      if (local == null) {",
                "        local = new SwitchingProvider<>(20);",
                "        binding20Provider = (Provider<Binding20>) local;",
                "      }",
                "      return (Provider<Binding20>) local;",
                "    }",
                "  }",
                "}");

    Compilation compilation = compilerWithAndroidMode().compile(javaFileObjects.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("dagger.internal.codegen.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  private static JavaFileObject createBinding(String bindingName, String... deps) {
    return JavaFileObjects.forSourceLines(
        "dagger.internal.codegen." + bindingName,
        "package dagger.internal.codegen;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "final class " + bindingName + " {",
        "  @Inject",
        "  " + bindingName + "(" + Arrays.stream(deps).collect(joining(", ")) + ") {}",
        "}");
  }

  private static JavaFileObject createComponent(ImmutableList<String> entryPoints) {
    return JavaFileObjects.forSourceLines(
        "dagger.internal.codegen.TestComponent",
        "package dagger.internal.codegen;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "@Component",
        "interface TestComponent {",
        "  " + entryPoints.stream().collect(joining("\n  ")),
        "}");
  }

  private static Compiler compilerWithAndroidMode() {
    return javac()
        .withProcessors(new ComponentProcessor())
        .withOptions(
            ImmutableSet.builder()
                .add("-Adagger.keysPerComponentShard=" + BINDINGS_PER_SHARD)
                .addAll(CompilerMode.FAST_INIT_MODE.javacopts())
                .build());
  }
}

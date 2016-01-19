/**
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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
package dagger.tests.integration.operation;

import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.ComponentProcessor;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static java.util.Arrays.asList;

@RunWith(JUnit4.class)
public final class PrimitiveInjectionTest {

  // TODO(cgruber): Use @test.ForTest to qualify primitives once qualifier equivalence is working.
  /*
  JavaFileObject annotation = JavaFileObjects.forSourceLines("test.ForTest",
      "package test;",
      "import javax.inject.Qualifier;",
      "@Qualifier",
      "public @interface ForTest {",
      "}");
  */

  // TODO(cgruber): Expand test to support more primitive types when b/15512877 is fixed.
  JavaFileObject primitiveInjectable = JavaFileObjects.forSourceLines("test.PrimitiveInjectable",
      "package test;",
      "import javax.inject.Inject;",
      "class PrimitiveInjectable {",
      "  @Inject PrimitiveInjectable(int ignored) {}",
      "}");

  JavaFileObject primitiveModule = JavaFileObjects.forSourceLines("test.PrimitiveModule",
      "package test;",
      "import dagger.Module;",
      "import dagger.Provides;",
      "@Module",
      "class PrimitiveModule {",
      "  @Provides int primitiveInt() { return Integer.MAX_VALUE; }",
      "}");

  JavaFileObject component = JavaFileObjects.forSourceLines("test.PrimitiveComponent",
      "package test;",
      "import dagger.Component;",
      "import dagger.Provides;",
      "@Component(modules = PrimitiveModule.class)",
      "interface PrimitiveComponent {",
      "  int primitiveInt();",
      "  PrimitiveInjectable primitiveInjectable();",
      "}");

  JavaFileObject expectedComponent = JavaFileObjects.forSourceLines(
      "test.DaggerPrimitiveComponent",
      "package test;",
      "",
      "import javax.annotation.Generated;",
      "import javax.inject.Provider;",
      "",
      GENERATED_ANNOTATION,
      "public final class DaggerPrimitiveComponent implements PrimitiveComponent {",
      "  private Provider<Integer> primitiveIntProvider;",
      "  private Provider<PrimitiveInjectable> primitiveInjectableProvider;",
      "",
      "  private DaggerPrimitiveComponent(Builder builder) {",
      "    assert builder != null;",
      "    initialize(builder);",
      "  }",
      "",
      "  public static Builder builder() {",
      "    return new Builder();",
      "  }",
      "",
      "  public static PrimitiveComponent create() {",
      "    return builder().build();",
      "  }",
      "",
      "  @SuppressWarnings(\"unchecked\")",
      "  private void initialize(final Builder builder) {",
      "    this.primitiveIntProvider =",
      "        PrimitiveModule_PrimitiveIntFactory.create(builder.primitiveModule);",
      "    this.primitiveInjectableProvider =",
      "        PrimitiveInjectable_Factory.create(primitiveIntProvider);",
      "  }",
      "",
      "  @Override",
      "  public int primitiveInt() {",
      "    return primitiveIntProvider.get();",
      "  }",
      "",
      "  @Override",
      "  public PrimitiveInjectable primitiveInjectable() {",
      "    return primitiveInjectableProvider.get();",
      "  }",
      "",
      "  public static final class Builder {",
      "    private PrimitiveModule primitiveModule;",
      "",
      "    private Builder() {",
      "    }",
      "",
      "    public PrimitiveComponent build() {",
      "      if (primitiveModule == null) {",
      "        this.primitiveModule = new PrimitiveModule();",
      "      }",
      "      return new DaggerPrimitiveComponent(this);",
      "    }",
      "",
      "    public Builder primitiveModule(PrimitiveModule primitiveModule) {",
      "      if (primitiveModule == null) {",
      "        throw new NullPointerException();",
      "      }",
      "      this.primitiveModule = primitiveModule;",
      "      return this;",
      "    }",
      "  }",
      "}");

  @Test public void primitiveArrayTypesAllInjected() {
    assert_().about(javaSources())
        .that(asList(component, primitiveInjectable, primitiveModule))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(expectedComponent);
  }
}

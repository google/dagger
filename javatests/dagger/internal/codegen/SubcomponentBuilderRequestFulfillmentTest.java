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
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SubcomponentBuilderRequestFulfillmentTest {
  @Test
  public void testInlinedSubcomponentBuilders_componentMethod() {
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
    JavaFileObject usesSubcomponent =
        JavaFileObjects.forSourceLines(
            "test.UsesSubcomponent",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class UsesSubcomponent {",
            "  @Inject UsesSubcomponent(Sub.Builder subBuilder) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C {",
            "  Sub.Builder sBuilder();",
            "  UsesSubcomponent usesSubcomponent();",
            "}");

    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerC",
            "package test;",
            "",
            "import javax.annotation.Generated;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerC implements C {",
            "  private DaggerC(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static C create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Sub.Builder sBuilder() {",
            "    return new SubBuilder();",
            "  }",
            "",
            "  @Override",
            "  public UsesSubcomponent usesSubcomponent() {",
            "    return new UsesSubcomponent(new SubBuilder());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public C build() {",
            "      return new DaggerC(this);",
            "    }",
            "  }",
            "",
            "  private final class SubBuilder implements Sub.Builder {",
            "    @Override",
            "    public Sub build() {",
            "      return new SubImpl(this);",
            "    }",
            "  }",
            "",
            "  private final class SubImpl implements Sub {",
            "    private SubImpl(SubBuilder builder) {}",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(subcomponent, usesSubcomponent, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerC")
        .hasSourceEquivalentTo(generatedComponent);
  }
}

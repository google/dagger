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

import static com.google.common.collect.Sets.cartesianProduct;
import static com.google.common.collect.Sets.immutableEnumSet;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.ComponentCreatorAnnotation.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.ComponentCreatorAnnotation.SUBCOMPONENT_FACTORY;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.testing.compile.Compilation;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SubcomponentCreatorRequestFulfillmentTest extends ComponentCreatorTestHelper {
  @Parameters(name = "compilerMode={0}, creatorKind={1}")
  public static Collection<Object[]> parameters() {
    Set<List<Object>> params =
        cartesianProduct(
            immutableEnumSet(DEFAULT_MODE, FAST_INIT_MODE),
            immutableEnumSet(SUBCOMPONENT_FACTORY, SUBCOMPONENT_BUILDER));
    return ImmutableList.copyOf(Iterables.transform(params, Collection::toArray));
  }

  public SubcomponentCreatorRequestFulfillmentTest(
      CompilerMode compilerMode, ComponentCreatorAnnotation componentCreatorAnnotation) {
    super(compilerMode, componentCreatorAnnotation);
  }

  @Test
  public void testInlinedSubcomponentCreators_componentMethod() {
    JavaFileObject subcomponent =
        preprocessedJavaFile(
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
        preprocessedJavaFile(
            "test.UsesSubcomponent",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class UsesSubcomponent {",
            "  @Inject UsesSubcomponent(Sub.Builder subBuilder) {}",
            "}");
    JavaFileObject component =
        preprocessedJavaFile(
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
        preprocessedJavaFile(
            "test.DaggerC",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerC implements C {",
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
            "  private final class SubBuilder implements Sub.Builder {",
            "    @Override",
            "    public Sub build() {",
            "      return new SubImpl();",
            "    }",
            "  }",
            "",
            "  private final class SubImpl implements Sub {",
            "    private SubImpl() {}",
            "  }",
            "}");

    Compilation compilation = compile(subcomponent, usesSubcomponent, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerC")
        .containsElementsIn(generatedComponent);
  }
}

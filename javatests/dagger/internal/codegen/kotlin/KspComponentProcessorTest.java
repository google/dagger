/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal.codegen.kotlin;

import static dagger.testing.compile.CompilerTests.compile;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KspComponentProcessorTest {
  @Test
  public void emptyComponentTest() throws Exception {
    Source componentSrc =
        Source.Companion.kotlin(
            "MyComponent.kt",
            String.join(
                "\n",
                "package test",
                "",
                "import dagger.BindsInstance",
                "import dagger.Component",
                "",
                "@Component",
                "interface MyComponent {}"));

    compile(
        ImmutableList.of(componentSrc),
        subject -> {
          subject.hasErrorCount(0);
          subject.generatedSource(
              Source.Companion.java(
                  "test/DaggerMyComponent",
                  String.join(
                      "\n",
                      "package test;",
                      "",
                      "import dagger.internal.DaggerGenerated;",
                      "import javax.annotation.processing.Generated;",
                      "",
                      "@DaggerGenerated",
                      "@Generated(",
                      "    value = \"dagger.internal.codegen.ComponentProcessor\",",
                      "    comments = \"https://dagger.dev\"",
                      ")",
                      "@SuppressWarnings({",
                      "    \"unchecked\",",
                      "    \"rawtypes\"",
                      "})",
                      "public final class DaggerMyComponent {",
                      "  private DaggerMyComponent() {",
                      "  }",
                      "",
                      "  public static Builder builder() {",
                      "    return new Builder();",
                      "  }",
                      "",
                      "  public static MyComponent create() {",
                      "    return new Builder().build();",
                      "  }",
                      "",
                      "  public static final class Builder {",
                      "    private Builder() {",
                      "    }",
                      "",
                      "    public MyComponent build() {",
                      "      return new MyComponentImpl();",
                      "    }",
                      "  }",
                      "",
                      "  private static final class MyComponentImpl implements MyComponent {",
                      "    private final MyComponentImpl myComponentImpl = this;",
                      "",
                      "    private MyComponentImpl() {",
                      "",
                      "",
                      "    }",
                      "  }",
                      "}")));
        });
  }
}

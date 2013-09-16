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
package dagger.tests.integration.validation;

import com.google.common.base.Joiner;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public final class LibraryModuleTest {
  @Test public void unusedProviderMethodsPassOnLibrary() {
    JavaFileObject source = JavaFileObjects.forSourceString("Library", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.ObjectGraph;",
        "import dagger.Provides;",
        "import java.lang.Override;",
        "@Module(library = true)",
        "class TestModule {",
        "  @Provides String string() {",
        "    return \"string\";",
        "  }",
        "}"));
    ASSERT.about(javaSource())
        .that(source).processedWith(daggerProcessors()).compilesWithoutError();
  }

  @Test public void unusedProviderMethodsFailOnNonLibrary() {
    JavaFileObject source = JavaFileObjects.forSourceString("Library", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.ObjectGraph;",
        "import dagger.Provides;",
        "import java.lang.Override;",
        "@Module(library = false)",
        "class TestModule {",
        "  @Provides String string() {",
        "    return \"string\";",
        "  }",
        "}"));
    ASSERT.about(javaSource()).that(source).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining("Graph validation failed:").in(source).onLine(6).and()
        .withErrorContaining("You have these unused @Provider methods:").in(source).onLine(6).and()
        .withErrorContaining("1. TestModule.string()").in(source).onLine(6).and()
        .withErrorContaining("Set library=true in your module").in(source).onLine(6);
  }
}

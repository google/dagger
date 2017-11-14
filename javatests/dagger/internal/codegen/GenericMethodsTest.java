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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GenericMethodsTest {
  @Test
  public void parameterizedComponentMethods() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import java.util.Set;",
            "",
            "@Component",
            "interface TestComponent {",
            "  <T1> void injectTypeVariable(T1 type);",
            "  <T2> MembersInjector<T2> membersInjector();",
            "  <T3> Set<T3> setOfT();",
            "  <UNUSED> TestComponent unused();",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("cannot have type variables")
        .inFile(component)
        .onLineContaining("<T1>");
    assertThat(compilation)
        .hadErrorContaining("cannot have type variables")
        .inFile(component)
        .onLineContaining("<T2>");
    assertThat(compilation)
        .hadErrorContaining("cannot have type variables")
        .inFile(component)
        .onLineContaining("<T3>");
    assertThat(compilation)
        .hadErrorContaining("cannot have type variables")
        .inFile(component)
        .onLineContaining("<UNUSED>");
  }
}

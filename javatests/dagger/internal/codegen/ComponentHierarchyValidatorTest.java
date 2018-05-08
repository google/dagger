/*
 * Copyright (C) 2016 The Dagger Authors.
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

/** Tests for {ComponentHierarchyValidator}. */
@RunWith(JUnit4.class)
public class ComponentHierarchyValidatorTest {
  @Test
  public void singletonSubcomponent() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Subcomponent",
            "interface Child {}");

    Compilation compilation = daggerCompiler().compile(component, subcomponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("conflicting scopes");
    assertThat(compilation).hadErrorContaining("test.Parent also has @Singleton");

    Compilation withoutScopeValidation =
        daggerCompiler()
            .withOptions("-Adagger.disableInterComponentScopeValidation=none")
            .compile(component, subcomponent);
    assertThat(withoutScopeValidation).succeeded();
  }
  
  @Test
  public void productionComponents_productionScopeImplicitOnBoth() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "  Object productionScopedObject();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.ProductionScope;",
            "",
            "@ProducerModule",
            "class ParentModule {",
            "  @Provides @ProductionScope Object parentScopedObject() { return new Object(); }",
            "}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String productionScopedString();",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.ProductionScope;",
            "",
            "@ProducerModule",
            "class ChildModule {",
            "  @Provides @ProductionScope String childScopedString() { return new String(); }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(component, subcomponent, parentModule, childModule);
    assertThat(compilation).succeeded();
  }
}

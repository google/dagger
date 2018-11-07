/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.android.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.ComponentProcessor;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DuplicateAndroidInjectorsCheckerTest {
  @Test
  public void conflictingMapKeys() {
    JavaFileObject activity =
        JavaFileObjects.forSourceLines(
            "test.TestActivity",
            "package test;",
            "",
            "import android.app.Activity;",
            "",
            "public class TestActivity extends Activity {}");
    JavaFileObject injectorFactory =
        JavaFileObjects.forSourceLines(
            "test.TestInjectorFactory",
            "package test;",
            "",
            "import dagger.android.AndroidInjector;",
            "import javax.inject.Inject;",
            "",
            "class TestInjectorFactory implements AndroidInjector.Factory<TestActivity> {",
            "  @Inject TestInjectorFactory() {}",
            "",
            "  @Override",
            "  public AndroidInjector<TestActivity> create(TestActivity instance) { return null; }",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import android.app.Activity;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.android.*;",
            "import dagger.multibindings.*;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds",
            "  @IntoMap",
            "  @ClassKey(TestActivity.class)",
            "  AndroidInjector.Factory<?> classKey(TestInjectorFactory factory);",
            "",
            "  @Binds",
            "  @IntoMap",
            "  @AndroidInjectionKey(\"test.TestActivity\")",
            "  AndroidInjector.Factory<?> stringKey(TestInjectorFactory factory);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import android.app.Activity;",
            "import dagger.Component;",
            "import dagger.android.DispatchingAndroidInjector;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  DispatchingAndroidInjector<Activity> dispatchingInjector();",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(ComponentProcessor.forTesting(new DuplicateAndroidInjectorsChecker()))
            .compile(activity, injectorFactory, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Multiple injector factories bound for the same type")
        .inFile(component)
        .onLineContaining("interface TestComponent");
    assertThat(compilation).hadErrorContaining("classKey(test.TestInjectorFactory)");
    assertThat(compilation).hadErrorContaining("stringKey(test.TestInjectorFactory)");
    assertThat(compilation).hadErrorCount(1);
  }
}

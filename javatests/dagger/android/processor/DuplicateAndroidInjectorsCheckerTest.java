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

import androidx.room.compiler.processing.util.Source;
import dagger.internal.codegen.ComponentProcessor;
import dagger.internal.codegen.KspComponentProcessor;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DuplicateAndroidInjectorsCheckerTest {
  @Test
  public void conflictingMapKeys() {
    Source activity =
        CompilerTests.javaSource(
            "test.TestActivity",
            "package test;",
            "",
            "import android.app.Activity;",
            "",
            "public class TestActivity extends Activity {}");
    Source injectorFactory =
        CompilerTests.javaSource(
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
    Source module =
        CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(activity, injectorFactory, module, component)
        .withAdditionalJavacProcessors(
            ComponentProcessor.withTestPlugins(new DuplicateAndroidInjectorsChecker()))
        .withAdditionalKspProcessors(
            KspComponentProcessor.Provider.withTestPlugins(new DuplicateAndroidInjectorsChecker()))
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject
                  .hasErrorContaining("Multiple injector factories bound for the same type")
                  .onLineContaining("interface TestComponent");
              subject.hasErrorContaining("classKey(test.TestInjectorFactory)");
              subject.hasErrorContaining("stringKey(test.TestInjectorFactory)");
              subject.hasErrorCount(1);
            });
  }
}

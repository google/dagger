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

package dagger.hilt.processor.internal.root;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compiler;

import com.google.common.base.Joiner;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// This test makes sure we don't regress the formatting in the components file.
@RunWith(JUnit4.class)
public final class RootFileFormatterTest {
  private static final Joiner JOINER = Joiner.on("\n");

  @Test
  public void testProdComponents() {
    Compilation compilation =
        compiler()
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.TestApplication",
                    "package test;",
                    "",
                    "import android.app.Application;",
                    "import dagger.hilt.android.HiltAndroidApp;",
                    "",
                    "@HiltAndroidApp(Application.class)",
                    "public class TestApplication extends Hilt_TestApplication {}"),
                entryPoint("SingletonComponent", "EntryPoint1"),
                entryPoint("SingletonComponent", "EntryPoint2"),
                entryPoint("ActivityComponent", "EntryPoint3"),
                entryPoint("ActivityComponent", "EntryPoint4"));
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/TestApplication_HiltComponents")
        .contentsAsUtf8String()
        .contains(
            JOINER.join(
                "  public abstract static class SingletonC implements"
                + " HiltWrapper_ActivityRetainedComponentManager"
                + "_ActivityRetainedComponentBuilderEntryPoint,",
                "      ServiceComponentManager.ServiceComponentBuilderEntryPoint,",
                "      SingletonComponent,",
                "      GeneratedComponent,",
                "      EntryPoint1,",
                "      EntryPoint2,",
                "      TestApplication_GeneratedInjector {"));

    assertThat(compilation)
        .generatedSourceFile("test/TestApplication_HiltComponents")
        .contentsAsUtf8String()
        .contains(
            JOINER.join(
                "  public abstract static class ActivityC implements ActivityComponent,",
                "      DefaultViewModelFactories.ActivityEntryPoint,",
                "      HiltWrapper_HiltViewModelFactory_ActivityCreatorEntryPoint,",
                "      FragmentComponentManager.FragmentComponentBuilderEntryPoint,",
                "      ViewComponentManager.ViewComponentBuilderEntryPoint,",
                "      GeneratedComponent,",
                "      EntryPoint3,",
                "      EntryPoint4 {"));
  }

  @Test
  public void testTestComponents() {
    Compilation compilation =
        compiler()
            .withOptions("-Adagger.hilt.shareTestComponents=false")
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.MyTest",
                    "package test;",
                    "",
                    "import dagger.hilt.android.testing.HiltAndroidTest;",
                    "",
                    "@HiltAndroidTest",
                    "public class MyTest {}"),
                entryPoint("SingletonComponent", "EntryPoint1"),
                entryPoint("SingletonComponent", "EntryPoint2"),
                entryPoint("ActivityComponent", "EntryPoint3"),
                entryPoint("ActivityComponent", "EntryPoint4"));
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test/MyTest_HiltComponents")
        .contentsAsUtf8String()
        .contains(
            JOINER.join(
                "  public abstract static class SingletonC implements"
                + " HiltWrapper_ActivityRetainedComponentManager"
                + "_ActivityRetainedComponentBuilderEntryPoint,",
                "      ServiceComponentManager.ServiceComponentBuilderEntryPoint,",
                "      SingletonComponent,",
                "      TestSingletonComponent,",
                "      EntryPoint1,",
                "      EntryPoint2,",
                "      MyTest_GeneratedInjector {"));

    assertThat(compilation)
        .generatedSourceFile("test/MyTest_HiltComponents")
        .contentsAsUtf8String()
        .contains(
            JOINER.join(
                "  public abstract static class ActivityC implements ActivityComponent,",
                "      DefaultViewModelFactories.ActivityEntryPoint,",
                "      HiltWrapper_HiltViewModelFactory_ActivityCreatorEntryPoint,",
                "      FragmentComponentManager.FragmentComponentBuilderEntryPoint,",
                "      ViewComponentManager.ViewComponentBuilderEntryPoint,",
                "      GeneratedComponent,",
                "      EntryPoint3,",
                "      EntryPoint4 {"));
  }

  @Test
  public void testSharedTestComponents() {
    Compilation compilation =
        compiler()
            .withOptions("-Adagger.hilt.shareTestComponents=true")
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.MyTest",
                    "package test;",
                    "",
                    "import dagger.hilt.android.testing.HiltAndroidTest;",
                    "",
                    "@HiltAndroidTest",
                    "public class MyTest {}"),
                entryPoint("SingletonComponent", "EntryPoint1"));
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("dagger/hilt/android/internal/testing/root/Default_HiltComponents")
        .contentsAsUtf8String()
        .contains(
            JOINER.join(
                "  public abstract static class SingletonC implements"
                + " HiltWrapper_ActivityRetainedComponentManager"
                + "_ActivityRetainedComponentBuilderEntryPoint,",
                "      ServiceComponentManager.ServiceComponentBuilderEntryPoint,",
                "      SingletonComponent,",
                "      TestSingletonComponent,",
                "      EntryPoint1,",
                "      MyTest_GeneratedInjector {"));
  }

  private static JavaFileObject entryPoint(String component, String name) {
    return JavaFileObjects.forSourceLines(
        "test." + name,
        "package test;",
        "",
        "import dagger.hilt.EntryPoint;",
        "import dagger.hilt.InstallIn;",
        component.equals("SingletonComponent") ? "import dagger.hilt.components.SingletonComponent;"
            : "import dagger.hilt.android.components." + component + ";",
        "",
        "@EntryPoint",
        "@InstallIn(" + component + ".class)",
        "public interface " + name + " {}");
  }
}

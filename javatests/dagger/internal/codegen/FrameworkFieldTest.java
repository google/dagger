/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.xprocessing.XTypeNames.JAVAX_PROVIDER;
import static dagger.internal.codegen.xprocessing.XTypeNames.MEMBERS_INJECTOR;
import static dagger.internal.codegen.xprocessing.XTypeNames.javaxProviderOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.membersInjectorOf;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.testing.compile.CompilationRule;
import dagger.Component;
import dagger.internal.codegen.binding.FrameworkField;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.javac.JavacPluginModule;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link FrameworkField}.
 */
@RunWith(JUnit4.class)
public class FrameworkFieldTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  @Inject XProcessingEnv processingEnv;
  @Inject CompilerOptions compilerOptions;

  private XType type;

  @Before
  public void setUp() {
    DaggerFrameworkFieldTest_TestComponent.builder()
        .javacPluginModule(
            new JavacPluginModule(compilationRule.getElements(), compilationRule.getTypes()))
        .build()
        .inject(this);
    type = processingEnv.requireType(X.class.getCanonicalName());
  }

  @Test public void frameworkType() {
    assertThat(FrameworkField.create("test", JAVAX_PROVIDER, type, compilerOptions).type())
        .isEqualTo(javaxProviderOf(type.asTypeName()));
    assertThat(FrameworkField.create("test", MEMBERS_INJECTOR, type, compilerOptions).type())
        .isEqualTo(membersInjectorOf(type.asTypeName()));
  }

  @Test public void nameSuffix() {
    assertThat(FrameworkField.create("foo", JAVAX_PROVIDER, type, compilerOptions).name())
        .isEqualTo("fooProvider");
    assertThat(FrameworkField.create("fooProvider", JAVAX_PROVIDER, type, compilerOptions).name())
        .isEqualTo("fooProvider");
  }

  static final class X {
    @Inject X() {}
  }

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface TestComponent {
    void inject(FrameworkFieldTest test);
  }
}
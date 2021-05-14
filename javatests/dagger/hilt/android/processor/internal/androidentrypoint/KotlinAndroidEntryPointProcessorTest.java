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

package dagger.hilt.android.processor.internal.androidentrypoint;

import static dagger.hilt.android.testing.compile.HiltCompilerTests.kotlinCompiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import com.tschuchort.compiletesting.KotlinCompilation;
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode;
import com.tschuchort.compiletesting.SourceFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KotlinAndroidEntryPointProcessorTest {
  @Test
  public void checkBaseClassConstructorHasNotDefaultParameters() {
    SourceFile fragmentSrc = SourceFile.Companion.kotlin("MyFragment.kt",
        String.join("\n",
            "package test",
            "",
            "import dagger.hilt.android.AndroidEntryPoint",
            "",
            "@AndroidEntryPoint",
            "class MyFragment : BaseFragment()"
        ),
        false);
    SourceFile baseFragmentSrc = SourceFile.Companion.kotlin("BaseFragment.kt",
        String.join("\n",
            "package test",
            "",
            "import androidx.fragment.app.Fragment",
            "",
            "abstract class BaseFragment(layoutId: Int = 0) : Fragment()"
        ),
        false);
    KotlinCompilation compilation = kotlinCompiler();
    compilation.setSources(ImmutableList.of(fragmentSrc, baseFragmentSrc));
    compilation.setKaptArgs(ImmutableMap.of(
        "dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true"));
    KotlinCompilation.Result result = compilation.compile();
    Truth.assertThat(result.getExitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
    Truth.assertThat(result.getMessages()).contains("The base class, 'test.BaseFragment', of the "
        + "@AndroidEntryPoint, 'test.MyFragment', contains a constructor with default parameters. "
        + "This is currently not supported by the Gradle plugin. Either specify the base class as "
        + "described at https://dagger.dev/hilt/gradle-setup#why-use-the-plugin or remove the "
        + "default value declaration.");
  }
}

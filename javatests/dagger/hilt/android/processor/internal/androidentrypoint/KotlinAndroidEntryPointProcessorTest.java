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

import static com.google.common.truth.Truth.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compileWithKapt;

import androidx.room.compiler.processing.util.DiagnosticMessage;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import javax.tools.Diagnostic.Kind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KotlinAndroidEntryPointProcessorTest {

  @Rule
  public TemporaryFolder tempFolderRule = new TemporaryFolder();

  @Test
  public void checkBaseClassConstructorHasNotDefaultParameters() {
    Source fragmentSrc = Source.Companion.kotlin("MyFragment.kt",
        String.join("\n",
            "package test",
            "",
            "import dagger.hilt.android.AndroidEntryPoint",
            "",
            "@AndroidEntryPoint",
            "class MyFragment : BaseFragment()"
        ));
    Source baseFragmentSrc = Source.Companion.kotlin("BaseFragment.kt",
        String.join("\n",
            "package test",
            "",
            "import androidx.fragment.app.Fragment",
            "",
            "abstract class BaseFragment(layoutId: Int = 0) : Fragment()"
        ));
    compileWithKapt(
        ImmutableList.of(fragmentSrc, baseFragmentSrc),
        ImmutableMap.of(
            "dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true"),
        tempFolderRule,
        result -> {
          assertThat(result.getSuccess()).isFalse();
          List<DiagnosticMessage> errors = result.getDiagnostics().get(Kind.ERROR);
          assertThat(errors).hasSize(1);
          assertThat(errors.get(0).getMsg())
              .contains("The base class, 'test.BaseFragment', of the "
                  + "@AndroidEntryPoint, 'test.MyFragment', contains a constructor with default "
                  + "parameters. This is currently not supported by the Gradle plugin.");
        });
  }
}

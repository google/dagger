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

import static com.google.common.truth.Truth.assertThat;
import static dagger.testing.compile.CompilerTests.compileWithKsp;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KspComponentProcessorTest {

  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();

  @Test
  public void componentTest() throws Exception {
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

    compileWithKsp(
        ImmutableList.of(componentSrc),
        tempFolderRule,
        result -> assertThat(result.getSuccess()).isTrue());
  }
}

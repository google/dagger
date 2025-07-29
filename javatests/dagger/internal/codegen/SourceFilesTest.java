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

import static com.google.common.truth.Truth.assertThat;

import androidx.room.compiler.codegen.XClassName;
import dagger.internal.codegen.binding.SourceFiles;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link SourceFiles}. */
@RunWith(JUnit4.class)
public final class SourceFilesTest {
  private static final class Int {}

  @Test
  public void testSimpleVariableName_typeCollisions() {
    // a handful of boxed types
    assertThat(simpleVariableName(Long.class)).isEqualTo("l");
    assertThat(simpleVariableName(Double.class)).isEqualTo("d");
    // not a boxed type type, but a custom type might collide
    assertThat(simpleVariableName(Int.class)).isEqualTo("i");
    // void is the weird pseudo-boxed type
    assertThat(simpleVariableName(Void.class)).isEqualTo("v");
    // reflective types
    assertThat(simpleVariableName(Class.class)).isEqualTo("clazz");
    assertThat(simpleVariableName(Package.class)).isEqualTo("pkg");
  }

  private static final class For {}

  private static final class Goto {}

  @Test
  public void testSimpleVariableName_randomKeywords() {
    assertThat(simpleVariableName(For.class)).isEqualTo("for_");
    assertThat(simpleVariableName(Goto.class)).isEqualTo("goto_");
  }

  @Test
  public void testSimpleVariableName() {
    assertThat(simpleVariableName(Object.class)).isEqualTo("object");
    assertThat(simpleVariableName(List.class)).isEqualTo("list");
  }

  private static String simpleVariableName(Class<?> clazz) {
    return SourceFiles.simpleVariableName(
        XClassName.Companion.get(clazz.getPackageName(), clazz.getSimpleName()));
  }
}

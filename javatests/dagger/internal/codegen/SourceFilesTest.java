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
import static dagger.internal.codegen.binding.SourceFiles.simpleVariableName;

import com.squareup.javapoet.ClassName;
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
    assertThat(simpleVariableName(ClassName.get(Long.class))).isEqualTo("l");
    assertThat(simpleVariableName(ClassName.get(Double.class))).isEqualTo("d");
    // not a boxed type type, but a custom type might collide
    assertThat(simpleVariableName(ClassName.get(Int.class))).isEqualTo("i");
    // void is the weird pseudo-boxed type
    assertThat(simpleVariableName(ClassName.get(Void.class))).isEqualTo("v");
    // reflective types
    assertThat(simpleVariableName(ClassName.get(Class.class))).isEqualTo("clazz");
    assertThat(simpleVariableName(ClassName.get(Package.class))).isEqualTo("pkg");
  }

  private static final class For {}

  private static final class Goto {}

  @Test
  public void testSimpleVariableName_randomKeywords() {
    assertThat(simpleVariableName(ClassName.get(For.class))).isEqualTo("for_");
    assertThat(simpleVariableName(ClassName.get(Goto.class))).isEqualTo("goto_");
  }

  @Test
  public void testSimpleVariableName() {
    assertThat(simpleVariableName(ClassName.get(Object.class))).isEqualTo("object");
    assertThat(simpleVariableName(ClassName.get(List.class))).isEqualTo("list");
  }
}

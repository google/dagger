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

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.toParametersCodeBlock;

import androidx.room.compiler.codegen.XCodeBlock;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link XCodeBlocks}. */
@RunWith(JUnit4.class)
public final class XCodeBlocksTest {
  private static final XCodeBlock objectO = XCodeBlock.of("%T o", Object.class);
  private static final XCodeBlock stringS = XCodeBlock.of("%T s", String.class);
  private static final XCodeBlock intI = XCodeBlock.of("%T i", int.class);

  @Test
  public void testToParametersCodeBlock() {
    assertThat(toJavaPoet(Stream.of(objectO, stringS, intI).collect(toParametersCodeBlock())))
        .isEqualTo(
            toJavaPoet(XCodeBlock.of("%T o, %T s, %T i", Object.class, String.class, int.class)));
  }

  @Test
  public void testToParametersCodeBlock_empty() {
    assertThat(
        toJavaPoet(Stream.<XCodeBlock>of().collect(toParametersCodeBlock())))
            .isEqualTo(toJavaPoet(XCodeBlock.of("")));
  }

  @Test
  public void testToParametersCodeBlock_oneElement() {
    assertThat(toJavaPoet(Stream.of(objectO).collect(toParametersCodeBlock())))
        .isEqualTo(toJavaPoet(objectO));
  }
}

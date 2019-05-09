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

package dagger.internal.codegen.javapoet;

import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.javapoet.CodeBlocks.javadocLinkTo;
import static dagger.internal.codegen.javapoet.CodeBlocks.toParametersCodeBlock;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.testing.compile.CompilationRule;
import com.squareup.javapoet.CodeBlock;
import java.util.stream.Stream;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CodeBlocks}. */
@RunWith(JUnit4.class)
public final class CodeBlocksTest {
  private static final CodeBlock objectO = CodeBlock.of("$T o", Object.class);
  private static final CodeBlock stringS = CodeBlock.of("$T s", String.class);
  private static final CodeBlock intI = CodeBlock.of("$T i", int.class);

  @Rule public CompilationRule compilationRule = new CompilationRule();
  private Elements elements;

  @Before
  public void setUp() {
    this.elements = compilationRule.getElements();
  }

  @Test
  public void testToParametersCodeBlock() {
    assertThat(Stream.of(objectO, stringS, intI).collect(toParametersCodeBlock()))
        .isEqualTo(CodeBlock.of("$T o, $T s, $T i", Object.class, String.class, int.class));
  }

  @Test
  public void testToParametersCodeBlock_empty() {
    assertThat(Stream.<CodeBlock>of().collect(toParametersCodeBlock())).isEqualTo(CodeBlock.of(""));
  }

  @Test
  public void testToParametersCodeBlock_oneElement() {
    assertThat(Stream.of(objectO).collect(toParametersCodeBlock())).isEqualTo(objectO);
  }

  @Test
  public void testJavadocLinkTo() {
    ExecutableElement equals =
        elements
            .getTypeElement(Object.class.getCanonicalName())
            .getEnclosedElements()
            .stream()
            .filter(element -> element.getKind().equals(METHOD))
            .map(ExecutableElement.class::cast)
            .filter(method -> method.getSimpleName().contentEquals("equals"))
            .findFirst()
            .get();
    assertThat(javadocLinkTo(equals))
        .isEqualTo(CodeBlock.of("{@link $T#equals($T)}", Object.class, Object.class));
  }
}

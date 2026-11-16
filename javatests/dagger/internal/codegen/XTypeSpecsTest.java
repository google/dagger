/*
 * Copyright (C) 2026 The Dagger Authors.
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

import static androidx.room3.compiler.codegen.compat.XConverters.toJavaPoet;
import static com.google.common.truth.Truth.assertThat;
import static dagger.testing.compile.CompilerTests.javaSource;

import androidx.room3.compiler.codegen.XTypeSpec;
import androidx.room3.compiler.processing.XTypeElement;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link XTypeSpecs}. */
@RunWith(JUnit4.class)
public final class XTypeSpecsTest {
  @Test
  public void noOriginatingElements() {
    assertThat(toJavaPoet(XTypeSpecs.classBuilder("Generated").build()).originatingElements)
        .isEmpty();
  }

  @Test
  public void addOriginatingElement_preservesAllElements() {
    CompilerTests.invocationCompiler(
            javaSource("test.First", "package test;", "class First {}"),
            javaSource("test.Second", "package test;", "class Second {}"))
        .compile(
            invocation -> {
              XTypeElement first = invocation.getProcessingEnv().requireTypeElement("test.First");
              XTypeElement second = invocation.getProcessingEnv().requireTypeElement("test.Second");
              XTypeSpec type =
                  XTypeSpecs.classBuilder("Generated")
                      .addOriginatingElement(first)
                      .addOriginatingElement(second)
                      .build();

              assertThat(toJavaPoet(type).originatingElements).hasSize(2);
            });
  }
}

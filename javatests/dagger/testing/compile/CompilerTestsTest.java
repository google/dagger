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

package dagger.testing.compile;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dagger.internal.codegen.ComponentProcessor;
import dagger.internal.codegen.KspComponentProcessor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CompilerTestsTest {

  @Test
  public void testAdditionalJavacProcessorWithComponentProcessor_fails() {
    CompilerTests.DaggerCompiler compiler =
        CompilerTests.daggerCompiler().withAdditionalJavacProcessors(new ComponentProcessor());
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(subject -> {}));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "ComponentProcessor is already included. To add plugins, use"
                + " withBindingGraphPlugins() instead.");
  }

  @Test
  public void testAdditionalKspProcessorWithComponentProcessorProvider_fails() {
    CompilerTests.DaggerCompiler compiler =
        CompilerTests.daggerCompiler()
            .withAdditionalKspProcessors(new KspComponentProcessor.Provider());
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(subject -> {}));
    assertThat(exception)
        .hasMessageThat()
        .contains(
            "KspComponentProcessor.Provider is already included. To add plugins, use"
                + " withBindingGraphPlugins() instead.");
  }
}

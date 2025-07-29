/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.generatesrootinput;

import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.util.Source;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.GeneratesRootInput;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that {@link GeneratesRootInputs} returns the elements to wait for. */
@RunWith(JUnit4.class)
public final class GeneratesRootInputProcessorTest {
  private static final int GENERATED_CLASSES = 5;
  private static final ClassName TEST_ANNOTATION = ClassName.get("test", "TestAnnotation");

  private final List<String> elementsToWaitFor = new ArrayList<>();
  private int generatedClasses = 0;

  public final class TestAnnotationStep extends BaseProcessingStep {
    private GeneratesRootInputs generatesRootInputs;

    public TestAnnotationStep(XProcessingEnv env) {
      super(env);
      generatesRootInputs = new GeneratesRootInputs(env);
    }

    @Override
    public void postProcess(XProcessingEnv processingEnv, XRoundEnv round) {
      if (generatedClasses > 0) {
        elementsToWaitFor.addAll(
            generatesRootInputs.getElementsToWaitFor(round).stream()
                .map(element -> XElements.asTypeElement(element).getQualifiedName())
                .collect(toImmutableList()));
      }
      if (generatedClasses < GENERATED_CLASSES) {
        TypeSpec typeSpec =
            TypeSpec.classBuilder("Foo" + generatedClasses++)
                .addAnnotation(TEST_ANNOTATION)
                .build();
        processingEnv.getFiler().write(JavaFile.builder("foo", typeSpec).build(), Mode.Isolating);
      }
    }
  }

  @Test
  public void succeeds_ComponentProcessorWaitsForAnnotationsWithGeneratesRootInput() {
    Source testAnnotation =
        HiltCompilerTests.javaSource(
            "test.TestAnnotation",
            "package test;",
            "@" + GeneratesRootInput.class.getCanonicalName(),
            "public @interface TestAnnotation {}");

    HiltCompilerTests.hiltCompiler(testAnnotation)
        .withProcessingSteps(TestAnnotationStep::new)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              assertThat(elementsToWaitFor)
                  .containsExactly("foo.Foo0", "foo.Foo1", "foo.Foo2", "foo.Foo3", "foo.Foo4")
                  .inOrder();
              elementsToWaitFor.clear();
              generatedClasses = 0;
            });
  }
}

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
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.auto.common.MoreElements;
import com.google.common.truth.Correspondence;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.BaseProcessor;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that {@link GeneratesRootInputs} returns the elements to wait for. */
@RunWith(JUnit4.class)
public final class GeneratesRootInputProcessorTest {
  private static final int GENERATED_CLASSES = 5;
  private static final ClassName TEST_ANNOTATION = ClassName.get("test", "TestAnnotation");

  private final List<Element> elementsToWaitFor = new ArrayList<>();
  private int generatedClasses = 0;

  @SupportedAnnotationTypes("*")
  public final class TestAnnotationProcessor extends BaseProcessor {
    private GeneratesRootInputs generatesRootInputs;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
      super.init(processingEnv);
      generatesRootInputs = new GeneratesRootInputs(processingEnv);
    }

    @Override
    protected void postRoundProcess(RoundEnvironment roundEnv) throws Exception {
      if (generatedClasses > 0) {
        elementsToWaitFor.addAll(generatesRootInputs.getElementsToWaitFor(roundEnv));
      }
      if (generatedClasses < GENERATED_CLASSES) {
        TypeSpec typeSpec =
            TypeSpec.classBuilder("Foo" + generatedClasses++)
                .addAnnotation(TEST_ANNOTATION)
                .build();
        JavaFile.builder("foo", typeSpec).build().writeTo(processingEnv.getFiler());
      }
    }
  }

  @Test
  public void succeeds_ComponentProcessorWaitsForAnnotationsWithGeneratesRootInput() {
    JavaFileObject testAnnotation =
        JavaFileObjects.forSourceLines(
            "test.TestAnnotation",
            "package test;",
            "@dagger.hilt.GeneratesRootInput",
            "public @interface TestAnnotation {}");

    Compilation compilation =
        javac()
            .withProcessors(new TestAnnotationProcessor(), new GeneratesRootInputProcessor())
            .compile(testAnnotation);

    assertThat(compilation).succeeded();
    assertThat(elementsToWaitFor)
        .comparingElementsUsing(
            Correspondence.<Element, String>transforming(
                element -> MoreElements.asType(element).getQualifiedName().toString(),
                "has qualified name of"))
        .containsExactly("foo.Foo0", "foo.Foo1", "foo.Foo2", "foo.Foo3", "foo.Foo4")
        .inOrder();
  }
}

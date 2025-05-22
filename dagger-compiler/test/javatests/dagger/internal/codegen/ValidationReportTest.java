/*
 * Copyright (C) 2015 The Dagger Authors.
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

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.testing.compile.CompilerTests;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ValidationReportTest {
  private static final Source TEST_CLASS_FILE =
      CompilerTests.javaSource(
          "test.TestClass",
          "package test;",
          "",
          "import javax.inject.Singleton;",
          "",
          "@Singleton",
          "final class TestClass {}");

  @Test
  public void basicReport() {
    CompilerTests.daggerCompiler(TEST_CLASS_FILE)
        .withProcessingSteps(
            () -> new SimpleTestStep() {
              @Override
              void test() {
                ValidationReport.about(getTypeElement("test.TestClass"))
                    .addError("simple error")
                    .build()
                    .printMessagesTo(processingEnv.getMessager());
              }
            })
        .compile(
          subject -> {
            subject.hasErrorCount(1);
            subject.hasErrorContaining("simple error")
                .onSource(TEST_CLASS_FILE)
                .onLine(6);
          });
  }

  @Test
  public void messageOnDifferentElement() {
    CompilerTests.daggerCompiler(TEST_CLASS_FILE)
        .withProcessingSteps(
            () -> new SimpleTestStep() {
              @Override
              void test() {
                ValidationReport.about(getTypeElement("test.TestClass"))
                    .addError("simple error", getTypeElement(String.class))
                    .build()
                    .printMessagesTo(processingEnv.getMessager());
              }
            })
        .compile(
          subject -> {
            subject.hasErrorCount(1);
            // TODO(b/249298461): The String types should match between javac and ksp.
            switch (CompilerTests.backend(subject)) {
              case JAVAC:
                subject.hasErrorContaining("[java.lang.String] simple error")
                    .onSource(TEST_CLASS_FILE)
                    .onLine(6);
                break;
              case KSP:
                subject.hasErrorContaining("[kotlin.String] simple error")
                    .onSource(TEST_CLASS_FILE)
                    .onLine(6);
            }
          });
  }

  @Test
  public void subreport() {
    CompilerTests.daggerCompiler(TEST_CLASS_FILE)
        .withProcessingSteps(
            () -> new SimpleTestStep() {
              @Override
              void test() {
                ValidationReport parentReport =
                    ValidationReport.about(getTypeElement(String.class))
                        .addSubreport(
                            ValidationReport.about(getTypeElement("test.TestClass"))
                                .addError("simple error")
                                .build())
                        .build();
                assertThat(parentReport.isClean()).isFalse();
                parentReport.printMessagesTo(processingEnv.getMessager());
              }
            })
        .compile(
          subject -> {
            subject.hasErrorCount(1);
            subject.hasErrorContaining("simple error")
                .onSource(TEST_CLASS_FILE)
                .onLine(6);
          });
  }

  private abstract static class SimpleTestStep implements XProcessingStep {
    protected XProcessingEnv processingEnv;

    @Override
    public final ImmutableSet<String> annotations() {
      // TODO(b/249322175): Replace this with "*" after this bug is fixed.
      // For now, we just trigger off of annotations in the other sources in the test, but ideally
      // this should support "*" similar to javac's Processor.
      return ImmutableSet.of("javax.inject.Singleton");
    }

    @Override
    public ImmutableSet<XElement> process(
        XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
      this.processingEnv = env;
      test();
      return ImmutableSet.of();
    }

    @Override
    public void processOver(
        XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {}

    protected final XTypeElement getTypeElement(Class<?> clazz) {
      return getTypeElement(clazz.getCanonicalName());
    }

    protected final XTypeElement getTypeElement(String canonicalName) {
      return processingEnv.requireTypeElement(canonicalName);
    }

    abstract void test();
  }
}

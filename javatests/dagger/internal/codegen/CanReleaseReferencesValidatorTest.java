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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CanReleaseReferencesValidator}. */
@RunWith(JUnit4.class)
public final class CanReleaseReferencesValidatorTest {
  @Test
  public void annotatesSourceRetainedAnnotation() {
    JavaFileObject annotation =
        JavaFileObjects.forSourceLines(
            "test.Metadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import java.lang.annotation.RetentionPolicy;",
            "",
            "@CanReleaseReferences",
            "@Retention(RetentionPolicy.SOURCE)",
            "@interface Metadata {}");
    assertAbout(javaSource())
        .that(annotation)
        .processedWith(new ComponentProcessor(), new AutoAnnotationProcessor())
        .failsToCompile()
        .withErrorContaining("SOURCE")
        .in(annotation)
        .onLine(8);
  }
}

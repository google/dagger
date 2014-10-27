/**
 * Copyright (C) 2014 Google, Inc.
 * Copyright (C) 2014 Square, Inc.
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
package dagger.tests.integration.operation.dagger1;

import dagger.Module;
import dagger.ObjectGraph;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import javax.inject.Qualifier;

import static junit.framework.Assert.fail;
import static org.assertj.core.api.Assertions.*;

@RunWith(JUnit4.class)
public final class FailureModeErrorsTest {

  @Module
  static class CompleteModule {}

  static class ArrayFoo {
    @Inject ArrayFoo(String[] ignored) {}
  }

  @Module(injects = ArrayFoo.class, complete = false)
  static class ArrayFooModule {}

  @Test public void failOnMissingModule_array_or_generics() {
    // Generics here are crazy to try to test for, but this code path is legit regardless.
    try {
      ObjectGraph.create(new CompleteModule(), new ArrayFooModule()).get(ArrayFoo.class);
      fail("Should have thrown.");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains(
          "java.lang.String[] is a generic class or an array and can only be bound with "
              + "concrete type parameter(s) in a @Provides method. required by class "
              + FailureModeErrorsTest.class.getCanonicalName() + "$ArrayFoo");
    }
  }

  @Qualifier @interface MyFoo {}

  static class QualifyingFoo {
    @Inject QualifyingFoo(@MyFoo String ignored) {}
  }

  @Module(injects = QualifyingFoo.class, complete = false)
  static class QualifyingFooModule {}

  @Test public void failOnMissingModule_qualified() {
    try {
      ObjectGraph.create(new CompleteModule(), new QualifyingFooModule()).get(QualifyingFoo.class);
      fail("Should have thrown.");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains(
          "@" + FailureModeErrorsTest.class.getCanonicalName() + "$MyFoo()/java.lang.String "
              + "is a @Qualifier-annotated type and must be bound by a @Provides method. required "
              + "by class "
              + FailureModeErrorsTest.class.getCanonicalName() + "$QualifyingFoo");
    }
  }
}

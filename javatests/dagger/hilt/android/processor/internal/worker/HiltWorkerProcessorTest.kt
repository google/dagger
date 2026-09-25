/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.worker

import androidx.room3.compiler.processing.ExperimentalProcessingApi
import dagger.hilt.android.testing.compile.HiltCompilerTests
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalProcessingApi::class)
@RunWith(JUnit4::class)
class HiltWorkerProcessorTest {
  @Test
  fun validWorkerWithInject() {
    val myWorker =
      HiltCompilerTests.javaSource(
        "dagger.hilt.android.test.MyWorker",
        """
        package dagger.hilt.android.test;

        import androidx.work.ListenableWorker;
        import androidx.work.WorkerParameters;
        import android.content.Context;
        import dagger.hilt.android.work.HiltWorker;
        import javax.inject.Inject;

        @HiltWorker
        public class MyWorker extends ListenableWorker {
            @Inject
            public MyWorker(
                @dagger.assisted.Assisted Context context,
                @dagger.assisted.Assisted WorkerParameters workerParams
            ) {
                super(context, workerParams);
            }
        }
        """
          .trimIndent()
      )
    HiltCompilerTests.hiltCompiler(myWorker)
      .withAdditionalJavacProcessors(HiltWorkerProcessor())
      .withAdditionalKspProcessors(KspHiltWorkerProcessor.Provider())
      .compile { subject -> subject.hasErrorCount(0) }
  }

  @Test
  fun validWorkerWithAssistedInject() {
    val myWorker =
      HiltCompilerTests.javaSource(
        "dagger.hilt.android.test.MyWorker",
        """
        package dagger.hilt.android.test;

        import androidx.work.ListenableWorker;
        import androidx.work.WorkerParameters;
        import android.content.Context;
        import dagger.assisted.Assisted;
        import dagger.assisted.AssistedInject;
        import dagger.hilt.android.work.HiltWorker;

        @HiltWorker
        public class MyWorker extends ListenableWorker {
            @AssistedInject
            public MyWorker(
                @Assisted Context context,
                @Assisted WorkerParameters workerParams,
                String dependency
            ) {
                super(context, workerParams);
            }
        }
        """
          .trimIndent()
      )
    HiltCompilerTests.hiltCompiler(myWorker)
      .withAdditionalJavacProcessors(HiltWorkerProcessor())
      .withAdditionalKspProcessors(KspHiltWorkerProcessor.Provider())
      .compile { subject -> subject.hasErrorCount(0) }
  }

  @Test
  fun verifyWorkerExtendsListenableWorker() {
    val myWorker =
      HiltCompilerTests.javaSource(
        "dagger.hilt.android.test.MyWorker",
        """
        package dagger.hilt.android.test;

        import dagger.hilt.android.work.HiltWorker;
        import javax.inject.Inject;

        @HiltWorker
        public class MyWorker {
            @Inject
            public MyWorker() { }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myWorker)
      .withAdditionalJavacProcessors(HiltWorkerProcessor())
      .withAdditionalKspProcessors(KspHiltWorkerProcessor.Provider())
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContainingMatch(
          "@HiltWorker is only supported on types that subclass androidx.work.ListenableWorker."
        )
      }
  }

  @Test
  fun verifyHasInjectConstructor() {
    val myWorker =
      HiltCompilerTests.javaSource(
        "dagger.hilt.android.test.MyWorker",
        """
        package dagger.hilt.android.test;

        import androidx.work.ListenableWorker;
        import androidx.work.WorkerParameters;
        import android.content.Context;
        import dagger.hilt.android.work.HiltWorker;

        @HiltWorker
        public class MyWorker extends ListenableWorker {
            public MyWorker(
                Context context,
                WorkerParameters workerParams
            ) {
                super(context, workerParams);
            }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myWorker)
      .withAdditionalJavacProcessors(HiltWorkerProcessor())
      .withAdditionalKspProcessors(KspHiltWorkerProcessor.Provider())
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(1)
        subject.hasErrorContaining(
          "@HiltWorker annotated class should contain exactly one @Inject or @AssistedInject annotated constructor."
        )
      }
  }

  @Test
  fun verifyNonPrivateConstructor() {
    val myWorker =
      HiltCompilerTests.javaSource(
        "dagger.hilt.android.test.MyWorker",
        """
        package dagger.hilt.android.test;

        import androidx.work.ListenableWorker;
        import androidx.work.WorkerParameters;
        import android.content.Context;
        import dagger.hilt.android.work.HiltWorker;
        import javax.inject.Inject;

        @HiltWorker
        public class MyWorker extends ListenableWorker {
            @Inject
            private MyWorker(
                Context context,
                WorkerParameters workerParams
            ) {
                super(context, workerParams);
            }
        }
        """
          .trimIndent()
      )

    HiltCompilerTests.hiltCompiler(myWorker)
      .withAdditionalJavacProcessors(HiltWorkerProcessor())
      .withAdditionalKspProcessors(KspHiltWorkerProcessor.Provider())
      .compile { subject ->
        subject.compilationDidFail()
        subject.hasErrorCount(2)
        subject.hasErrorContaining("Dagger does not support injection into private constructors")
        subject.hasErrorContaining(
          "@Inject annotated constructors must not be private."
        )
      }
  }
}

/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.androidentrypoint;

import static dagger.hilt.processor.internal.HiltCompilerOptions.getGradleProjectType;
import static dagger.hilt.processor.internal.HiltCompilerOptions.useAggregatingRootProcessor;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.optionvalues.GradleProjectType;

/**
 * Processor that creates a module for classes marked with {@link
 * dagger.hilt.android.AndroidEntryPoint}.
 */
public final class AndroidEntryPointProcessingStep extends BaseProcessingStep {
  public AndroidEntryPointProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(
        AndroidClassNames.ANDROID_ENTRY_POINT, AndroidClassNames.HILT_ANDROID_APP);
  }

  @Override
  public boolean delayErrors() {
    return true;
  }

  @Override
  public void processEach(ClassName annotation, XElement element) throws Exception {
    AndroidEntryPointMetadata metadata = AndroidEntryPointMetadata.of(element);
    new InjectorEntryPointGenerator(processingEnv(), metadata).generate();
    switch (metadata.androidType()) {
      case APPLICATION:
        GradleProjectType projectType = getGradleProjectType(processingEnv());
        if (projectType != GradleProjectType.UNSET) {
          ProcessorErrors.checkState(
              projectType == GradleProjectType.APP,
              element,
              "Application class, %s, annotated with @HiltAndroidApp must be defined in a "
                  + "Gradle android application module (i.e. contains a build.gradle file with "
                  + "`plugins { id 'com.android.application' }`).",
              metadata.element().getQualifiedName());
        }

        // The generated application references the generated component so they must be generated
        // in the same build unit. Thus, we only generate the application here if we're using the
        // aggregating root processor. If we're using the Hilt Gradle plugin's aggregating task, we
        // need to generate the application within ComponentTreeDepsProcessor instead.
        if (useAggregatingRootProcessor(processingEnv())) {
          // While we could always generate the application in ComponentTreeDepsProcessor, even if
          // we're using the aggregating root processor, it can lead to extraneous errors when
          // things fail before ComponentTreeDepsProcessor runs so we generate it here to avoid that
          new ApplicationGenerator(processingEnv(), metadata).generate();
        } else {
          // If we're not using the aggregating root processor, then make sure the root application
          // does not extend the generated application directly, and instead uses bytecode injection
          ProcessorErrors.checkState(
              metadata.requiresBytecodeInjection(),
              metadata.element(),
              "'enableAggregatingTask=true' cannot be used when the application directly "
                  + "references the generated Hilt class, %s. Either extend %s directly (relying "
                  + "on the Gradle plugin described in "
                  + "https://dagger.dev/hilt/gradle-setup#why-use-the-plugin or set "
                  + "'enableAggregatingTask=false'.",
              metadata.generatedClassName(),
              metadata.baseClassName());
        }
        break;
      case ACTIVITY:
        new ActivityGenerator(processingEnv(), metadata).generate();
        break;
      case BROADCAST_RECEIVER:
        new BroadcastReceiverGenerator(processingEnv(), metadata).generate();
        break;
      case FRAGMENT:
        new FragmentGenerator(processingEnv(), metadata).generate();
        break;
      case SERVICE:
        new ServiceGenerator(processingEnv(), metadata).generate();
        break;
      case VIEW:
        new ViewGenerator(processingEnv(), metadata).generate();
        break;
    }
  }
}

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
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.optionvalues.GradleProjectType;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/**
 * Processor that creates a module for classes marked with {@link
 * dagger.hilt.android.AndroidEntryPoint}.
 */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class AndroidEntryPointProcessor extends BaseProcessor {

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        AndroidClassNames.ANDROID_ENTRY_POINT.toString(),
        AndroidClassNames.HILT_ANDROID_APP.toString());
  }

  @Override
  public boolean delayErrors() {
    return true;
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    AndroidEntryPointMetadata metadata = AndroidEntryPointMetadata.of(getProcessingEnv(), element);
    new InjectorEntryPointGenerator(getProcessingEnv(), metadata).generate();
    switch (metadata.androidType()) {
      case APPLICATION:
        GradleProjectType projectType = getGradleProjectType(getProcessingEnv());
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
        if (useAggregatingRootProcessor(getProcessingEnv())) {
          // While we could always generate the application in ComponentTreeDepsProcessor, even if
          // we're using the aggregating root processor, it can lead to extraneous errors when
          // things fail before ComponentTreeDepsProcessor runs so we generate it here to avoid that
          new ApplicationGenerator(getProcessingEnv(), metadata).generate();
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
        new ActivityGenerator(getProcessingEnv(), metadata).generate();
        break;
      case BROADCAST_RECEIVER:
        new BroadcastReceiverGenerator(getProcessingEnv(), metadata).generate();
        break;
      case FRAGMENT:
        new FragmentGenerator(
            getProcessingEnv(), metadata )
            .generate();
        break;
      case SERVICE:
        new ServiceGenerator(getProcessingEnv(), metadata).generate();
        break;
      case VIEW:
        new ViewGenerator(getProcessingEnv(), metadata).generate();
        break;
      default:
        throw new IllegalStateException("Unknown Hilt type: " + metadata.androidType());
    }
  }
}

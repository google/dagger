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

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.BaseProcessor;
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
        new ApplicationGenerator(getProcessingEnv(), metadata).generate();
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

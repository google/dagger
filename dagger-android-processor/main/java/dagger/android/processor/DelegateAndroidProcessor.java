/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.android.processor;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingEnvConfig;
import androidx.room.compiler.processing.XProcessingStep;
import com.google.common.collect.ImmutableList;
import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;

/** An implementation of Dagger Android processor that is shared between Javac and KSP. */
final class DelegateAndroidProcessor {
  static final XProcessingEnvConfig PROCESSING_ENV_CONFIG =
      new XProcessingEnvConfig.Builder().build();
  static final String FLAG_EXPERIMENTAL_USE_STRING_KEYS =
      "dagger.android.experimentalUseStringKeys";

  private XProcessingEnv env;

  public void initialize(XProcessingEnv env) {
    this.env = env;
  }

  public ImmutableList<XProcessingStep> processingSteps() {
    return ImmutableList.of(
        new AndroidMapKeyProcessingStep(env), new ContributesAndroidInjectorProcessingStep(env));
  }

  @Singleton
  @Component
  interface Injector {
    void inject(DelegateAndroidProcessor delegateAndroidProcessor);

    @Component.Factory
    interface Factory {
      Injector create(@BindsInstance XProcessingEnv env);
    }
  }
}

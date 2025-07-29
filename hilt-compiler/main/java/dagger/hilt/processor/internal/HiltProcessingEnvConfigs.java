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

package dagger.hilt.processor.internal;

import androidx.room.compiler.processing.XProcessingEnvConfig;

/** The {@link XProcessingEnvConfig} used when processing Hilt. */
public final class HiltProcessingEnvConfigs {
  public static final XProcessingEnvConfig CONFIGS =
      new XProcessingEnvConfig.Builder()
          // In Hilt we disable the default element validation because we would otherwise run into a
          // cycle where our Hilt processors are waiting on the "Hilt_Foo" classes to be generated
          // before processing "Foo", but "Hilt_Foo" can't be generated until "Foo" is processed.
          // Thus, we disable that validation here and we perform our own validation when necessary.
          .disableAnnotatedElementValidation(true)
          .build();

  private HiltProcessingEnvConfigs() {}
}

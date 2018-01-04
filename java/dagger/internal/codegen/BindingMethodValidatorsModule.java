/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static com.google.common.collect.Maps.uniqueIndex;

import com.google.common.collect.ImmutableMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Binds each {@link BindingMethodValidator} into a map, keyed by {@link
 * BindingMethodValidator#methodAnnotation()}.
 */
@Module
interface BindingMethodValidatorsModule {
  @Provides
  static ImmutableMap<Class<? extends Annotation>, BindingMethodValidator> indexValidators(
      Set<BindingMethodValidator> validators) {
    return uniqueIndex(validators, BindingMethodValidator::methodAnnotation);
  }

  @Binds
  @IntoSet
  BindingMethodValidator provides(ProvidesMethodValidator validator);

  @Binds
  @IntoSet
  BindingMethodValidator produces(ProducesMethodValidator validator);

  @Binds
  @IntoSet
  BindingMethodValidator binds(BindsMethodValidator validator);

  @Binds
  @IntoSet
  BindingMethodValidator multibinds(MultibindsMethodValidator validator);

  @Binds
  @IntoSet
  BindingMethodValidator bindsOptionalOf(BindsOptionalOfMethodValidator validator);
}

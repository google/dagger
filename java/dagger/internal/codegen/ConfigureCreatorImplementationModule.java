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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Retention;
import java.util.Optional;
import javax.inject.Qualifier;

/**
 * Breaks the initialization cycle between {@link ComponentImplementation} and it's optional {@link
 * ComponentCreatorImplementation}, which itself needs a {@link ComponentImplementation}.
 *
 * <p>This module takes the component implementation that is bound elsewhere with the {@link
 * Unconfigured} qualifier, creates the creator implementation and calls {@link
 * ComponentImplementation#setCreatorImplementation(Optional)}.
 */
@Module
interface ConfigureCreatorImplementationModule {
  @Provides
  @PerComponentImplementation
  static Optional<ComponentCreatorImplementation> creatorImplementation(
      @Unconfigured ComponentImplementation componentImplementation,
      BindingGraph bindingGraph,
      DaggerTypes types,
      DaggerElements elements) {
    return ComponentCreatorImplementation.create(
        componentImplementation, bindingGraph, elements, types);
  }

  @Provides
  @PerComponentImplementation
  static ComponentImplementation componentImplementation(
      @Unconfigured ComponentImplementation componentImplementation,
      Optional<ComponentCreatorImplementation> componentCreatorImplementation) {
    componentImplementation.setCreatorImplementation(componentCreatorImplementation);
    return componentImplementation;
  }

  /**
   * Designates that the {@link ComponentImplementation} is not yet configured with its creator
   * implementation.
   */
  @Retention(RUNTIME)
  @Qualifier
  @interface Unconfigured {}
}

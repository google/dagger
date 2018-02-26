/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.producers;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.Module;
import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a class that contributes {@link Produces} bindings to the production component.
 *
 * @since 2.0
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
@Beta
public @interface ProducerModule {
  /**
   * Additional {@code @ProducerModule}- or {@link Module}-annotated classes from which this module
   * is composed. The de-duplicated contributions of the modules in {@code includes}, and of their
   * inclusions recursively, are all contributed to the object graph.
   */
  Class<?>[] includes() default {};

  /**
   * Any {@link dagger.Subcomponent}- or {@link ProductionSubcomponent}-annotated classes which
   * should be children of the component in which this module is installed. A subcomponent may be
   * listed in more than one module in a component.
   *
   * @since 2.7
   */
  Class<?>[] subcomponents() default {};
}

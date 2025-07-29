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

package dagger.producers;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a production component or subcomponent to specify its policy when a child component is
 * cancelled.
 *
 * <p>When a future returned from an entry point on a production component is cancelled, the
 * component is cancelled: all producers in the component (including those for other entry points)
 * are cancelled.
 *
 * <p>When a child component is cancelled, its parent component <i>is not</i> cancelled unless the
 * parent component is annotated with {@code @CancellationPolicy(fromSubcomponents = PROPAGATE)}. If
 * that parent component has a parent (the grandparent of the cancelled child component), it will
 * not be cancelled unless it also has a {@code @CancellationPolicy} annotation allowing
 * cancellation to propagate to it from subcomponents.
 */
@Documented
@Target(TYPE)
@Retention(CLASS)
@Beta
public @interface CancellationPolicy {
  /**
   * Defines whether the annotated production component is cancelled when a child component is
   * cancelled.
   *
   * <p>The default, if no cancellation policy annotation is provided, is {@link
   * Propagation#IGNORE}.
   */
  Propagation fromSubcomponents();

  /**
   * Enumeration of the options for what happens to a parent component when one of its child
   * components is cancelled.
   */
  enum Propagation {
    /** Cancel the annotated component when a child component is cancelled. */
    PROPAGATE,

    /** Do not cancel the annotated component when a child component is cancelled. */
    IGNORE
  }
}

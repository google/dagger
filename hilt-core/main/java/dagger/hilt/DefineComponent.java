/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import dagger.hilt.internal.definecomponent.DefineComponentNoParent;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Defines a Hilt component.
 *
 * <p>Example defining a root component, {@code ParentComponent}:
 *
 * <pre><code>
 *   {@literal @}ParentScoped
 *   {@literal @}DefineComponent
 *   interface ParentComponent {}
 * </code></pre>
 *
 * <p>Example defining a child component, {@code ChildComponent}:
 *
 * <pre><code>
 *   {@literal @}ChildScoped
 *   {@literal @}DefineComponent(parent = ParentComponent.class)
 *   interface ChildComponent {}
 * </code></pre>
 */
@Retention(CLASS)
@Target(TYPE)
@GeneratesRootInput
public @interface DefineComponent {
  /** Returns the parent of this component, if it exists. */
  Class<?> parent() default DefineComponentNoParent.class;

  /**
   * Defines a builder for a Hilt component.
   *
   * <pre><code>
   *   {@literal @}DefineComponent.Builder
   *   interface ParentComponentBuilder {
   *     ParentComponentBuilder seedData(SeedData seed);
   *     ParentComponent build();
   *   }
   * </code></pre>
   */
  // TODO(bcorso): Consider making this a top-level class to hint that it doesn't need to be nested.
  @Retention(CLASS)
  @Target(TYPE)
  @GeneratesRootInput
  public @interface Builder {}
}

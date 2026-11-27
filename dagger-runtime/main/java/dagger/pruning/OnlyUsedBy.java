/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.pruning;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a component or Hilt {@code @EntryPoint} method as only being called by bindings matching
 * the given binding identifiers.
 *
 * <p>A binding's identifier can be declared with {@link BindingIdentifier}. When {@code
 * @BindingIdentifier} is not specified, an {@code @Inject} constructor binding defaults to its own
 * class as its identifier; other bindings have no default identifier.
 *
 * <p>If none of the listed binding identifiers are reachable in the binding graph, Dagger prunes
 * the annotated method and its exclusive transitive dependencies before validation and code
 * generation. Invoking a pruned method at runtime throws {@link UnsupportedOperationException}.
 *
 * <p>Example usage:
 *
 * <pre><code>
 * {@literal @}Component
 * public interface MyComponent {
 *   {@literal @}OnlyUsedBy(FooId.class) Foo getFoo();
 * }
 *
 * class FooAccessor {
 *   {@literal @}BindingIdentifier(FooId.class) {@literal @}Inject FooAccessor() {}
 *
 *   Foo getFoo(MyComponent component) { return component.getFoo(); }
 * }
 * </code></pre>
 */
@Documented
@Retention(CLASS)
@Target(METHOD)
public @interface OnlyUsedBy {
  /** The binding identifier classes whose reachability retains this entry point method. */
  Class<?>[] value();
}

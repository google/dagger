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

import com.google.common.util.concurrent.ListenableFuture;
import dagger.Module;
import dagger.Provides;
import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.inject.Inject;
import javax.inject.Qualifier;

/**
 * Annotates an interface or abstract class for which a fully-formed, dependency-injected
 * implementation is to be generated from a set of {@linkplain #modules modules}. The generated
 * class will have the name of the type annotated with {@code @ProductionComponent} prepended with
 * {@code Dagger}. For example, {@code @ProductionComponent interface MyComponent {...}} will
 * produce an implementation named {@code DaggerMyComponent}.
 *
 * <p>Each {@link Produces} method that contributes to the component will be called at most once per
 * component instance, no matter how many times that binding is used as a dependency. TODO(beder):
 * Decide on how scope works for producers.
 *
 * <h2>Component methods</h2>
 *
 * <p>Every type annotated with {@code @ProductionComponent} must contain at least one abstract
 * component method. Component methods must represent {@linkplain Producer production}.
 *
 * <p>Production methods have no arguments and return either a {@link ListenableFuture} or {@link
 * Producer} of a type that is {@link Inject injected}, {@link Provides provided}, or {@link
 * Produces produced}. Each may have a {@link Qualifier} annotation as well. The following are all
 * valid production method declarations:
 *
 * <pre><code>
 *   {@literal ListenableFuture<SomeType>} getSomeType();
 *   {@literal Producer<Set<SomeType>>} getSomeTypes();
 *   {@literal @Response ListenableFuture<Html>} getResponse();
 * </code></pre>
 *
 * <h2>Exceptions</h2>
 *
 * <p>When a producer throws an exception, the exception will be propagated to its downstream
 * producers in the following way: if the downstream producer injects a type {@code T}, then that
 * downstream producer will be skipped, and the exception propagated to its downstream producers;
 * and if the downstream producer injects a {@code Produced<T>}, then the downstream producer will
 * be run with the exception stored in the {@code Produced<T>}.
 *
 * <p>If a non-execution exception is thrown (e.g., an {@code InterruptedException} or {@code
 * CancellationException}), then exception is handled as in {@link
 * com.google.common.util.concurrent.Futures#transform}.
 * <!-- TODO(beder): Explain this more thoroughly, and update the javadocs of those utilities. -->
 *
 * <h2>Executor</h2>
 *
 * <p>The component must include a binding for <code>{@literal @}{@link Production}
 * {@link java.util.concurrent.Executor}</code>; this binding will be called exactly once, and the
 * provided executor will be used by the framework to schedule all producer methods (for this
 * component, and any {@link ProductionSubcomponent} it may have.
 *
 * @since 2.0
 */
@Retention(RUNTIME) // Allows runtimes to have specialized behavior interoperating with Dagger.
@Documented
@Target(TYPE)
@Beta
public @interface ProductionComponent {
  /**
   * A list of classes annotated with {@link Module} or {@link ProducerModule} whose bindings are
   * used to generate the component implementation.
   */
  Class<?>[] modules() default {};

  /**
   * A list of types that are to be used as component dependencies.
   */
  Class<?>[] dependencies() default {};

  /**
   * A builder for a component. Components may have a single nested static abstract class or
   * interface annotated with {@code @ProductionComponent.Builder}. If they do, then the component's
   * generated builder will match the API in the type.  Builders must follow some rules:
   * <ul>
   * <li> A single abstract method with no arguments must exist, and must return the component.
   *      (This is typically the {@code build()} method.)
   * <li> All other abstract methods must take a single argument and must return void,
   *      the builder type, or a supertype of the builder.
   * <li> Each component dependency <b>must</b> have an abstract setter method.
   * <li> Each module dependency that Dagger can't instantiate itself (i.e., the module
   *      doesn't have a visible no-args constructor) <b>must</b> have an abstract setter method.
   *      Other module dependencies (ones that Dagger can instantiate) are allowed, but not
   *      required.
   * <li> Non-abstract methods are allowed, but ignored as far as validation and builder generation
   *      are concerned.
   * </ul>
   *
   * For example, this could be a valid {@code ProductionComponent} with a builder: <pre><code>
   * {@literal @}ProductionComponent(modules = {BackendModule.class, FrontendModule.class})
   * interface MyComponent {
   *   {@literal ListenableFuture<MyWidget>} myWidget();
   *
   *   {@literal @}ProductionComponent.Builder
   *   interface Builder {
   *     MyComponent build();
   *     Builder backendModule(BackendModule bm);
   *     Builder frontendModule(FrontendModule fm);
   *   }
   * }</code></pre>
   */
  @Target(TYPE)
  @Documented
  @interface Builder {}
}

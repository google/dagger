/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.android;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Generates an {@link AndroidInjector} for the return type of this method. The injector is
 * implemented with a {@link dagger.Subcomponent} and will be a child of the {@link dagger.Module}'s
 * component.
 *
 * <p>This annotation must be applied to an abstract method in a {@link dagger.Module} that returns
 * a concrete Android framework type (e.g. {@code FooActivity}, {@code BarFragment}, {@code
 * MyService}, etc). The method should have no parameters.
 *
 * <p>For more information, see <a href="https://dagger.dev/android">the docs</a>
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface ContributesAndroidInjector {
  /** Modules to be installed in the generated {@link dagger.Subcomponent}. */
  Class<?>[] modules() default {};
}

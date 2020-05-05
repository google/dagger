/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.internal.testing;

import static java.lang.annotation.RetentionPolicy.CLASS;

import android.app.Application;
import dagger.hilt.GeneratesRootInput;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Annotation that generates a Hilt test application. */
@Retention(CLASS)
@Target({ElementType.TYPE})
@GeneratesRootInput
public @interface InternalTestRoot {

  /** Returns the test class. */
  Class<?> testClass();

  /** Returns the base {@link Application} class.  */
  Class<? extends Application> applicationBaseClass();
}

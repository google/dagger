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

package dagger.hilt.migration;

import dagger.hilt.GeneratesRootInput;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines an alias between an existing Hilt scope and the annotated scope. For example, the
 * following code makes {@literal @}MyScope a functional replacement for {@literal @}ActivityScope.
 *
 * <p>
 *
 * <pre>
 *   {@literal @}Scope
 *   {@literal @}AliasOf(ActivityScope.class)
 *   public {@literal @}interface MyScope{}
 * </pre>
 *
 * <p>
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
@GeneratesRootInput
public @interface AliasOf {
  /** Returns the existing Hilt scope(s) that the annotated scope is aliasing. */
  Class<? extends Annotation>[] value();
}

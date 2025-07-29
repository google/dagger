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

package dagger.hilt.android;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Makes a View annotated with {@link AndroidEntryPoint} have access to fragment bindings.
 *
 * <p>By default, views annotated with {@literal @}AndroidEntryPoint do not have access to fragment
 * bindings and must use this annotation if fragment bindings are required. When this annotation is
 * used, this view must always be attached through a fragment.
 */
@Target({ElementType.TYPE})
public @interface WithFragmentBindings {}

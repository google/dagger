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

package dagger.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Metadata annotation for base subcomponent implementations in ahead-of-time compilations. This
 * propagates any compiler options related to code generation so that later compilations can
 * recreate the model of the generated code of superclass implementations.
 */
@Target(ElementType.TYPE)
public @interface GenerationOptions {
  boolean fastInit();
}

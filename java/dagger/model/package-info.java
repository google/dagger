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

/**
 * This package contains the APIs that are core to Dagger's internal model of bindings and the
 * binding graph. The types are shared with the Dagger processor and are exposed to clients of the
 * Dagger SPI.
 *
 * <p>Unless otherwise specified, the types/interfaces are only intended to be implemented in this
 * package (i.e. via {@code @AutoValue}) or by Dagger's processor. This applies to test code as
 * well, so if you need a fake, please file a feature request instead of implementing it yourself.
 */
@CheckReturnValue
@Beta
package dagger.model;

import com.google.errorprone.annotations.CheckReturnValue;
import dagger.internal.Beta;

/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import javax.inject.Qualifier;

/**
 * Qualifies a type that will be provided to the framework for use internally.
 *
 * <p>The only type that may be so qualified is {@link java.util.concurrent.Executor}. In this case,
 * the resulting executor is used to schedule {@linkplain Produces producer methods} in a
 * {@link ProductionComponent} or {@link ProductionSubcomponent}.
 */
@Documented
@Retention(RUNTIME)
@Qualifier
@Beta
public @interface Production {}

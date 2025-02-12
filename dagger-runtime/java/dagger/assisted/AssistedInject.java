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

package dagger.assisted;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates the constuctor of a type that will be created via assisted injection.
 *
 * <p>Note that an assisted injection type cannot be scoped. In addition, assisted injection
 * requires the use of a factory annotated with {@link AssistedFactory} (see the example below).
 *
 * <p>Example usage:
 *
 * <p>Suppose we have a type, {@code DataService}, that has two dependencies: {@code DataFetcher}
 * and {@code Config}. When creating {@code DataService}, we would like to pass in an instance of
 * {@code Config} manually rather than having Dagger create it for us. This can be done using
 * assisted injection.
 *
 * <p>To start, we annotate the {@code DataService} constructor with {@link AssistedInject} and we
 * annotate the {@code Config} parameter with {@link Assisted}, as shown below:
 *
 * <pre><code>
 *   final class DataService {
 *     private final DataFetcher dataFetcher;
 *     private final Config config;
 *
 *     {@literal @}AssistedInject
 *     DataService(DataFetcher dataFetcher, {@literal @}Assisted Config config) {
 *       this.dataFetcher = dataFetcher;
 *       this.config = config;
 *     }
 *   }
 * </code></pre>
 *
 * <p>Next, we define a factory for the assisted type, {@code DataService}, and annotate it with
 * {@link AssistedFactory}. The factory must contain a single abstract, non-default method which
 * takes in all of the assisted parameters (in order) and returns the assisted type.
 *
 * <pre><code>
 *   {@literal @}AssistedFactory
 *   interface DataServiceFactory {
 *     DataService create(Config config);
 *   }
 * </code></pre>
 *
 * <p>Dagger will generate an implementation of the factory and bind it to the factory type. The
 * factory can then be used to create an instance of the assisted type:
 *
 * <pre><code>
 *   class MyApplication {
 *     {@literal @}Inject DataServiceFactory dataServiceFactory;
 *
 *     dataService = dataServiceFactory.create(new Config(...));
 *   }
 * </code></pre>
 */
@Documented
@Retention(RUNTIME)
@Target(CONSTRUCTOR)
public @interface AssistedInject {}

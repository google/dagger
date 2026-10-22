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

package dagger.hilt.android.work;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a class that is a WorkManager {@link
 * androidx.work.ListenableWorker} to enable injection with Hilt.
 *
 * <p>Hilt currently supports the following types of workers:
 *
 * <ul>
 *   <li>{@link androidx.work.Worker}
 *   <li>{@link androidx.work.CoroutineWorker}
 * </ul>
 *
 * <p>The annotated class must have a single constructor annotated with
 * {@code @Inject} or {@code @AssistedInject}.
 *
 * <p>Example usage with a Worker:
 * <pre>
 *   &#64;HiltWorker
 *   public class MyWorker extends Worker {
 *     &#64;Inject
 *     public MyWorker(
 *         &#64;Assisted Context context,
 *         &#64;Assisted WorkerParameters workerParams,
 *         MyDependency myDependency
 *     ) {
 *       super(context, workerParams);
 *     }
 *
 *     &#64;Override
 *     public Result doWork() { ... }
 *   }
 * </pre>
 */
@Documented
@Retention(CLASS)
@Target(TYPE)
public @interface HiltWorker {}

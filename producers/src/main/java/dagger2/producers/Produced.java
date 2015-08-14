/*
 * Copyright (C) 2014 Google Inc.
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
package dagger2.producers;

import dagger2.internal.Beta;
import java.util.concurrent.ExecutionException;

/**
 * An interface that represents the result of a {@linkplain Producer production} of type {@code T},
 * or an exception that was thrown during that production. For any type {@code T} that can be
 * injected, you can also inject {@code Produced<T>}, which enables handling of any exceptions that
 * were thrown during the production of {@code T}.
 *
 * <p>For example: <pre><code>
 *   {@literal @}Produces Html getResponse(
 *       UserInfo criticalInfo, {@literal Produced<ExtraInfo>} noncriticalInfo) {
 *     try {
 *       return new Html(criticalInfo, noncriticalInfo.get());
 *     } catch (ExecutionException e) {
 *       logger.warning(e, "Noncritical info");
 *       return new Html(criticalInfo);
 *     }
 *   }
 * </code></pre>
 *
 * @author Jesse Beder
 */
@Beta
public interface Produced<T> {
  /**
   * Returns the result of a production.
   *
   * @throws ExecutionException if the production threw an exception
   */
  T get() throws ExecutionException;
}

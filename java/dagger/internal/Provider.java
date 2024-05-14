/*
 * Copyright (C) 2023 The Dagger Authors.
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

/**
 * Internal Provider interface to make support for {@code javax.inject.Provider} and
 * {@code jakarta.inject.Provider} easier. Do not use outside of Dagger implementation code.
 */
// TODO(erichang): Make this also extend the Jakarta Provider
public interface Provider<T> extends javax.inject.Provider<T>{
}

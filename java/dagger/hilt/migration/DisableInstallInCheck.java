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

/**
 * Marks a {@link dagger.Module}-annotated class to allow it to have no {@link
 * dagger.hilt.InstallIn} annotation.
 *
 * <p>Use this annotation on modules to suppress the error of a missing {@link
 * dagger.hilt.InstallIn} annotation. This is useful in cases where non-Hilt Dagger code must be
 * used long-term. If the issue is widespread, consider changing the error behavior with the
 * compiler flag {@code dagger.hilt.disableModulesHaveInstallInCheck} instead.
 */
public @interface DisableInstallInCheck {}

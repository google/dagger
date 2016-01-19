/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger.internal.codegen;

/**
 * A template for types that generate source files from an annotation processor.
 *
 * @param <T> The input type from which source is to be generated.
 * @author Gregory Kick
 * @since 2.0
 */
interface SourceFileGenerator<T> {
  /** Generates a source file to be compiled for {@code T}. */
  void generate(T input) throws SourceFileGenerationException;
}

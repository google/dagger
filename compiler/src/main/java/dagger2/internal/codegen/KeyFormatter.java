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
package dagger2.internal.codegen;

/**
 * Formats a {@link Key} into a {@link String} suitable for use in error messages
 *
 * @author Christian Gruber
 * @since 2.0
 */
final class KeyFormatter extends Formatter<Key> {

  @Override public String format(Key request) {
    StringBuilder builder = new StringBuilder();
    if (request.qualifier().isPresent()) {
      builder.append(request.qualifier()); // TODO(cgruber): Use AnnotationMirrorFormatter.
      builder.append(' ');
    }
    builder.append(request.type()); // TODO(cgruber): Use TypeMirrorFormatter.
    return builder.toString();
  }
}

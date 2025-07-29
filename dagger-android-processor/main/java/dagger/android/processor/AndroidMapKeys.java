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

package dagger.android.processor;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import java.util.Optional;

final class AndroidMapKeys {
  /**
   * If {@code mapKey} is {@code AndroidInjectionKey}, returns the string value for the map key. If
   * it's {@link dagger.multibindings.ClassKey}, returns the fully-qualified class name of the
   * annotation value. Otherwise returns {@link Optional#empty()}.
   */
  static Optional<String> injectedTypeFromMapKey(XAnnotation mapKey) {
    XAnnotationValue mapKeyClass = mapKey.getAnnotationValue("value");
    if (mapKeyClass.hasStringValue()) {
      return Optional.of(mapKeyClass.asString());
    } else if (mapKeyClass.hasTypeValue()) {
      return Optional.of(mapKeyClass.asType().getTypeElement().getQualifiedName());
    } else {
      return Optional.empty();
    }
  }
}

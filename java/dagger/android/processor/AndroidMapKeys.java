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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;

import com.google.auto.common.MoreTypes;
import dagger.android.AndroidInjectionKey;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

final class AndroidMapKeys {
  /**
   * If {@code mapKey} is {@link AndroidInjectionKey}, returns the string value for the map key. If
   * it's {@link dagger.multibindings.ClassKey}, returns the fully-qualified class name of the
   * annotation value. Otherwise returns {@link Optional#empty()}.
   */
  static Optional<String> injectedTypeFromMapKey(AnnotationMirror mapKey) {
    Object mapKeyClass = getAnnotationValue(mapKey, "value").getValue();
    if (mapKeyClass instanceof String) {
      return Optional.of((String) mapKeyClass);
    } else if (mapKeyClass instanceof TypeMirror) {
      TypeElement type = MoreTypes.asTypeElement((TypeMirror) mapKeyClass);
      return Optional.of(type.getQualifiedName().toString());
    } else {
      return Optional.empty();
    }
  }
}

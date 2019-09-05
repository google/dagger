/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.internal.codegen.kotlin;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static dagger.internal.codegen.base.MoreAnnotationValues.asAnnotationValues;
import static dagger.internal.codegen.langmodel.DaggerElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;

import dagger.internal.codegen.base.ClearableCache;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import kotlin.Metadata;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

/**
 * Factory for parsing and creating Kotlin metadata data object.
 *
 * <p>The metadata is cache since it can be expensive to parse the information stored in a proto
 * binary string format in the metadata annotation values.
 */
@Singleton
public final class KotlinMetadataFactory implements ClearableCache {
  private final Map<TypeElement, Optional<KotlinMetadata>> metadataCache = new HashMap<>();

  @Inject
  KotlinMetadataFactory() {}

  public Optional<KotlinMetadata> create(Element element) {
    TypeElement enclosingElement = closestEnclosingTypeElement(element);
    return metadataCache.computeIfAbsent(
        enclosingElement,
        typeElement ->
            kmClassOf(typeElement)
                .map(classMetadata -> new KotlinMetadata(typeElement, classMetadata)));
  }

  private static Optional<KmClass> kmClassOf(TypeElement typeElement) {
    Optional<AnnotationMirror> metadataAnnotation =
        getAnnotationMirror(typeElement, Metadata.class);
    if (!metadataAnnotation.isPresent()) {
      return Optional.empty();
    }
    KotlinClassHeader header =
        new KotlinClassHeader(
            getIntValue(metadataAnnotation.get(), "k"),
            getIntArrayValue(metadataAnnotation.get(), "mv"),
            getIntArrayValue(metadataAnnotation.get(), "bv"),
            getStringArrayValue(metadataAnnotation.get(), "d1"),
            getStringArrayValue(metadataAnnotation.get(), "d2"),
            getStringValue(metadataAnnotation.get(), "xs"),
            getStringValue(metadataAnnotation.get(), "pn"),
            getIntValue(metadataAnnotation.get(), "xi"));
    KotlinClassMetadata metadata = KotlinClassMetadata.read(header);
    if (metadata == null) {
      // Should only happen on Kotlin < 1.0 (i.e. metadata version < 1.1)
      return Optional.empty();
    }
    if (metadata instanceof KotlinClassMetadata.Class) {
      // TODO(user): If when we need other types of metadata then move to right method.
      return Optional.of(((KotlinClassMetadata.Class) metadata).toKmClass());
    } else {
      // Unsupported
      return Optional.empty();
    }
  }

  private static int getIntValue(AnnotationMirror annotation, String valueName) {
    return (int) getAnnotationValue(annotation, valueName).getValue();
  }

  private static String getStringValue(AnnotationMirror annotation, String valueName) {
    return getAnnotationValue(annotation, valueName).getValue().toString();
  }

  private static int[] getIntArrayValue(AnnotationMirror annotation, String valueName) {
    return asAnnotationValues(getAnnotationValue(annotation, valueName)).stream()
        .mapToInt(it -> (int) it.getValue())
        .toArray();
  }

  private static String[] getStringArrayValue(AnnotationMirror annotation, String valueName) {
    return asAnnotationValues(getAnnotationValue(annotation, valueName)).stream()
        .map(it -> it.getValue().toString())
        .toArray(String[]::new);
  }

  @Override
  public void clearCache() {
    metadataCache.clear();
  }
}

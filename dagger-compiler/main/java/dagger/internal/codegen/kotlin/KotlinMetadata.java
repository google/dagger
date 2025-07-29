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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableMap;
import dagger.internal.codegen.extension.DaggerCollectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Data class of a TypeElement and its Kotlin metadata. */
@AutoValue
abstract class KotlinMetadata {
  // Kotlin suffix for fields that are for a delegated property.
  // See:
  // https://github.com/JetBrains/kotlin/blob/master/core/compiler.common.jvm/src/org/jetbrains/kotlin/load/java/JvmAbi.kt#L32
  private static final String DELEGATED_PROPERTY_NAME_SUFFIX = "$delegate";

  // Map that associates field elements with its Kotlin synthetic method for annotations.
  private final Map<XFieldElement, Optional<MethodForAnnotations>> elementFieldAnnotationMethodMap =
      new HashMap<>();

  // Map that associates field elements with its Kotlin getter method.
  private final Map<XFieldElement, Optional<XMethodElement>> elementFieldGetterMethodMap =
      new HashMap<>();

  abstract XTypeElement typeElement();

  abstract ClassMetadata classMetadata();

  @Memoized
  ImmutableMap<String, XMethodElement> methodDescriptors() {
    return typeElement().getDeclaredMethods().stream()
        .collect(toImmutableMap(XMethodElement::getJvmDescriptor, Function.identity()));
  }

  /** Gets the synthetic method for annotations of a given field element. */
  Optional<XMethodElement> getSyntheticAnnotationMethod(XFieldElement fieldElement) {
    return getAnnotationMethod(fieldElement)
        .map(
            methodForAnnotations -> {
              if (methodForAnnotations == MethodForAnnotations.MISSING) {
                throw new IllegalStateException(
                    "Method for annotations is missing for " + fieldElement);
              }
              return methodForAnnotations.method();
            });
  }

  /**
   * Returns true if the synthetic method for annotations is missing. This can occur when inspecting
   * the Kotlin metadata of a property from another compilation unit.
   */
  boolean isMissingSyntheticAnnotationMethod(XFieldElement fieldElement) {
    return getAnnotationMethod(fieldElement)
        .map(methodForAnnotations -> methodForAnnotations == MethodForAnnotations.MISSING)
        // This can be missing if there was no property annotation at all (e.g. no annotations or
        // the qualifier is already properly attached to the field). For these cases, it isn't
        // considered missing since there was no method to look for in the first place.
        .orElse(false);
  }

  private Optional<MethodForAnnotations> getAnnotationMethod(XFieldElement fieldElement) {
    return elementFieldAnnotationMethodMap.computeIfAbsent(
        fieldElement, this::getAnnotationMethodUncached);
  }

  private Optional<MethodForAnnotations> getAnnotationMethodUncached(XFieldElement fieldElement) {
    return Optional.ofNullable(findProperty(fieldElement).getMethodForAnnotationsSignature())
        .map(
            signature ->
                Optional.ofNullable(methodDescriptors().get(signature))
                    .map(MethodForAnnotations::create)
                    // The method may be missing across different compilations.
                    // See https://youtrack.jetbrains.com/issue/KT-34684
                    .orElse(MethodForAnnotations.MISSING));
  }

  /** Gets the getter method of a given field element corresponding to a property. */
  Optional<XMethodElement> getPropertyGetter(XFieldElement fieldElement) {
    return elementFieldGetterMethodMap.computeIfAbsent(
        fieldElement, this::getPropertyGetterUncached);
  }

  private Optional<XMethodElement> getPropertyGetterUncached(XFieldElement fieldElement) {
    return Optional.ofNullable(findProperty(fieldElement).getGetterSignature())
        .flatMap(signature -> Optional.ofNullable(methodDescriptors().get(signature)));
  }

  private PropertyMetadata findProperty(XFieldElement field) {
    String fieldDescriptor = field.getJvmDescriptor();
    if (classMetadata().getPropertiesBySignature().containsKey(fieldDescriptor)) {
      return classMetadata().getPropertiesBySignature().get(fieldDescriptor);
    } else {
      // Fallback to finding property by name, see: https://youtrack.jetbrains.com/issue/KT-35124
      final String propertyName = getPropertyNameFromField(field);
      return classMetadata().getPropertiesBySignature().values().stream()
          .filter(property -> propertyName.contentEquals(property.getName())) // SUPPRESS_GET_NAME_CHECK
          .collect(DaggerCollectors.onlyElement());
    }
  }

  private static String getPropertyNameFromField(XFieldElement field) {
    String name = getSimpleName(field);
    if (name.endsWith(DELEGATED_PROPERTY_NAME_SUFFIX)) {
      return name.substring(0, name.length() - DELEGATED_PROPERTY_NAME_SUFFIX.length());
    } else {
      return name;
    }
  }

  /** Parse Kotlin class metadata from a given type element. */
  static KotlinMetadata from(XTypeElement typeElement) {
    return new AutoValue_KotlinMetadata(typeElement, ClassMetadata.of(typeElement));
  }

  @AutoValue
  abstract static class MethodForAnnotations {
    static MethodForAnnotations create(XMethodElement method) {
      return new AutoValue_KotlinMetadata_MethodForAnnotations(method);
    }

    static final MethodForAnnotations MISSING = MethodForAnnotations.create(null);

    @Nullable
    abstract XMethodElement method();
  }
}

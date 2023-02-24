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

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.extension.DaggerCollectors;
import dagger.internal.codegen.javapoet.TypeNames;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import kotlin.Metadata;
import kotlinx.metadata.Flag;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMetadataUtil;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.KotlinClassMetadata;

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
    return findProperty(fieldElement)
        .methodForAnnotationsSignature()
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
    return findProperty(fieldElement)
        .getterSignature()
        .flatMap(signature -> Optional.ofNullable(methodDescriptors().get(signature)));
  }

  private PropertyMetadata findProperty(XFieldElement field) {
    String fieldDescriptor = field.getJvmDescriptor();
    if (classMetadata().propertiesByFieldSignature().containsKey(fieldDescriptor)) {
      return classMetadata().propertiesByFieldSignature().get(fieldDescriptor);
    } else {
      // Fallback to finding property by name, see: https://youtrack.jetbrains.com/issue/KT-35124
      final String propertyName = getPropertyNameFromField(field);
      return classMetadata().propertiesByFieldSignature().values().stream()
          .filter(property -> propertyName.contentEquals(property.name()))
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
    return new AutoValue_KotlinMetadata(typeElement, ClassMetadata.create(metadataOf(typeElement)));
  }

  private static KotlinClassMetadata.Class metadataOf(XTypeElement typeElement) {
    XAnnotation annotationMirror = typeElement.getAnnotation(TypeNames.KOTLIN_METADATA);
    Preconditions.checkNotNull(annotationMirror);
    Metadata metadataAnnotation =
        JvmMetadataUtil.Metadata(
            annotationMirror.getAsInt("k"),
            annotationMirror.getAsIntList("mv").stream().mapToInt(Integer::intValue).toArray(),
            annotationMirror.getAsStringList("d1").toArray(new String[0]),
            annotationMirror.getAsStringList("d2").toArray(new String[0]),
            annotationMirror.getAsString("xs"),
            annotationMirror.getAnnotationValue("pn").hasStringValue()
                ? annotationMirror.getAsString("pn")
                : null,
            annotationMirror.getAnnotationValue("xi").hasIntValue()
                ? annotationMirror.getAsInt("xi")
                : null);
    KotlinClassMetadata metadata = KotlinClassMetadata.read(metadataAnnotation);
    if (metadata == null) {
      // Can happen if Kotlin < 1.0 or if metadata version is not supported, i.e.
      // kotlinx-metadata-jvm is outdated.
      throw new IllegalStateException(
          "Unable to read Kotlin metadata due to unsupported metadata version.");
    }
    if (metadata instanceof KotlinClassMetadata.Class) {
      // TODO(danysantiago): If when we need other types of metadata then move to right method.
      return (KotlinClassMetadata.Class) metadata;
    } else {
      throw new IllegalStateException("Unsupported metadata type: " + metadata);
    }
  }

  @AutoValue
  abstract static class ClassMetadata extends BaseMetadata {
    abstract Optional<String> companionObjectName();

    abstract ImmutableSet<FunctionMetadata> constructors();

    abstract ImmutableMap<String, FunctionMetadata> functionsBySignature();

    abstract ImmutableMap<String, PropertyMetadata> propertiesByFieldSignature();

    static ClassMetadata create(KotlinClassMetadata.Class metadata) {
      KmClass kmClass = metadata.toKmClass();
      ClassMetadata.Builder builder =
          ClassMetadata.builder(
              kmClass.getFlags(), kmClass.getName()); // // SUPPRESS_GET_NAME_CHECK
      builder.companionObjectName(Optional.ofNullable(kmClass.getCompanionObject()));
      kmClass.getConstructors().forEach(it -> builder.addConstructor(FunctionMetadata.create(it)));
      kmClass.getFunctions().forEach(it -> builder.addFunction(FunctionMetadata.create(it)));
      kmClass.getProperties().forEach(it -> builder.addProperty(PropertyMetadata.create(it)));
      return builder.build();
    }

    private static Builder builder(int flags, String name) {
      return new AutoValue_KotlinMetadata_ClassMetadata.Builder().flags(flags).name(name);
    }

    @AutoValue.Builder
    abstract static class Builder implements BaseMetadata.Builder<Builder> {
      abstract Builder companionObjectName(Optional<String> companionObjectName);

      abstract ImmutableSet.Builder<FunctionMetadata> constructorsBuilder();

      abstract ImmutableMap.Builder<String, FunctionMetadata> functionsBySignatureBuilder();

      abstract ImmutableMap.Builder<String, PropertyMetadata> propertiesByFieldSignatureBuilder();

      Builder addConstructor(FunctionMetadata constructor) {
        constructorsBuilder().add(constructor);
        functionsBySignatureBuilder().put(constructor.signature(), constructor);
        return this;
      }

      Builder addFunction(FunctionMetadata function) {
        functionsBySignatureBuilder().put(function.signature(), function);
        return this;
      }

      Builder addProperty(PropertyMetadata property) {
        if (property.fieldSignature().isPresent()) {
          propertiesByFieldSignatureBuilder().put(property.fieldSignature().get(), property);
        }
        return this;
      }

      abstract ClassMetadata build();
    }
  }

  @AutoValue
  abstract static class FunctionMetadata extends BaseMetadata {
    abstract String signature();

    abstract ImmutableList<ValueParameterMetadata> parameters();

    static FunctionMetadata create(KmConstructor metadata) {
      FunctionMetadata.Builder builder = FunctionMetadata.builder(metadata.getFlags(), "<init>");
      metadata
          .getValueParameters()
          .forEach(
              it ->
                  builder.addParameter(
                      ValueParameterMetadata.create(
                          it.getFlags(), it.getName()))); // SUPPRESS_GET_NAME_CHECK
      builder.signature(Objects.requireNonNull(JvmExtensionsKt.getSignature(metadata)).asString());
      return builder.build();
    }

    static FunctionMetadata create(KmFunction metadata) {
      FunctionMetadata.Builder builder =
          FunctionMetadata.builder(
              metadata.getFlags(), metadata.getName()); // SUPPRESS_GET_NAME_CHECK
      metadata
          .getValueParameters()
          .forEach(
              it ->
                  builder.addParameter(
                      ValueParameterMetadata.create(
                          it.getFlags(), it.getName()))); // SUPPRESS_GET_NAME_CHECK
      builder.signature(Objects.requireNonNull(JvmExtensionsKt.getSignature(metadata)).asString());
      return builder.build();
    }

    private static Builder builder(int flags, String name) {
      return new AutoValue_KotlinMetadata_FunctionMetadata.Builder().flags(flags).name(name);
    }

    @AutoValue.Builder
    abstract static class Builder implements BaseMetadata.Builder<Builder> {
      abstract Builder signature(String signature);

      abstract ImmutableList.Builder<ValueParameterMetadata> parametersBuilder();

      Builder addParameter(ValueParameterMetadata parameter) {
        parametersBuilder().add(parameter);
        return this;
      }

      abstract FunctionMetadata build();
    }
  }

  @AutoValue
  abstract static class PropertyMetadata extends BaseMetadata {
    /** Returns the JVM field descriptor of the backing field of this property. */
    abstract Optional<String> fieldSignature();

    abstract Optional<String> getterSignature();

    /** Returns JVM method descriptor of the synthetic method for property annotations. */
    abstract Optional<String> methodForAnnotationsSignature();

    static PropertyMetadata create(KmProperty metadata) {
      PropertyMetadata.Builder builder =
          PropertyMetadata.builder(
              metadata.getFlags(), metadata.getName()); // SUPPRESS_GET_NAME_CHECK
      builder.fieldSignature(
          Optional.ofNullable(JvmExtensionsKt.getFieldSignature(metadata))
              .map(JvmFieldSignature::asString));
      builder.getterSignature(
          Optional.ofNullable(JvmExtensionsKt.getGetterSignature(metadata))
              .map(JvmMethodSignature::asString));
      builder.methodForAnnotationsSignature(
          Optional.ofNullable(JvmExtensionsKt.getSyntheticMethodForAnnotations(metadata))
              .map(JvmMethodSignature::asString));
      return builder.build();
    }

    private static Builder builder(int flags, String name) {
      return new AutoValue_KotlinMetadata_PropertyMetadata.Builder().flags(flags).name(name);
    }

    @AutoValue.Builder
    interface Builder extends BaseMetadata.Builder<Builder> {
      Builder fieldSignature(Optional<String> signature);

      Builder getterSignature(Optional<String> signature);

      Builder methodForAnnotationsSignature(Optional<String> signature);

      PropertyMetadata build();
    }
  }

  @AutoValue
  abstract static class ValueParameterMetadata extends BaseMetadata {
    private static ValueParameterMetadata create(int flags, String name) {
      return new AutoValue_KotlinMetadata_ValueParameterMetadata(flags, name);
    }
  }

  abstract static class BaseMetadata {
    /** Returns the Kotlin metadata flags for this property. */
    abstract int flags();

    /** returns {@code true} if the given flag (e.g. {@link Flag.IS_PRIVATE}) applies. */
    boolean flags(Flag flag) {
      return flag.invoke(flags());
    }

    /** Returns the simple name of this property. */
    abstract String name();

    interface Builder<BuilderT> {
      BuilderT flags(int flags);

      BuilderT name(String name);
    }
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

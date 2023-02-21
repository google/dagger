/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.hilt.processor.internal.kotlin;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;
import static com.google.auto.common.AnnotationValues.getAnnotationValues;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static dagger.hilt.processor.internal.ElementDescriptors.getMethodDescriptor;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static kotlinx.metadata.Flag.ValueParameter.DECLARES_DEFAULT_VALUE;

import com.google.auto.common.AnnotationValues;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ElementDescriptors;
import dagger.internal.codegen.extension.DaggerCollectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
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
  private final Map<VariableElement, Optional<MethodForAnnotations>>
      elementFieldAnnotationMethodMap = new HashMap<>();

  // Map that associates field elements with its Kotlin getter method.
  private final Map<VariableElement, Optional<ExecutableElement>> elementFieldGetterMethodMap =
      new HashMap<>();

  abstract TypeElement typeElement();

  abstract ClassMetadata classMetadata();

  @Memoized
  ImmutableMap<String, ExecutableElement> methodDescriptors() {
    return ElementFilter.methodsIn(typeElement().getEnclosedElements()).stream()
        .collect(toImmutableMap(ElementDescriptors::getMethodDescriptor, Function.identity()));
  }

  /** Returns true if any constructor of the defined a default parameter. */
  @Memoized
  boolean containsConstructorWithDefaultParam() {
    return classMetadata().constructors().stream()
        .flatMap(constructor -> constructor.parameters().stream())
        .anyMatch(parameter -> parameter.flags(DECLARES_DEFAULT_VALUE));
  }

  /** Gets the synthetic method for annotations of a given field element. */
  Optional<ExecutableElement> getSyntheticAnnotationMethod(VariableElement fieldElement) {
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
  boolean isMissingSyntheticAnnotationMethod(VariableElement fieldElement) {
    return getAnnotationMethod(fieldElement)
        .map(methodForAnnotations -> methodForAnnotations == MethodForAnnotations.MISSING)
        // This can be missing if there was no property annotation at all (e.g. no annotations or
        // the qualifier is already properly attached to the field). For these cases, it isn't
        // considered missing since there was no method to look for in the first place.
        .orElse(false);
  }

  private Optional<MethodForAnnotations> getAnnotationMethod(VariableElement fieldElement) {
    return elementFieldAnnotationMethodMap.computeIfAbsent(
        fieldElement, this::getAnnotationMethodUncached);
  }

  private Optional<MethodForAnnotations> getAnnotationMethodUncached(VariableElement fieldElement) {
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
  Optional<ExecutableElement> getPropertyGetter(VariableElement fieldElement) {
    return elementFieldGetterMethodMap.computeIfAbsent(
        fieldElement, this::getPropertyGetterUncached);
  }

  private Optional<ExecutableElement> getPropertyGetterUncached(VariableElement fieldElement) {
    return findProperty(fieldElement)
        .getterSignature()
        .flatMap(signature -> Optional.ofNullable(methodDescriptors().get(signature)));
  }

  private PropertyMetadata findProperty(VariableElement field) {
    String fieldDescriptor = ElementDescriptors.getFieldDescriptor(field);
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

  private static String getPropertyNameFromField(VariableElement field) {
    String name = field.getSimpleName().toString();
    if (name.endsWith(DELEGATED_PROPERTY_NAME_SUFFIX)) {
      return name.substring(0, name.length() - DELEGATED_PROPERTY_NAME_SUFFIX.length());
    } else {
      return name;
    }
  }

  FunctionMetadata getFunctionMetadata(ExecutableElement method) {
    return classMetadata().functionsBySignature().get(getMethodDescriptor(method));
  }

  /** Parse Kotlin class metadata from a given type element. */
  static KotlinMetadata from(TypeElement typeElement) {
    return new AutoValue_KotlinMetadata(typeElement, ClassMetadata.create(metadataOf(typeElement)));
  }

  private static KotlinClassMetadata.Class metadataOf(TypeElement typeElement) {
    AnnotationMirror annotationMirror =
        getAnnotationMirror(typeElement, ClassNames.KOTLIN_METADATA.canonicalName()).get();
    Metadata metadataAnnotation =
        JvmMetadataUtil.Metadata(
            getIntValue(annotationMirror, "k"),
            getIntArrayValue(annotationMirror, "mv"),
            getStringArrayValue(annotationMirror, "d1"),
            getStringArrayValue(annotationMirror, "d2"),
            getStringValue(annotationMirror, "xs"),
            getOptionalStringValue(annotationMirror, "pn").orElse(null),
            getOptionalIntValue(annotationMirror, "xi").orElse(null));
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
      ClassMetadata.Builder builder = ClassMetadata.builder(kmClass.getFlags(), kmClass.getName());
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
                  builder.addParameter(ValueParameterMetadata.create(it.getFlags(), it.getName())));
      builder.signature(Objects.requireNonNull(JvmExtensionsKt.getSignature(metadata)).asString());
      return builder.build();
    }

    static FunctionMetadata create(KmFunction metadata) {
      FunctionMetadata.Builder builder =
          FunctionMetadata.builder(metadata.getFlags(), metadata.getName());
      metadata
          .getValueParameters()
          .forEach(
              it ->
                  builder.addParameter(ValueParameterMetadata.create(it.getFlags(), it.getName())));
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
          PropertyMetadata.builder(metadata.getFlags(), metadata.getName());
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
    static MethodForAnnotations create(ExecutableElement method) {
      return new AutoValue_KotlinMetadata_MethodForAnnotations(method);
    }

    static final MethodForAnnotations MISSING = MethodForAnnotations.create(null);

    @Nullable
    abstract ExecutableElement method();
  }

  private static int getIntValue(AnnotationMirror annotation, String valueName) {
    return AnnotationValues.getInt(getAnnotationValue(annotation, valueName));
  }

  private static Optional<Integer> getOptionalIntValue(
      AnnotationMirror annotation, String valueName) {
    return isValuePresent(annotation, valueName)
        ? Optional.of(getIntValue(annotation, valueName))
        : Optional.empty();
  }

  private static int[] getIntArrayValue(AnnotationMirror annotation, String valueName) {
    return getAnnotationValues(getAnnotationValue(annotation, valueName)).stream()
        .mapToInt(AnnotationValues::getInt)
        .toArray();
  }

  private static String getStringValue(AnnotationMirror annotation, String valueName) {
    return AnnotationValues.getString(getAnnotationValue(annotation, valueName));
  }

  private static Optional<String> getOptionalStringValue(
      AnnotationMirror annotation, String valueName) {
    return isValuePresent(annotation, valueName)
        ? Optional.of(getStringValue(annotation, valueName))
        : Optional.empty();
  }

  private static String[] getStringArrayValue(AnnotationMirror annotation, String valueName) {
    return getAnnotationValues(getAnnotationValue(annotation, valueName)).stream()
        .map(AnnotationValues::getString)
        .toArray(String[]::new);
  }

  private static boolean isValuePresent(AnnotationMirror annotation, String valueName) {
    return getAnnotationValuesWithDefaults(annotation).keySet().stream()
        .anyMatch(member -> member.getSimpleName().contentEquals(valueName));
  }
}

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
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.base.MoreAnnotationValues.asAnnotationValues;
import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import kotlin.Metadata;
import kotlinx.metadata.Flag;
import kotlinx.metadata.KmClassVisitor;
import kotlinx.metadata.KmExtensionType;
import kotlinx.metadata.KmPropertyExtensionVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

/** Data class of a TypeElement and its Kotlin metadata. */
final class KotlinMetadata {

  private final TypeElement typeElement;

  /**
   * Kotlin metadata flag for this class.
   *
   * <p>Use {@link Flag.Class} to apply the right mask and obtain a specific value.
   */
  private final int flags;

  // Map that associates @Inject field elements with its Kotlin synthetic method for annotations.
  private final Supplier<Map<VariableElement, Optional<ExecutableElement>>>
      elementFieldAnnotationMethodMap;

  private KotlinMetadata(TypeElement typeElement, int flags, List<Property> properties) {
    this.typeElement = typeElement;
    this.flags = flags;
    this.elementFieldAnnotationMethodMap =
        Suppliers.memoize(
            () -> {
              Map<String, Property> propertyDescriptors =
                  properties.stream()
                      .filter(property -> property.getFieldSignature().isPresent())
                      .collect(
                          Collectors.toMap(
                              property -> property.getFieldSignature().get(), Function.identity()));
              Map<String, ExecutableElement> methodDescriptors =
                  ElementFilter.methodsIn(typeElement.getEnclosedElements()).stream()
                      .collect(
                          Collectors.toMap(
                              DaggerElements::getMethodDescriptor, Function.identity()));
              return ElementFilter.fieldsIn(typeElement.getEnclosedElements()).stream()
                  .filter(field -> isAnnotationPresent(field, Inject.class))
                  .collect(
                      Collectors.toMap(
                          Function.identity(),
                          field ->
                              Optional.ofNullable(
                                      propertyDescriptors.get(
                                          DaggerElements.getFieldDescriptor(field)))
                                  .flatMap(Property::getMethodForAnnotationsSignature)
                                  .map(methodDescriptors::get)));
            });
  }

  TypeElement getTypeElement() {
    return typeElement;
  }

  /** Gets the synthetic method for annotations of a given @Inject annotated field element. */
  Optional<ExecutableElement> getSyntheticAnnotationMethod(VariableElement fieldElement) {
    checkArgument(elementFieldAnnotationMethodMap.get().containsKey(fieldElement));
    return elementFieldAnnotationMethodMap.get().get(fieldElement);
  }

  boolean isObjectClass() {
    return Flag.Class.IS_OBJECT.invoke(flags);
  }

  /** Parse Kotlin class metadata from a given type element * */
  static Optional<KotlinMetadata> from(TypeElement typeElement) {
    return metadataOf(typeElement)
        .map(
            metadata -> {
              MetadataVisitor visitor = new MetadataVisitor();
              metadata.accept(visitor);
              return new KotlinMetadata(typeElement, visitor.classFlags, visitor.classProperties);
            });
  }

  private static Optional<KotlinClassMetadata.Class> metadataOf(TypeElement typeElement) {
    Optional<AnnotationMirror> metadataAnnotation =
        getAnnotationMirror(typeElement, Metadata.class);
    Preconditions.checkState(metadataAnnotation.isPresent());
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
      return Optional.of((KotlinClassMetadata.Class) metadata);
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

  private static final class MetadataVisitor extends KmClassVisitor {

    int classFlags;
    List<Property> classProperties = new ArrayList<>();

    @Override
    public void visit(int flags, String s) {
      this.classFlags = flags;
    }

    @Override
    public KmPropertyVisitor visitProperty(
        int flags, String name, int getterFlags, int setterFlags) {
      return new KmPropertyVisitor() {
        String fieldSignature;
        String methodForAnnotationsSignature;

        @Override
        public KmPropertyExtensionVisitor visitExtensions(KmExtensionType kmExtensionType) {
          if (!kmExtensionType.equals(JvmPropertyExtensionVisitor.TYPE)) {
            return null;
          }

          return new JvmPropertyExtensionVisitor() {
            @Override
            public void visit(
                int jvmFlags,
                JvmFieldSignature jvmFieldSignature,
                JvmMethodSignature getterSignature,
                JvmMethodSignature setterSignature) {
              if (jvmFieldSignature != null) {
                fieldSignature = jvmFieldSignature.asString();
              }
            }

            @Override
            public void visitSyntheticMethodForAnnotations(JvmMethodSignature methodSignature) {
              if (methodSignature != null) {
                methodForAnnotationsSignature = methodSignature.asString();
              }
            }
          };
        }

        @Override
        public void visitEnd() {
          classProperties.add(
              new Property(
                  name,
                  flags,
                  Optional.ofNullable(fieldSignature),
                  Optional.ofNullable(methodForAnnotationsSignature)));
        }
      };
    }
  }

  /* Data class representing Kotlin Property Metadata */
  private static final class Property {

    private final String name;
    private final int flags;
    private final Optional<String> fieldSignature;
    private final Optional<String> methodForAnnotationsSignature;

    Property(
        String name,
        int flags,
        Optional<String> fieldSignature,
        Optional<String> methodForAnnotationsSignature) {
      this.name = name;
      this.flags = flags;
      this.fieldSignature = fieldSignature;
      this.methodForAnnotationsSignature = methodForAnnotationsSignature;
    }

    /** Returns the simple name of this property. */
    String getName() {
      return name;
    }

    /**
     * Returns the Kotlin metadata flags for this property.
     *
     * <p>Use {@link Flag.Property} to apply the right mask and obtain a specific value.
     */
    int getFlags() {
      return flags;
    }

    /** Returns the JVM field descriptor of the backing field of this property. */
    Optional<String> getFieldSignature() {
      return fieldSignature;
    }

    /** Returns JVM method descriptor of the synthetic method for property annotations. */
    Optional<String> getMethodForAnnotationsSignature() {
      return methodForAnnotationsSignature;
    }
  }
}

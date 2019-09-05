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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;

/** Data class of a TypeElement and its Kotlin metadata. */
final class KotlinMetadata {

  private final TypeElement typeElement;
  private final KmClass kmClass;

  // Map that associates @Inject field elements with its Kotlin synthetic method for annotations.
  private final Supplier<Map<VariableElement, Optional<ExecutableElement>>>
      elementFieldAnnotationMethodMap;

  KotlinMetadata(TypeElement typeElement, KmClass kmClass) {
    this.typeElement = typeElement;
    this.kmClass = kmClass;
    this.elementFieldAnnotationMethodMap =
        Suppliers.memoize(
            () -> {
              Map<String, KmProperty> propertyDescriptors = new HashMap<>();
              kmClass
                  .getProperties()
                  .forEach(
                      property -> {
                        JvmFieldSignature signature = JvmExtensionsKt.getFieldSignature(property);
                        if (signature != null) {
                          propertyDescriptors.put(signature.asString(), property);
                        }
                      });
              Map<String, ExecutableElement> methodDescriptors =
                  ElementFilter.methodsIn(typeElement.getEnclosedElements()).stream()
                      .collect(
                          Collectors.toMap(DaggerElements::getMethodDescriptor, method -> method));
              return ElementFilter.fieldsIn(typeElement.getEnclosedElements()).stream()
                  .filter(field -> isAnnotationPresent(field, Inject.class))
                  .collect(
                      Collectors.toMap(
                          Function.identity(),
                          field ->
                              Optional.ofNullable(
                                      propertyDescriptors.get(
                                          DaggerElements.getFieldDescriptor(field)))
                                  .map(JvmExtensionsKt::getSyntheticMethodForAnnotations)
                                  .map(JvmMethodSignature::asString)
                                  .map(methodDescriptors::get)));
            });
  }

  TypeElement getTypeElement() {
    return typeElement;
  }

  KmClass getKmClass() {
    return kmClass;
  }

  /** Gets the synthetic method for annotations of a given @Inject annotated field element. */
  Optional<ExecutableElement> getSyntheticAnnotationMethod(VariableElement fieldElement) {
    checkArgument(elementFieldAnnotationMethodMap.get().containsKey(fieldElement));
    return elementFieldAnnotationMethodMap.get().get(fieldElement);
  }
}

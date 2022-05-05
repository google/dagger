/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.processingstep;

import static dagger.internal.codegen.binding.MapKeys.getUnwrappedMapKeyType;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.MapKey;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.MapKeyValidator;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.writing.AnnotationCreatorGenerator;
import dagger.internal.codegen.writing.UnwrappedMapKeyGenerator;
import javax.inject.Inject;

/**
 * The annotation processor responsible for validating the mapKey annotation and auto-generate
 * implementation of annotations marked with {@link MapKey @MapKey} where necessary.
 */
final class MapKeyProcessingStep extends TypeCheckingProcessingStep<XTypeElement> {
  private final XMessager messager;
  private final MapKeyValidator mapKeyValidator;
  private final AnnotationCreatorGenerator annotationCreatorGenerator;
  private final UnwrappedMapKeyGenerator unwrappedMapKeyGenerator;

  @Inject
  MapKeyProcessingStep(
      XMessager messager,
      MapKeyValidator mapKeyValidator,
      AnnotationCreatorGenerator annotationCreatorGenerator,
      UnwrappedMapKeyGenerator unwrappedMapKeyGenerator) {
    this.messager = messager;
    this.mapKeyValidator = mapKeyValidator;
    this.annotationCreatorGenerator = annotationCreatorGenerator;
    this.unwrappedMapKeyGenerator = unwrappedMapKeyGenerator;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.MAP_KEY);
  }

  @Override
  protected void process(XTypeElement mapAnnotation, ImmutableSet<ClassName> annotations) {
    ValidationReport mapKeyReport = mapKeyValidator.validate(mapAnnotation);
    mapKeyReport.printMessagesTo(messager);

    if (mapKeyReport.isClean()) {
      if (!mapAnnotation.getAnnotation(TypeNames.MAP_KEY).getAsBoolean("unwrapValue")) {
        annotationCreatorGenerator.generate(mapAnnotation, messager);
      } else if (isAnnotationType(getUnwrappedMapKeyType(mapAnnotation.getType()))) {
        unwrappedMapKeyGenerator.generate(mapAnnotation, messager);
      }
    }
  }

  private boolean isAnnotationType(XType type) {
    return isDeclared(type) && type.getTypeElement().isAnnotationClass();
  }
}

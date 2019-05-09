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

package dagger.internal.codegen;

import static dagger.internal.codegen.MapKeys.getUnwrappedMapKeyType;
import static javax.lang.model.element.ElementKind.ANNOTATION_TYPE;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.MapKey;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * The annotation processor responsible for validating the mapKey annotation and auto-generate
 * implementation of annotations marked with {@link MapKey @MapKey} where necessary.
 */
public class MapKeyProcessingStep extends TypeCheckingProcessingStep<TypeElement> {
  private final Messager messager;
  private final DaggerTypes types;
  private final MapKeyValidator mapKeyValidator;
  private final AnnotationCreatorGenerator annotationCreatorGenerator;
  private final UnwrappedMapKeyGenerator unwrappedMapKeyGenerator;

  @Inject
  MapKeyProcessingStep(
      Messager messager,
      DaggerTypes types,
      MapKeyValidator mapKeyValidator,
      AnnotationCreatorGenerator annotationCreatorGenerator,
      UnwrappedMapKeyGenerator unwrappedMapKeyGenerator) {
    super(MoreElements::asType);
    this.messager = messager;
    this.types = types;
    this.mapKeyValidator = mapKeyValidator;
    this.annotationCreatorGenerator = annotationCreatorGenerator;
    this.unwrappedMapKeyGenerator = unwrappedMapKeyGenerator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(MapKey.class);
  }

  @Override
  protected void process(
      TypeElement mapKeyAnnotationType, ImmutableSet<Class<? extends Annotation>> annotations) {
    ValidationReport<Element> mapKeyReport = mapKeyValidator.validate(mapKeyAnnotationType);
    mapKeyReport.printMessagesTo(messager);

    if (mapKeyReport.isClean()) {
      MapKey mapkey = mapKeyAnnotationType.getAnnotation(MapKey.class);
      if (!mapkey.unwrapValue()) {
        annotationCreatorGenerator.generate(mapKeyAnnotationType, messager);
      } else if (unwrappedValueKind(mapKeyAnnotationType).equals(ANNOTATION_TYPE)) {
        unwrappedMapKeyGenerator.generate(mapKeyAnnotationType, messager);
      }
    }
  }

  private ElementKind unwrappedValueKind(TypeElement mapKeyAnnotationType) {
    DeclaredType unwrappedMapKeyType =
        getUnwrappedMapKeyType(MoreTypes.asDeclared(mapKeyAnnotationType.asType()), types);
    return unwrappedMapKeyType.asElement().getKind();
  }
}

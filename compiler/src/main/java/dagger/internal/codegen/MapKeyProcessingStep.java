/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.MapKey;
import dagger.internal.codegen.MapKeyGenerator.MapKeyCreatorSpecification;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Types;

import static dagger.internal.codegen.MapKeyGenerator.MapKeyCreatorSpecification.unwrappedMapKeyWithAnnotationValue;
import static dagger.internal.codegen.MapKeyGenerator.MapKeyCreatorSpecification.wrappedMapKey;
import static dagger.internal.codegen.MapKeys.getUnwrappedMapKeyType;

/**
 * The annotation processor responsible for validating the mapKey annotation and auto-generate
 * implementation of annotations marked with &#064MapKey where necessary.
 *
 * @author Chenying Hou
 * @since 2.0
 */
public class MapKeyProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
  private final Messager messager;
  private final Types types;
  private final MapKeyValidator mapKeyValidator;
  private final MapKeyGenerator mapKeyGenerator;

  MapKeyProcessingStep(
      Messager messager,
      Types types,
      MapKeyValidator mapKeyValidator,
      MapKeyGenerator mapKeyGenerator) {
    this.messager = messager;
    this.types = types;
    this.mapKeyValidator = mapKeyValidator;
    this.mapKeyGenerator = mapKeyGenerator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(MapKey.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Element element : elementsByAnnotation.get(MapKey.class)) {
      ValidationReport<Element> mapKeyReport = mapKeyValidator.validate(element);
      mapKeyReport.printMessagesTo(messager);

      if (mapKeyReport.isClean()) {
        MapKey mapkey = element.getAnnotation(MapKey.class);
        if (mapkey.unwrapValue()) {
          DeclaredType keyType =
              getUnwrappedMapKeyType(MoreTypes.asDeclared(element.asType()), types);
          if (keyType.asElement().getKind() == ElementKind.ANNOTATION_TYPE) {
            writeCreatorClass(
                unwrappedMapKeyWithAnnotationValue(
                    MoreElements.asType(element), MoreTypes.asTypeElement(keyType)));
          }
        } else {
          writeCreatorClass(wrappedMapKey(MoreElements.asType(element)));
        }
      }
    }
    return ImmutableSet.of();
  }

  private void writeCreatorClass(MapKeyCreatorSpecification mapKeyCreatorType) {
    try {
      mapKeyGenerator.generate(mapKeyCreatorType);
    } catch (SourceFileGenerationException e) {
      e.printMessageTo(messager);
    }
  }
}

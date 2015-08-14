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
package dagger2.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger2.MapKey;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

/**
 * The annotation processor responsible for validating the mapKey annotation and auto-generate
 * implementation of annotations marked with &#064MapKey where necessary.
 *
 * @author Chenying Hou
 * @since 2.0
 */
public class MapKeyProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
  private final Messager messager;
  private final MapKeyValidator mapKeyValidator;
  private final MapKeyGenerator mapKeyGenerator;

  MapKeyProcessingStep(Messager messager, MapKeyValidator mapKeyValidator,
      MapKeyGenerator mapKeyGenerator) {
    this.messager = messager;
    this.mapKeyValidator = mapKeyValidator;
    this.mapKeyGenerator = mapKeyGenerator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(MapKey.class);
  }

  @Override
  public void process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Element element : elementsByAnnotation.get(MapKey.class)) {
      ValidationReport<Element> mapKeyReport = mapKeyValidator.validate(element);
      mapKeyReport.printMessagesTo(messager);

      if (mapKeyReport.isClean()) {
        MapKey mapkey = element.getAnnotation(MapKey.class);
        if (!mapkey.unwrapValue()) {
          try {
            mapKeyGenerator.generate(element);
          } catch (SourceFileGenerationException e) {
            e.printMessageTo(messager);
          }
        }
      }
    }
  }
}

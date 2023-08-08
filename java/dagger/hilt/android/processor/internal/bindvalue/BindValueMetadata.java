/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.bindvalue;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.asField;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XElementKt;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Collection;
import java.util.Optional;

/**
 * Represents metadata for a test class that has {@code BindValue} fields.
 */
@AutoValue
abstract class BindValueMetadata {
  static final ImmutableSet<ClassName> BIND_VALUE_ANNOTATIONS =
      ImmutableSet.of(
          ClassNames.ANDROID_BIND_VALUE);
  static final ImmutableSet<ClassName> BIND_VALUE_INTO_SET_ANNOTATIONS =
      ImmutableSet.of(
          ClassNames.ANDROID_BIND_VALUE_INTO_SET);
  static final ImmutableSet<ClassName> BIND_ELEMENTS_INTO_SET_ANNOTATIONS =
      ImmutableSet.of(
          ClassNames.ANDROID_BIND_ELEMENTS_INTO_SET);
  static final ImmutableSet<ClassName> BIND_VALUE_INTO_MAP_ANNOTATIONS =
      ImmutableSet.of(
          ClassNames.ANDROID_BIND_VALUE_INTO_MAP);

  /**
   * @return the {@code TestRoot} annotated class's name.
   */
  abstract XTypeElement testElement();

  /** @return a {@link ImmutableSet} of elements annotated with @BindValue. */
  abstract ImmutableSet<BindValueElement> bindValueElements();

  /**
   * @return a new BindValueMetadata instance.
   */
  static BindValueMetadata create(
      XTypeElement testElement, Collection<XElement> bindValueElements) {

    ImmutableSet.Builder<BindValueElement> elements = ImmutableSet.builder();
    for (XElement element : bindValueElements) {
      elements.add(BindValueElement.create(element));
    }

    return new AutoValue_BindValueMetadata(testElement, elements.build());
  }

  @AutoValue
  abstract static class BindValueElement {
    abstract XFieldElement fieldElement();

    abstract ClassName annotationName();

    abstract Optional<XAnnotation> qualifier();

    abstract Optional<XAnnotation> mapKey();

    abstract Optional<XMethodElement> getterElement();

    static BindValueElement create(XElement element) {
      ImmutableList<ClassName> bindValues =
          BindValueProcessingStep.getBindValueAnnotations(element);
      ProcessorErrors.checkState(
          bindValues.size() == 1,
          element,
          "Fields can be annotated with only one of @BindValue, @BindValueIntoMap,"
              + " @BindElementsIntoSet, @BindValueIntoSet. Found: %s",
          bindValues.stream().map(m -> "@" + m.simpleName()).collect(toImmutableList()));
      ClassName annotationClassName = getOnlyElement(bindValues);

      ProcessorErrors.checkState(
          XElementKt.isField(element),
          element,
          "@%s can only be used with fields. Found: %s",
          annotationClassName.simpleName(),
          XElements.toStableString(element));

      XFieldElement field = asField(element);
      Optional<XMethodElement> propertyGetter = Optional.ofNullable(field.getGetter());
      if (propertyGetter.isPresent()) {
        ProcessorErrors.checkState(
            !propertyGetter.get().isPrivate(),
            field,
            "@%s field getter cannot be private. Found: %s",
            annotationClassName.simpleName(),
            XElements.toStableString(field));
      } else {
        ProcessorErrors.checkState(
            !XElements.isPrivate(field),
            field,
            "@%s fields cannot be private. Found: %s",
            annotationClassName.simpleName(),
            XElements.toStableString(field));
      }

      ProcessorErrors.checkState(
          !field.hasAnnotation(ClassNames.INJECT),
          field,
          "@%s fields cannot be used with @Inject annotation. Found %s",
          annotationClassName.simpleName(),
          XElements.toStableString(field));

      ImmutableList<XAnnotation> qualifiers = Processors.getQualifierAnnotations(field);
      ProcessorErrors.checkState(
          qualifiers.size() <= 1,
          field,
          "@%s fields cannot have more than one qualifier. Found %s",
          annotationClassName.simpleName(),
          qualifiers.stream().map(XAnnotations::toStableString).collect(toImmutableList()));

      ImmutableList<XAnnotation> mapKeys = Processors.getMapKeyAnnotations(field);
      Optional<XAnnotation> optionalMapKeys;
      if (BIND_VALUE_INTO_MAP_ANNOTATIONS.contains(annotationClassName)) {
        ProcessorErrors.checkState(
            mapKeys.size() == 1,
            field,
            "@BindValueIntoMap fields must have exactly one @MapKey. Found %s",
            mapKeys.stream().map(XAnnotations::toStableString).collect(toImmutableList()));
        optionalMapKeys = Optional.of(mapKeys.get(0));
      } else {
        ProcessorErrors.checkState(
            mapKeys.isEmpty(),
            field,
            "@MapKey can only be used on @BindValueIntoMap fields, not @%s fields",
            annotationClassName.simpleName());
        optionalMapKeys = Optional.empty();
      }

      ImmutableList<XAnnotation> scopes = Processors.getScopeAnnotations(field);
      ProcessorErrors.checkState(
          scopes.isEmpty(),
          field,
          "@%s fields cannot be scoped. Found %s",
          annotationClassName.simpleName(),
          scopes.stream().map(XAnnotations::toStableString).collect(toImmutableList()));

      return new AutoValue_BindValueMetadata_BindValueElement(
          field,
          annotationClassName,
          qualifiers.isEmpty()
              ? Optional.<XAnnotation>empty()
              : Optional.<XAnnotation>of(qualifiers.get(0)),
          optionalMapKeys,
          propertyGetter);
    }
  }
}

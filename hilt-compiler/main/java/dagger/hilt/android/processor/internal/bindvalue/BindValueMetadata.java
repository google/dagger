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
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;

import androidx.room3.compiler.processing.XAnnotation;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XElementKt;
import androidx.room3.compiler.processing.XFieldElement;
import androidx.room3.compiler.processing.XMethodElement;
import androidx.room3.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.LazyString;
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
      XTypeElement testElement, Collection<BindValueElement> bindValueElements) {
    return new AutoValue_BindValueMetadata(testElement, ImmutableSet.copyOf(bindValueElements));
  }

  @AutoValue
  abstract static class BindValueElement {

    abstract XTypeElement testElement();

    abstract XTypeElement enclosingElement();

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
          XElementKt.isTypeElement(element.getEnclosingElement()),
          element.getEnclosingElement(),
          "@%s can only be used within a class. Found: %s",
          annotationClassName.simpleName(),
          LazyString.of(() -> XElements.toStableString(element.getEnclosingElement())));
      XTypeElement enclosingElement = asTypeElement(element.getEnclosingElement());

      XTypeElement testElement =
          enclosingElement.isCompanionObject()
              ? enclosingElement.getEnclosingTypeElement()
              : enclosingElement;
      // Restrict BindValue to the direct test class (e.g. not allowed in a base test class) because
      // otherwise generated BindValue modules from the base class will not associate with the
      // correct test class. This would make the modules apply globally which would be a weird
      // difference since just moving a declaration to the parent would change whether the module is
      // limited to the test that declares it to global.
      ProcessorErrors.checkState(
          testElement.isClass()
              && (testElement.hasAnnotation(ClassNames.HILT_ANDROID_TEST)
              ),
          testElement,
          "@%s can only be used within a class annotated with "
              + "@HiltAndroidTest. Found: %s",
          annotationClassName.simpleName(),
          LazyString.of(() -> XElements.toStableString(testElement)));

      ProcessorErrors.checkState(
          XElementKt.isField(element),
          element,
          "@%s can only be used with fields. Found: %s",
          annotationClassName.simpleName(),
          LazyString.of(() -> XElements.toStableString(element)));

      XFieldElement field = asField(element);
      Optional<XMethodElement> propertyGetter = Optional.ofNullable(field.getGetter());
      if (propertyGetter.isPresent()) {
        ProcessorErrors.checkState(
            !propertyGetter.get().isPrivate(),
            field,
            "@%s field getter cannot be private. Found: %s",
            annotationClassName.simpleName(),
            LazyString.of(() -> XElements.toStableString(field)));
      } else {
        ProcessorErrors.checkState(
            !XElements.isPrivate(field),
            field,
            "@%s fields cannot be private. Found: %s",
            annotationClassName.simpleName(),
            LazyString.of(() -> XElements.toStableString(field)));
      }

      ProcessorErrors.checkState(
          !Processors.isAnnotatedWithInject(field),
          field,
          "@%s fields cannot be used with @Inject annotation. Found %s",
          annotationClassName.simpleName(),
          LazyString.of(() -> XElements.toStableString(field)));

      ImmutableList<XAnnotation> qualifiers = Processors.getQualifierAnnotations(field);
      ProcessorErrors.checkState(
          qualifiers.size() <= 1,
          field,
          "@%s fields cannot have more than one qualifier. Found %s",
          annotationClassName.simpleName(),
          LazyString.of(
              () ->
                  qualifiers.stream()
                      .map(XAnnotations::toStableString)
                      .collect(toImmutableList())
                      .toString()));

      ImmutableList<XAnnotation> mapKeys = Processors.getMapKeyAnnotations(field);
      Optional<XAnnotation> optionalMapKeys;
      if (BIND_VALUE_INTO_MAP_ANNOTATIONS.contains(annotationClassName)) {
        ProcessorErrors.checkState(
            mapKeys.size() == 1,
            field,
            "@BindValueIntoMap fields must have exactly one @MapKey. Found %s",
            LazyString.of(
                () ->
                    mapKeys.stream()
                        .map(XAnnotations::toStableString)
                        .collect(toImmutableList())
                        .toString()));
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
          LazyString.of(
              () ->
                  scopes.stream()
                      .map(XAnnotations::toStableString)
                      .collect(toImmutableList())
                      .toString()));

      return new AutoValue_BindValueMetadata_BindValueElement(
          testElement,
          enclosingElement,
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

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

package dagger.hilt.processor.internal.aggregateddeps;


import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.util.Optional;

/** PkgPrivateModuleMetadata contains a set of utilities for processing package private modules. */
@AutoValue
public abstract class PkgPrivateMetadata {
  /** Returns the public Hilt wrapped type or the type itself if it is already public. */
  public static XTypeElement publicModule(XTypeElement element, XProcessingEnv env) {
    return publicDep(element, env, ClassNames.MODULE);
  }

  /** Returns the public Hilt wrapped type or the type itself if it is already public. */
  public static XTypeElement publicEarlyEntryPoint(XTypeElement element, XProcessingEnv env) {
    return publicDep(element, env, ClassNames.EARLY_ENTRY_POINT);
  }

  /** Returns the public Hilt wrapped type or the type itself if it is already public. */
  public static XTypeElement publicEntryPoint(XTypeElement element, XProcessingEnv env) {
    return publicDep(element, env, ClassNames.ENTRY_POINT);
  }

  private static XTypeElement publicDep(
      XTypeElement element, XProcessingEnv env, ClassName annotation) {

    return of(element, annotation)
        .map(PkgPrivateMetadata::generatedClassName)
        .map(ClassName::canonicalName)
        .map(env::requireTypeElement)
        .orElse(element);
  }

  private static final String PREFIX = "HiltWrapper_";

  /** Returns the base class name of the elemenet. */
  TypeName baseClassName() {
    return getTypeElement().getType().getTypeName();
  }

  /** Returns TypeElement for the module element the metadata object represents */
  abstract XTypeElement getTypeElement();

  /**
   * Returns an optional @InstallIn AnnotationMirror for the module element the metadata object
   * represents
   */
  abstract Optional<XAnnotation> getOptionalInstallInAnnotation();

  /** Return the Type of this package private element. */
  abstract ClassName getAnnotation();

  /** Returns the expected genenerated classname for the element the metadata object represents */
  final ClassName generatedClassName() {
    return Processors.prepend(
        Processors.getEnclosedClassName(getTypeElement().getClassName()), PREFIX);
  }

  /**
   * Returns an Optional PkgPrivateMetadata requiring Hilt processing, otherwise returns an empty
   * Optional.
   */
  static Optional<PkgPrivateMetadata> of(XTypeElement element, ClassName annotation) {
    // If this is a public element no wrapping is needed
    if (isEffectivePublic(element)) {
      return Optional.empty();
    }

    Optional<XAnnotation> installIn;
    if (element.hasAnnotation(ClassNames.INSTALL_IN)) {
      installIn = Optional.of(element.getAnnotation(ClassNames.INSTALL_IN));
    } else if (element.hasAnnotation(ClassNames.TEST_INSTALL_IN)) {
      installIn = Optional.of(element.getAnnotation(ClassNames.TEST_INSTALL_IN));
    } else {
      throw new IllegalStateException(
          "Expected element to be annotated with @InstallIn: " + element);
    }

    if (annotation.equals(ClassNames.MODULE)
        ) {
      // Skip modules that require a module instance. Otherwise Dagger validation will (correctly)
      // fail on the wrapper saying a public module can't include a private one, which makes the
      // error more confusing for users since they probably aren't aware of the wrapper. When
      // skipped, if the root is in a different package, the error will instead just be on the
      // generated Hilt component.
      if (Processors.requiresModuleInstance(element.getType().getTypeElement())) {
        return Optional.empty();
      }
    }
    return Optional.of(
        new AutoValue_PkgPrivateMetadata(
            element.getType().getTypeElement(), installIn, annotation));
  }

  private static boolean isEffectivePublic(XTypeElement element) {
    boolean isPublic = true;
    XTypeElement currentElement = element;
    while (currentElement != null) {
      isPublic = isPublic && currentElement.isPublic() && !currentElement.isInternal();
      currentElement = currentElement.getEnclosingTypeElement();
    }
    return isPublic;
  }
}

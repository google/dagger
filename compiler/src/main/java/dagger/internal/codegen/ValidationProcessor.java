/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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

import dagger.Module;
import dagger.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import static dagger.internal.codegen.Util.elementToString;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.ABSTRACT;

/**
 * Checks for errors that are not directly related to modules and
 *  {@code @Inject} annotated elements.
 *
 *  <p> Warnings for invalid use of qualifier annotations can be suppressed
 *  with @SuppressWarnings("qualifiers")
 *
 *  <p> Warnings for invalid use of scoping annotations can be suppressed
 *  with @SuppressWarnings("scoping")
 */
@SupportedAnnotationTypes({ "*" })
public final class ValidationProcessor extends AbstractProcessor {

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override public boolean process(Set<? extends TypeElement> types, RoundEnvironment env) {
    List<Element> allElements = new ArrayList<Element>();
    Map<Element, Element> parametersToTheirMethods = new LinkedHashMap<Element, Element>();
    getAllElements(env, allElements, parametersToTheirMethods);
    for (Element element : allElements) {
        validateProvides(element);
        validateScoping(element);
        validateQualifiers(element, parametersToTheirMethods);
    }
    return false;
  }

  private void validateProvides(Element element) {
    if (element.getAnnotation(Provides.class) != null
        && element.getEnclosingElement().getAnnotation(Module.class) == null) {
      error("@Provides methods must be declared in modules: " + elementToString(element), element);
    }
  }

  private void validateQualifiers(Element element, Map<Element, Element> parametersToTheirMethods) {
    boolean suppressWarnings =
        element.getAnnotation(SuppressWarnings.class) != null && Arrays.asList(
            element.getAnnotation(SuppressWarnings.class).value()).contains("qualifiers");
    int numberOfQualifiersOnElement = 0;
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (annotation.getAnnotationType().asElement().getAnnotation(Qualifier.class) == null) {
       continue;
      }
      switch (element.getKind()) {
        case FIELD:
          numberOfQualifiersOnElement++;
          if (element.getAnnotation(Inject.class) == null && !suppressWarnings) {
            warning("Dagger will ignore qualifier annotations on fields that are not "
                + "annotated with @Inject: " + elementToString(element), element);
          }
          break;
        case METHOD:
          numberOfQualifiersOnElement++;
          if (!isProvidesMethod(element) && !suppressWarnings) {
            warning("Dagger will ignore qualifier annotations on methods that are not "
                + "@Provides methods: " + elementToString(element), element);
          }
          break;
        case PARAMETER:
          numberOfQualifiersOnElement++;
          if (!isInjectableConstructorParameter(element, parametersToTheirMethods)
              && !isProvidesMethodParameter(element, parametersToTheirMethods)
              && !suppressWarnings) {
            warning("Dagger will ignore qualifier annotations on parameters that are not "
                + "@Inject constructor parameters or @Provides method parameters: "
                + elementToString(element), element);
          }
          break;
        default:
          error("Qualifier annotations are only allowed on fields, methods, and parameters: "
              + elementToString(element), element);
      }
    }
    if (numberOfQualifiersOnElement > 1) {
      error("Only one qualifier annotation is allowed per element: " + elementToString(element),
          element);
    }
  }

  private void validateScoping(Element element) {
    boolean suppressWarnings =
        element.getAnnotation(SuppressWarnings.class) != null && Arrays.asList(
            element.getAnnotation(SuppressWarnings.class).value()).contains("scoping");
    int numberOfScopingAnnotationsOnElement = 0;
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (annotation.getAnnotationType().asElement().getAnnotation(Scope.class) == null) {
        continue;
      }
      switch (element.getKind()) {
        case METHOD:
          numberOfScopingAnnotationsOnElement++;
          if (!isProvidesMethod(element) && !suppressWarnings) {
            warning("Dagger will ignore scoping annotations on methods that are not "
                + "@Provides methods: " + elementToString(element), element);
          }
          break;
        case CLASS:
          if (!element.getModifiers().contains(ABSTRACT)) {
            numberOfScopingAnnotationsOnElement++;
            break;
          }
        // fall through if abstract
        default:
          error("Scoping annotations are only allowed on concrete types and @Provides methods: "
              + elementToString(element), element);
      }
    }
    if (numberOfScopingAnnotationsOnElement > 1) {
      error("Only one scoping annotation is allowed per element: " + elementToString(element),
          element);
    }
  }

  private void getAllElements(
      RoundEnvironment env, List<Element> result, Map<Element, Element> parametersToTheirMethods) {
    for (Element element : env.getRootElements()) {
      addAllEnclosed(element, result, parametersToTheirMethods);
    }
  }

  private void addAllEnclosed(
      Element element, List<Element> result, Map<Element, Element> parametersToTheirMethods) {
    result.add(element);
    for (Element enclosed : element.getEnclosedElements()) {
      addAllEnclosed(enclosed, result, parametersToTheirMethods);
      if (enclosed.getKind() == METHOD || enclosed.getKind() == CONSTRUCTOR) {
        for (Element parameter : ((ExecutableElement) enclosed).getParameters()) {
          result.add(parameter);
          parametersToTheirMethods.put(parameter, enclosed);
        }
      }
    }
  }

  private boolean isProvidesMethod(Element element) {
    return element.getKind() == METHOD && element.getAnnotation(Provides.class) != null;
  }

  /**
   * @param parameter an {@code Element} whose {@code Kind} is parameter. The {@code Kind} is not
   *        tested here.
   */
  private boolean isProvidesMethodParameter(
      Element parameter, Map<Element, Element> parametersToTheirMethods) {
    return parametersToTheirMethods.get(parameter).getAnnotation(Provides.class) != null;
  }

  /**
   * @param parameter an {@code Element} whose {@code Kind} is parameter. The {@code Kind} is not
   *        tested here.
   */
  private boolean isInjectableConstructorParameter(
      Element parameter, Map<Element, Element> parametersToTheirMethods) {
    return parametersToTheirMethods.get(parameter).getKind() == CONSTRUCTOR
        && parametersToTheirMethods.get(parameter).getAnnotation(Inject.class) != null;
  }

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
  }

  private void warning(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, element);
  }

}

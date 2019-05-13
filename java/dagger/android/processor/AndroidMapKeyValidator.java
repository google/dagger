/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.android.processor;

import static com.google.auto.common.AnnotationMirrors.getAnnotatedAnnotations;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.android.processor.AndroidMapKeys.injectedTypeFromMapKey;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Binds;
import dagger.MapKey;
import dagger.android.AndroidInjectionKey;
import dagger.android.AndroidInjector;
import dagger.multibindings.ClassKey;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

/** Validates the correctness of {@link MapKey}s used with {@code dagger.android}. */
final class AndroidMapKeyValidator implements ProcessingStep {
  private final Elements elements;
  private final Types types;
  private final Messager messager;

  AndroidMapKeyValidator(Elements elements, Types types, Messager messager) {
    this.elements = elements;
    this.types = types;
    this.messager = messager;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>builder()
        .add(AndroidInjectionKey.class)
        .add(ClassKey.class)
        .build();
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> deferredElements = ImmutableSet.builder();
    elementsByAnnotation
        .entries()
        .forEach(
            entry -> {
              try {
                validateMethod(entry.getKey(), MoreElements.asExecutable(entry.getValue()));
              } catch (TypeNotPresentException e) {
                deferredElements.add(entry.getValue());
              }
            });
    return deferredElements.build();
  }

  private void validateMethod(Class<? extends Annotation> annotation, ExecutableElement method) {
    if (!getAnnotatedAnnotations(method, Qualifier.class).isEmpty()) {
      return;
    }

    TypeMirror returnType = method.getReturnType();
    if (!types.isAssignable(types.erasure(returnType), factoryElement().asType())) {
      // if returnType is not related to AndroidInjector.Factory, ignore the method
      return;
    }

    if (!getAnnotatedAnnotations(method, Scope.class).isEmpty()) {
      SuppressWarnings suppressedWarnings = method.getAnnotation(SuppressWarnings.class);
      if (suppressedWarnings == null
          || !ImmutableSet.copyOf(suppressedWarnings.value())
              .contains("dagger.android.ScopedInjectorFactory")) {
        AnnotationMirror mapKeyAnnotation =
            getOnlyElement(getAnnotatedAnnotations(method, MapKey.class));
        TypeElement mapKeyValueElement =
            elements.getTypeElement(injectedTypeFromMapKey(mapKeyAnnotation).get());
        messager.printMessage(
            Kind.ERROR,
            String.format(
                "%s bindings should not be scoped. Scoping this method may leak instances of %s.",
                AndroidInjector.Factory.class.getCanonicalName(),
                mapKeyValueElement.getQualifiedName()),
            method);
      }
    }

    validateReturnType(method);

    // @Binds methods should only have one parameter, but we can't guarantee the order of Processors
    // in javac, so do a basic check for valid form
    if (isAnnotationPresent(method, Binds.class) && method.getParameters().size() == 1) {
      validateMapKeyMatchesBindsParameter(annotation, method);
    }
  }

  /** Report an error if the method's return type is not {@code AndroidInjector.Factory<?>}. */
  private void validateReturnType(ExecutableElement method) {
    TypeMirror returnType = method.getReturnType();
    DeclaredType requiredReturnType = injectorFactoryOf(types.getWildcardType(null, null));

    if (!types.isSameType(returnType, requiredReturnType)) {
      messager.printMessage(
          Kind.ERROR,
          String.format(
              "%s should bind %s, not %s. See https://dagger.dev/android",
              method, requiredReturnType, returnType),
          method);
    }
  }

  /**
   * A valid @Binds method could bind an {@link AndroidInjector.Factory} for one type, while giving
   * it a map key of a different type. The return type and parameter type would pass typical @Binds
   * validation, but the map lookup in {@link dagger.android.DispatchingAndroidInjector} would
   * retrieve the wrong injector factory.
   *
   * <pre>{@code
   * {@literal @Binds}
   * {@literal @IntoMap}
   * {@literal @ClassKey(GreenActivity.class)}
   * abstract AndroidInjector.Factory<?> bindBlueActivity(
   *     BlueActivityComponent.Builder builder);
   * }</pre>
   */
  private void validateMapKeyMatchesBindsParameter(
      Class<? extends Annotation> annotation, ExecutableElement method) {
    TypeMirror parameterType = getOnlyElement(method.getParameters()).asType();
    AnnotationMirror annotationMirror = getAnnotationMirror(method, annotation).get();
    TypeMirror mapKeyType =
        elements.getTypeElement(injectedTypeFromMapKey(annotationMirror).get()).asType();
    if (!types.isAssignable(parameterType, injectorFactoryOf(mapKeyType))) {
      messager.printMessage(
          Kind.ERROR,
          String.format("%s does not implement AndroidInjector<%s>", parameterType, mapKeyType),
          method,
          annotationMirror);
    }
  }

  /** Returns a {@link DeclaredType} for {@code AndroidInjector.Factory<implementationType>}. */
  private DeclaredType injectorFactoryOf(TypeMirror implementationType) {
    return types.getDeclaredType(factoryElement(), implementationType);
  }

  private TypeElement factoryElement() {
    return elements.getTypeElement(AndroidInjector.Factory.class.getCanonicalName());
  }
}

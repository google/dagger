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
import static dagger.android.processor.AndroidMapKeys.frameworkTypesByMapKey;
import static dagger.android.processor.AndroidMapKeys.injectedTypeFromMapKey;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
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

/**
 * Validates the correctness of {@link dagger.MapKey}s in {@code dagger.android} and {@code
 * dagger.android.support} methods.
 */
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
        .addAll(frameworkTypesByMapKey(elements).keySet())
        .add(AndroidInjectionKey.class)
        .add(ClassKey.class)
        .build();
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    elementsByAnnotation.forEach(
        (annotation, element) -> validateMethod(annotation, MoreElements.asExecutable(element)));
    return ImmutableSet.of();
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
        messager.printMessage(
            Kind.ERROR,
            String.format(
                "%s bindings should not be scoped. Scoping this method may leak instances of %s. ",
                AndroidInjector.Factory.class.getCanonicalName(), frameworkTypeForMapKey(method)),
            method);
      }
    }

    validateReturnTypeMatchesMapKey(method, annotation);

    // @Binds methods should only have one parameter, but we can't guarantee the order of Processors
    // in javac, so do a basic check for valid form
    if (isAnnotationPresent(method, Binds.class) && method.getParameters().size() == 1) {
      validateMapKeyMatchesBindsParameter(annotation, method);
    }
  }

  /**
   * Report an error if the method's return type doesn't match the expectations for the map key
   * annotation.
   *
   * <ul>
   *   <li>Methods annotated with {@code @ClassKey} must return {@code AndroidInjector.Factory<?>}.
   *   <li>Methods annotated with {@code @AndroidInjectionKey} must return either {@code
   *       AndroidInjector.Factory<?>} or {@code AndroidInjector.Factory<? extends AndroidType>},
   *       where {@code AndroidType} is the Android component type extended by the map key class.
   *   <li>Methods annotated with {@code @ActivityKey}, {@code FragmentKey}, etc., must return
   *       {@code AndroidInjector.Factory<? extends Activity>}, {@code AndroidInjector.Factory<?
   *       extends Fragment>}, etc.
   * </ul>
   */
  private void validateReturnTypeMatchesMapKey(
      ExecutableElement method, Class<? extends Annotation> mapKeyType) {
    TypeMirror returnType = method.getReturnType();
    DeclaredType boundedInjectorFactoryType =
        injectorFactoryOf(types.getWildcardType(frameworkTypeForMapKey(method), null));
    DeclaredType unboundedInjectorFactoryType =
        injectorFactoryOf(types.getWildcardType(null, null));

    // first check the original return type format, AndroidInjector.Factory<? extends FRAMEWORK>.
    // This should match all map keys besides ClassKey.
    boolean isValidReturnType =
        mapKeyType != ClassKey.class
            && MoreTypes.equivalence().equivalent(returnType, boundedInjectorFactoryType);

    // if the first check fails, check if the return type matches the new, all-encompassing
    // multibindings: AndroidInjector.Factory<?>. This is only supported for ClassKey (which has an
    // unbounded Class<?> return type) or AndroidInjectionKey
    isValidReturnType |=
        ((mapKeyType == ClassKey.class || mapKeyType == AndroidInjectionKey.class)
            && MoreTypes.equivalence().equivalent(returnType, unboundedInjectorFactoryType));

    if (!isValidReturnType) {
      String subject =
          mapKeyType.equals(AndroidInjectionKey.class)
              ? method.toString()
              : String.format("@%s methods", mapKeyType.getCanonicalName());

      messager.printMessage(
          Kind.ERROR,
          String.format(
              "%s should bind %s, not %s. See https://google.github.io/dagger/android",
              subject, boundedInjectorFactoryType, returnType),
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
   * {@literal @ActivityKey(GreenActivity.class)}
   * abstract AndroidInjector.Factory<? extends Activity> bindBlueActivity(
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

  /** Returns the framework type that {@code method}'s map key annotation represents. */
  private TypeMirror frameworkTypeForMapKey(ExecutableElement method) {
    AnnotationMirror mapKeyAnnotation =
        getOnlyElement(getAnnotatedAnnotations(method, MapKey.class));
    TypeMirror mapKeyType =
        elements.getTypeElement(injectedTypeFromMapKey(mapKeyAnnotation).get()).asType();
    return frameworkTypesByMapKey(elements)
        .values()
        .stream()
        .filter(frameworkType -> types.isAssignable(mapKeyType, frameworkType))
        .findFirst()
        .get();
  }

  /** Returns a {@link DeclaredType} for {@code AndroidInjector.Factory<implementationType>}. */
  private DeclaredType injectorFactoryOf(TypeMirror implementationType) {
    return types.getDeclaredType(factoryElement(), implementationType);
  }

  private TypeElement factoryElement() {
    return elements.getTypeElement(AndroidInjector.Factory.class.getCanonicalName());
  }
}

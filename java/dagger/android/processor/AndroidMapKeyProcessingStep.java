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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.android.processor.AndroidMapKeys.injectedTypeFromMapKey;
import static dagger.internal.codegen.xprocessing.XTypes.toStableString;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypes;
import javax.tools.Diagnostic.Kind;

/** Validates the correctness of {@link dagger.MapKey}s used with {@code dagger.android}. */
final class AndroidMapKeyProcessingStep extends BaseProcessingStep {
  private final XProcessingEnv processingEnv;

  AndroidMapKeyProcessingStep(XProcessingEnv processingEnv) {
    this.processingEnv = processingEnv;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.ANDROID_INJECTION_KEY, TypeNames.CLASS_KEY);
  }

  @Override
  public void process(XElement element, ImmutableSet<ClassName> annotationNames) {
    for (ClassName annotationName : annotationNames) {
      validateMethod(annotationName, XElements.asMethod(element));
    }
  }

  private void validateMethod(ClassName annotation, XMethodElement method) {
    if (!Sets.union(
            method.getAnnotationsAnnotatedWith(TypeNames.QUALIFIER),
            method.getAnnotationsAnnotatedWith(TypeNames.QUALIFIER_JAVAX))
        .isEmpty()) {
      return;
    }

    XType returnType = method.getReturnType();
    if (!factoryElement().getType().getRawType().isAssignableFrom(returnType.getRawType())) {
      // if returnType is not related to AndroidInjector.Factory, ignore the method
      return;
    }

    if (!Sets.union(
            method.getAnnotationsAnnotatedWith(TypeNames.SCOPE),
            method.getAnnotationsAnnotatedWith(TypeNames.SCOPE_JAVAX))
        .isEmpty()) {
      XAnnotation suppressedWarnings = method.getAnnotation(ClassName.get(SuppressWarnings.class));
      if (suppressedWarnings == null
          || !ImmutableSet.copyOf(suppressedWarnings.getAsStringList("value"))
              .contains("dagger.android.ScopedInjectorFactory")) {
        XAnnotation mapKeyAnnotation =
            getOnlyElement(method.getAnnotationsAnnotatedWith(TypeNames.MAP_KEY));
        XTypeElement mapKeyValueElement =
            processingEnv.requireTypeElement(injectedTypeFromMapKey(mapKeyAnnotation).get());
        processingEnv
            .getMessager()
            .printMessage(
                Kind.ERROR,
                String.format(
                    "%s bindings should not be scoped. Scoping this method may leak instances of"
                        + " %s.",
                    TypeNames.ANDROID_INJECTOR_FACTORY.canonicalName(),
                    mapKeyValueElement.getQualifiedName()),
                method);
      }
    }

    validateReturnType(method);

    // @Binds methods should only have one parameter, but we can't guarantee the order of Processors
    // in javac, so do a basic check for valid form
    if (method.hasAnnotation(TypeNames.BINDS) && method.getParameters().size() == 1) {
      validateMapKeyMatchesBindsParameter(annotation, method);
    }
  }

  /** Report an error if the method's return type is not {@code AndroidInjector.Factory<?>}. */
  private void validateReturnType(XMethodElement method) {
    XType returnType = method.getReturnType();
    XType requiredReturnType = injectorFactoryOf(processingEnv.getWildcardType(null, null));

    if (!returnType.isSameType(requiredReturnType)) {
      processingEnv
          .getMessager()
          .printMessage(
              Kind.ERROR,
              String.format(
                  "%s should bind %s, not %s. See https://dagger.dev/android",
                  method, toStableString(requiredReturnType), toStableString(returnType)),
              method);
    }
  }

  /**
   * A valid @Binds method could bind an {@code AndroidInjector.Factory} for one type, while giving
   * it a map key of a different type. The return type and parameter type would pass typical @Binds
   * validation, but the map lookup in {@code DispatchingAndroidInjector} would retrieve the wrong
   * injector factory.
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
      ClassName annotationName, XMethodElement method) {
    XType parameterType = getOnlyElement(method.getParameters()).getType();
    XAnnotation annotation = method.getAnnotation(annotationName);
    XType mapKeyType =
        processingEnv.requireTypeElement(injectedTypeFromMapKey(annotation).get()).getType();
    if (!XTypes.isAssignableTo(parameterType, injectorFactoryOf(mapKeyType))) {
      processingEnv
          .getMessager()
          .printMessage(
              Kind.ERROR,
              String.format(
                  "%s does not implement AndroidInjector<%s>",
                  toStableString(parameterType), toStableString(mapKeyType)),
              method,
              annotation);
    }
  }

  /** Returns a {@link XType} for {@code AndroidInjector.Factory<implementationType>}. */
  private XType injectorFactoryOf(XType implementationType) {
    return processingEnv.getDeclaredType(factoryElement(), implementationType);
  }

  private XTypeElement factoryElement() {
    return processingEnv.requireTypeElement(TypeNames.ANDROID_INJECTOR_FACTORY.canonicalName());
  }
}

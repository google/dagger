/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.codegen.XTypeNameKt.toJavaPoet;
import static androidx.room.compiler.processing.XTypeKt.isArray;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.base.MapKeyAccessibility.isMapKeyPubliclyAccessible;
import static dagger.internal.codegen.binding.SourceFiles.elementBasedClassName;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.xprocessing.XElements.asExecutable;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.rewrapType;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import dagger.MapKey;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.MapKeyAccessibility;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Methods for extracting {@link MapKey} annotations and key code blocks from binding elements. */
public final class MapKeys {
  public static final String LAZY_CLASS_KEY_NAME_FIELD = "lazyClassKeyName";
  public static final String KEEP_FIELD_TYPE_FIELD = "keepFieldType";

  /**
   * If {@code bindingElement} is annotated with a {@link MapKey} annotation, returns it.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one {@code MapKey}
   *     annotation
   */
  static Optional<XAnnotation> getMapKey(XElement bindingElement) {
    return getMapKeys(bindingElement).stream().collect(toOptional());
  }

  /** Returns all of the {@link MapKey} annotations that annotate {@code bindingElement}. */
  public static ImmutableSet<XAnnotation> getMapKeys(XElement bindingElement) {
    return XElements.getAnnotatedAnnotations(bindingElement, XTypeNames.MAP_KEY);
  }

  /**
   * Returns the annotation value if {@code mapKey}'s type is annotated with {@link
   * MapKey @MapKey(unwrapValue = true)}.
   *
   * @throws IllegalArgumentException if {@code mapKey}'s type is not annotated with {@link
   *     MapKey @MapKey} at all.
   */
  private static Optional<XAnnotationValue> unwrapValue(XAnnotation mapKey) {
    XTypeElement mapKeyType = mapKey.getType().getTypeElement();
    XAnnotation mapKeyAnnotation = mapKeyType.getAnnotation(XTypeNames.MAP_KEY);
    checkArgument(mapKeyAnnotation != null, "%s is not annotated with @MapKey", mapKeyType);
    return mapKeyAnnotation.getAsBoolean("unwrapValue")
        ? Optional.of(getOnlyElement(mapKey.getAnnotationValues()))
        : Optional.empty();
  }

  static XType mapKeyType(XAnnotation mapKey) {
    return unwrapValue(mapKey).isPresent()
        ? getUnwrappedMapKeyType(mapKey.getType())
        : mapKey.getType();
  }

  /**
   * Returns the map key type for an unwrapped {@link MapKey} annotation type. If the single member
   * type is primitive, returns the boxed type.
   *
   * @throws IllegalArgumentException if {@code mapKeyAnnotationType} is not an annotation type or
   *     has more than one member, or if its single member is an array
   * @throws NoSuchElementException if the annotation has no members
   */
  public static XType getUnwrappedMapKeyType(XType mapKeyAnnotationType) {
    checkArgument(
        isDeclared(mapKeyAnnotationType)
            && mapKeyAnnotationType.getTypeElement().isAnnotationClass(),
        "%s is not an annotation type",
        mapKeyAnnotationType);

    XMethodElement annotationValueMethod =
        getOnlyElement(mapKeyAnnotationType.getTypeElement().getDeclaredMethods());
    XType annotationValueType = annotationValueMethod.getReturnType();
    if (isArray(annotationValueType)) {
      throw new IllegalArgumentException(
          mapKeyAnnotationType
              + "."
              + getSimpleName(annotationValueMethod)
              + " cannot be an array");
    }
    // If the source kind is Kotlin, the annotation value type is seen as KClass rather than Class,
    // but either way we want the multibinding key to be Class so we rewrap it here.
    return isTypeOf(annotationValueType, XTypeNames.KCLASS)
        ? rewrapType(annotationValueType, XTypeNames.CLASS)
        : annotationValueType.boxed();
  }

  /**
   * Returns a code block for {@code binding}'s {@link ContributionBinding#mapKeyAnnotation() map
   * key}. If for whatever reason the map key is not accessible from within {@code requestingClass}
   * (i.e. it has a package-private {@code enum} from a different package), this will return an
   * invocation of a proxy-method giving it access.
   *
   * @throws IllegalStateException if {@code binding} is not a {@link dagger.multibindings.IntoMap
   *     map} contribution.
   */
  public static CodeBlock getMapKeyExpression(
      ContributionBinding binding, XClassName requestingClass, XProcessingEnv processingEnv) {
    XAnnotation mapKeyAnnotation = binding.mapKey().get();
    return MapKeyAccessibility.isMapKeyAccessibleFrom(
            mapKeyAnnotation, requestingClass.getPackageName())
        ? directMapKeyExpression(mapKeyAnnotation, processingEnv)
        : CodeBlock.of("$T.create()", toJavaPoet(mapKeyProxyClassName(binding)));
  }

  /**
   * Returns a code block for the map key annotation {@code mapKey}.
   *
   * <p>This method assumes the map key will be accessible in the context that the returned {@link
   * CodeBlock} is used. Use {@link #getMapKeyExpression(ContributionBinding, XClassName,
   * XProcessingEnv)} when that assumption is not guaranteed.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one {@code MapKey}
   *     annotation
   * @throws IllegalStateException if {@code bindingElement} is not annotated with a {@code MapKey}
   *     annotation
   */
  private static CodeBlock directMapKeyExpression(
      XAnnotation mapKey, XProcessingEnv processingEnv) {
    Optional<XAnnotationValue> unwrappedValue = unwrapValue(mapKey);
    if (mapKey.getQualifiedName().contentEquals("dagger.android.AndroidInjectionKey")) {
      XTypeElement unwrappedType =
          DaggerSuperficialValidation.requireTypeElement(
              processingEnv, unwrappedValue.get().asString());
      return CodeBlock.of(
          "$T.of($S)",
          toJavaPoet(XClassName.get("dagger.android.internal", "AndroidInjectionKeys")),
          unwrappedType.getClassName().reflectionName());
    }
    AnnotationExpression annotationExpression = new AnnotationExpression(mapKey);
    return unwrappedValue.isPresent()
        ? annotationExpression.getValueExpression(unwrappedValue.get())
        : annotationExpression.getAnnotationInstanceExpression();
  }

  /**
   * Returns the {@link XClassName} in which {@link #mapKeyFactoryMethod(ContributionBinding,
   * XProcessingEnv)} is generated.
   */
  public static XClassName mapKeyProxyClassName(ContributionBinding binding) {
    return elementBasedClassName(asExecutable(binding.bindingElement().get()), "MapKey");
  }

  /**
   * A {@code static create()} method to be added to {@link
   * #mapKeyProxyClassName(ContributionBinding)} when the {@code @MapKey} annotation is not publicly
   * accessible.
   */
  public static Optional<MethodSpec> mapKeyFactoryMethod(
      ContributionBinding binding, XProcessingEnv processingEnv) {
    return binding
        .mapKey()
        .filter(mapKey -> !isMapKeyPubliclyAccessible(mapKey))
        .map(
            mapKey ->
                methodBuilder("create")
                    .addModifiers(PUBLIC, STATIC)
                    .returns(mapKeyType(mapKey).getTypeName())
                    .addStatement("return $L", directMapKeyExpression(mapKey, processingEnv))
                    .build());
  }

  /**
   * Returns if this binding is a map binding and uses @LazyClassKey for contributing class keys.
   *
   * <p>@LazyClassKey won't co-exist with @ClassKey in the graph, since the same binding type cannot
   * use more than one @MapKey annotation type and Dagger validation will fail.
   */
  public static boolean useLazyClassKey(Binding binding, BindingGraph graph) {
    if (!binding.dependencies().isEmpty()) {
      ContributionBinding contributionBinding =
          graph.contributionBinding(binding.dependencies().iterator().next().key());
      return contributionBinding.mapKey().isPresent()
          && contributionBinding
              .mapKey()
              .get()
              .getTypeElement()
              .asClassName()
              .equals(XTypeNames.LAZY_CLASS_KEY);
    }
    return false;
  }

  public static CodeBlock getLazyClassMapKeyExpression(ContributionBinding contributionBinding) {
    XClassName proxyClassName =
        lazyClassKeyProxyClassName(XElements.asMethod(contributionBinding.bindingElement().get()));
    return CodeBlock.of(
        "$T.$N",
        toJavaPoet(proxyClassName),
        LAZY_CLASS_KEY_NAME_FIELD);
  }

  public static XClassName lazyClassKeyProxyClassName(XMethodElement methodElement) {
    return elementBasedClassName(methodElement, "_LazyMapKey");
  }

  private MapKeys() {}
}

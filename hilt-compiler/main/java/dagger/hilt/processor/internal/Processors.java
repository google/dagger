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

package dagger.hilt.processor.internal;

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.hilt.processor.internal.kotlin.KotlinMetadataUtils.getMetadataUtil;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static javax.lang.model.element.Modifier.PUBLIC;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XHasModifiers;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/** Static helper methods for writing a processor. */
public final class Processors {

  public static final String CONSTRUCTOR_NAME = "<init>";

  public static final String STATIC_INITIALIZER_NAME = "<clinit>";

  /** Generates the aggregating metadata class for an aggregating annotation. */
  public static void generateAggregatingClass(
      String aggregatingPackage,
      AnnotationSpec aggregatingAnnotation,
      XTypeElement originatingElement,
      Class<?> generatorClass) {
    generateAggregatingClass(
        aggregatingPackage,
        aggregatingAnnotation,
        originatingElement,
        generatorClass,
        Mode.Isolating);
  }

  /** Generates the aggregating metadata class for an aggregating annotation. */
  public static void generateAggregatingClass(
      String aggregatingPackage,
      AnnotationSpec aggregatingAnnotation,
      XTypeElement originatingElement,
      Class<?> generatorClass,
      Mode mode) {
    ClassName name =
        ClassName.get(aggregatingPackage, "_" + getFullEnclosedName(originatingElement));
    XProcessingEnv env = getProcessingEnv(originatingElement);
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(name)
            .addModifiers(PUBLIC)
            .addAnnotation(aggregatingAnnotation)
            .addJavadoc("This class should only be referenced by generated code! ")
            .addJavadoc("This class aggregates information across multiple compilations.\n");
    JavaPoetExtKt.addOriginatingElement(builder, originatingElement);
    addGeneratedAnnotation(builder, env, generatorClass);

    env.getFiler().write(JavaFile.builder(name.packageName(), builder.build()).build(), mode);
  }

  /** Returns a map from {@link XAnnotation} attribute name to {@link XAnnotationValue}s */
  public static ImmutableMap<String, XAnnotationValue> getAnnotationValues(XAnnotation annotation) {
    ImmutableMap.Builder<String, XAnnotationValue> annotationMembers = ImmutableMap.builder();
    for (XAnnotationValue value : annotation.getAnnotationValues()) {
      annotationMembers.put(value.getName(), value);
    }
    return annotationMembers.build();
  }

  /** Returns the {@link XTypeElement} for a class attribute on an annotation. */
  public static XTypeElement getAnnotationClassValue(XAnnotation annotation, String key) {
    return Iterables.getOnlyElement(getAnnotationClassValues(annotation, key));
  }

  /** Returns a list of {@link XTypeElement}s for a class attribute on an annotation. */
  public static ImmutableList<XTypeElement> getAnnotationClassValues(
      XAnnotation annotation, String key) {
    ImmutableList<XTypeElement> values = getOptionalAnnotationClassValues(annotation, key);

    ProcessorErrors.checkState(
        values.size() >= 1,
        annotation.getTypeElement(),
        "@%s, '%s' class is invalid or missing: %s",
        annotation.getName(),
        key,
        XAnnotations.toStableString(annotation));

    return values;
  }

  public static ImmutableList<XTypeElement> getOptionalAnnotationClassValues(
      XAnnotation annotation, String key) {
    return getOptionalAnnotationValues(annotation, key).stream()
        .filter(XAnnotationValue::hasTypeValue)
        .flatMap(
            annotationValue -> getTypeFromAnnotationValue(annotation, annotationValue).stream())
        .map(XType::getTypeElement)
        .collect(toImmutableList());
  }

  private static ImmutableList<XAnnotationValue> getOptionalAnnotationValues(
      XAnnotation annotation, String key) {
    return annotation.getAnnotationValues().stream()
        .filter(annotationValue -> annotationValue.getName().equals(key))
        .collect(toOptional())
        .map(
            annotationValue ->
                (annotationValue.hasListValue()
                    ? ImmutableList.copyOf(annotationValue.asAnnotationValueList())
                    : ImmutableList.of(annotationValue)))
        .orElse(ImmutableList.of());
  }

  private static ImmutableList<XType> getTypeFromAnnotationValue(
      XAnnotation annotation, XAnnotationValue annotationValue) {
    validateAnnotationValueType(annotation, annotationValue);
    return ImmutableList.of(annotationValue.asType());
  }

  private static void validateAnnotationValueType(
      XAnnotation annotation, XAnnotationValue annotationValue) {
    boolean error = false;
    try {
      if (annotationValue.asType().isError()) {
        error = true;
      }
    } catch (TypeNotPresentException unused) {
      // TODO(b/277367118): we may need a way to ignore error types in XProcessing.
      error = true;
    }
    if (error) {
      throw new ErrorTypeException(
          String.format(
              "@%s, '%s' class is invalid or missing: %s",
              XElements.getSimpleName(annotation.getTypeElement()),
              annotationValue.getName(),
              XAnnotations.toStableString(annotation)),
          annotation.getTypeElement());
    }
  }

  public static XTypeElement getTopLevelType(XElement originalElement) {
    checkNotNull(originalElement);
    for (XElement e = originalElement; e != null; e = e.getEnclosingElement()) {
      if (isTopLevel(e)) {
        return XElements.asTypeElement(e);
      }
    }
    throw new IllegalStateException(
        "Cannot find a top-level type for " + XElements.toStableString(originalElement));
  }

  public static boolean isTopLevel(XElement element) {
    return element.getEnclosingElement() == null;
  }

  /** Returns true if the given element has an annotation with the given class name. */
  public static boolean hasAnnotation(Element element, ClassName className) {
    return getAnnotationMirrorOptional(element, className).isPresent();
  }

  /** Returns true if the given element has an annotation that is an error kind. */
  public static boolean hasErrorTypeAnnotation(XElement element) {
    for (XAnnotation annotation : element.getAllAnnotations()) {
      if (annotation.getType().isError()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the annotation mirror from the given element that corresponds to the given class.
   *
   * @throws IllegalArgumentException if 2 or more annotations are found.
   * @return {@link Optional#empty()} if no annotation is found on the element.
   */
  static Optional<AnnotationMirror> getAnnotationMirrorOptional(
      Element element, ClassName className) {
    return element.getAnnotationMirrors().stream()
        .filter(mirror -> ClassName.get(mirror.getAnnotationType()).equals(className))
        .collect(toOptional());
  }

  /**
   * Returns the name of a class, including prefixing with enclosing class names. i.e. for inner
   * class Foo enclosed by Bar, returns Bar_Foo instead of just Foo
   */
  public static String getEnclosedName(ClassName name) {
    return Joiner.on('_').join(name.simpleNames());
  }

  /**
   * Returns an equivalent class name with the {@code .} (dots) used for inner classes replaced with
   * {@code _}.
   */
  public static ClassName getEnclosedClassName(ClassName className) {
    return ClassName.get(className.packageName(), getEnclosedName(className));
  }

  /**
   * Returns an equivalent class name with the {@code .} (dots) used for inner classes replaced with
   * {@code _}.
   */
  public static ClassName getEnclosedClassName(XTypeElement typeElement) {
    return getEnclosedClassName(typeElement.getClassName());
  }

  /**
   * Returns the fully qualified class name, with _ instead of . For elements that are not type
   * elements, this continues to append the simple name of elements. For example,
   * foo_bar_Outer_Inner_fooMethod.
   */
  public static String getFullEnclosedName(XElement element) {
    Preconditions.checkNotNull(element);
    String qualifiedName = "";
    while (element != null) {
      if (element.getEnclosingElement() == null) {
        qualifiedName =
            element.getClosestMemberContainer().asClassName().getCanonicalName() + qualifiedName;
      } else {
        // This check is needed to keep the name stable when compiled with jdk8 vs jdk11. jdk11
        // contains newly added "module" enclosing elements of packages, which adds an additional
        // "_" prefix to the name due to an empty module element compared with jdk8.
        if (!XElements.getSimpleName(element).isEmpty()) {
          qualifiedName = "." + XElements.getSimpleName(element) + qualifiedName;
        }
      }
      element = element.getEnclosingElement();
    }
    return qualifiedName.replace('.', '_');
  }

  /** Appends the given string to the end of the class name. */
  public static ClassName append(ClassName name, String suffix) {
    return name.peerClass(name.simpleName() + suffix);
  }

  /** Prepends the given string to the beginning of the class name. */
  public static ClassName prepend(ClassName name, String prefix) {
    return name.peerClass(prefix + name.simpleName());
  }

  /**
   * Removes the string {@code suffix} from the simple name of {@code type} and returns it.
   *
   * @throws BadInputException if the simple name of {@code type} does not end with {@code suffix}
   */
  public static ClassName removeNameSuffix(XTypeElement type, String suffix) {
    ClassName originalName = type.getClassName();
    String originalSimpleName = originalName.simpleName();
    ProcessorErrors.checkState(
        originalSimpleName.endsWith(suffix),
        type,
        "Name of type %s must end with '%s'",
        originalName,
        suffix);
    String withoutSuffix =
        originalSimpleName.substring(0, originalSimpleName.length() - suffix.length());
    return originalName.peerClass(withoutSuffix);
  }

  /** Returns {@code true} if element inherits directly or indirectly from the className. */
  public static boolean isAssignableFrom(XTypeElement element, ClassName className) {
    return isAssignableFromAnyOf(element, ImmutableSet.of(className));
  }

  /** Returns {@code true} if element inherits directly or indirectly from any of the classNames. */
  public static boolean isAssignableFromAnyOf(
      XTypeElement element, ImmutableSet<ClassName> classNames) {
    for (ClassName className : classNames) {
      if (element.getClassName().equals(className)) {
        return true;
      }
    }

    XType superClass = element.getSuperClass();
    // None type is returned if this is an interface or Object
    // Error type is returned for classes that are generated by this processor
    if (superClass != null && !superClass.isNone() && !superClass.isError()) {
      Preconditions.checkState(XTypes.isDeclared(superClass));
      if (isAssignableFromAnyOf(superClass.getTypeElement(), classNames)) {
        return true;
      }
    }

    for (XType iface : element.getSuperInterfaces()) {
      // Skip errors and keep looking. This is especially needed for classes generated by this
      // processor.
      if (iface.isError()) {
        continue;
      }
      Preconditions.checkState(
          XTypes.isDeclared(iface), "Interface type is %s", XTypes.getKindName(iface));
      if (isAssignableFromAnyOf(iface.getTypeElement(), classNames)) {
        return true;
      }
    }

    return false;
  }

  /** Returns MapKey annotated annotations found on an element. */
  public static ImmutableList<XAnnotation> getMapKeyAnnotations(XElement element) {
    // Normally, we wouldn't need to handle Kotlin metadata because map keys are typically used
    // only on methods. However, with @BindValueIntoMap, this can be used on fields so we need
    // to check annotations on the property as well, just like with qualifiers.
    return getMetadataUtil().getAnnotationsAnnotatedWith(element, ClassNames.MAP_KEY);
  }

  /** Returns true if an element is annotated with {@literal @}Inject. */
  public static boolean isAnnotatedWithInject(XElement element) {
    return element.hasAnyAnnotation(ClassNames.INJECT, ClassNames.JAKARTA_INJECT);
  }

  /** Returns Qualifier annotated annotations found on an element. */
  public static ImmutableList<XAnnotation> getQualifierAnnotations(XElement element) {
    return getMetadataUtil().getAnnotationsAnnotatedWithAnyOf(
        element, ClassNames.QUALIFIER, ClassNames.JAKARTA_QUALIFIER);
  }

  /** Returns true if an element is annotated with {@literal @}Scope. */
  public static boolean isAnnotatedWithScope(XElement element) {
    return element.hasAnyAnnotation(ClassNames.SCOPE, ClassNames.JAKARTA_SCOPE);
  }

  /** Returns Scope annotated annotations found on an element. */
  public static ImmutableList<XAnnotation> getScopeAnnotations(XElement element) {
    return ImmutableList.<XAnnotation>builder()
        .addAll(element.getAnnotationsAnnotatedWith(ClassNames.SCOPE))
        .addAll(element.getAnnotationsAnnotatedWith(ClassNames.JAKARTA_SCOPE))
        .build();
  }

  /**
   * Shortcut for converting from upper camel to lower camel case
   *
   * <p>Example: "SomeString" => "someString"
   */
  public static String upperToLowerCamel(String upperCamel) {
    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, upperCamel);
  }

  /** @return copy of the given MethodSpec as {@link MethodSpec.Builder} with method body removed */
  public static MethodSpec.Builder copyMethodSpecWithoutBody(MethodSpec methodSpec) {
    MethodSpec.Builder builder;

    if (methodSpec.isConstructor()) {
      // Constructors cannot have return types
      builder = MethodSpec.constructorBuilder();
    } else {
      builder = MethodSpec.methodBuilder(methodSpec.name)
          .returns(methodSpec.returnType);
    }

    return builder
        .addAnnotations(methodSpec.annotations)
        .addModifiers(methodSpec.modifiers)
        .addParameters(methodSpec.parameters)
        .addExceptions(methodSpec.exceptions)
        .addJavadoc(methodSpec.javadoc.toString())
        .addTypeVariables(methodSpec.typeVariables);
  }

  /**
   * Returns true if the given method is annotated with one of the annotations Dagger recognizes for
   * abstract methods (e.g. @Binds).
   */
  public static boolean hasDaggerAbstractMethodAnnotation(XExecutableElement method) {
    return method.hasAnnotation(ClassNames.BINDS)
        || method.hasAnnotation(ClassNames.BINDS_OPTIONAL_OF)
        || method.hasAnnotation(ClassNames.MULTIBINDS)
        || method.hasAnnotation(ClassNames.CONTRIBUTES_ANDROID_INJECTOR);
  }

  public static boolean requiresModuleInstance(XTypeElement module) {
    // Binding methods that lack ABSTRACT or STATIC require module instantiation.
    // Required by Dagger.  See b/31489617.
    return module.getDeclaredMethods().stream()
            .filter(Processors::isBindingMethod)
            .anyMatch(method -> !method.isAbstract() && !method.isStatic())
        && !module.isKotlinObject();
  }

  public static boolean hasVisibleEmptyConstructor(XTypeElement type) {
    List<XConstructorElement> constructors = type.getConstructors();
    return constructors.isEmpty()
        || constructors.stream()
            .filter(constructor -> constructor.getParameters().isEmpty())
            .anyMatch(
                constructor ->
                    !constructor.isPrivate()
                        );
  }

  private static boolean isBindingMethod(XExecutableElement method) {
    return method.hasAnnotation(ClassNames.PROVIDES)
        || method.hasAnnotation(ClassNames.BINDS)
        || method.hasAnnotation(ClassNames.BINDS_OPTIONAL_OF)
        || method.hasAnnotation(ClassNames.MULTIBINDS);
  }

  public static void addGeneratedAnnotation(
      TypeSpec.Builder typeSpecBuilder, XProcessingEnv env, Class<?> generatorClass) {
    addGeneratedAnnotation(typeSpecBuilder, env, generatorClass.getName());
  }

  public static void addGeneratedAnnotation(
      TypeSpec.Builder typeSpecBuilder, XProcessingEnv env, String generatorClass) {
    XTypeElement annotation = env.findGeneratedAnnotation();
    if (annotation != null) {
      typeSpecBuilder.addAnnotation(
          AnnotationSpec.builder(annotation.getClassName())
              .addMember("value", "$S", generatorClass)
              .build());
    }
  }

  public static AnnotationSpec getOriginatingElementAnnotation(XTypeElement element) {
    TypeName rawType = rawTypeName(getTopLevelType(element).getClassName());
    return AnnotationSpec.builder(ClassNames.ORIGINATING_ELEMENT)
        .addMember("topLevelClass", "$T.class", rawType)
        .build();
  }

  /**
   * Returns the {@link TypeName} for the raw type of the given type name. If the argument isn't a
   * parameterized type, it returns the argument unchanged.
   */
  public static TypeName rawTypeName(TypeName typeName) {
    return (typeName instanceof ParameterizedTypeName)
        ? ((ParameterizedTypeName) typeName).rawType
        : typeName;
  }

  public static Optional<XTypeElement> getOriginatingTestElement(XElement element) {
    XTypeElement topLevelType = getOriginatingTopLevelType(element);
    return topLevelType.hasAnnotation(ClassNames.HILT_ANDROID_TEST)
        ? Optional.of(topLevelType)
        : Optional.empty();
  }

  private static XTypeElement getOriginatingTopLevelType(XElement element) {
    XTypeElement topLevelType = getTopLevelType(element);
    if (topLevelType.hasAnnotation(ClassNames.ORIGINATING_ELEMENT)) {
      return getOriginatingTopLevelType(
          XAnnotations.getAsTypeElement(
              topLevelType.getAnnotation(ClassNames.ORIGINATING_ELEMENT), "topLevelClass"));
    }
    return topLevelType;
  }

  public static boolean hasJavaPackagePrivateVisibility(XHasModifiers element) {
    return !element.isPrivate()
        && !element.isProtected()
        && !element.isInternal()
        && !element.isPublic();
  }

  private Processors() {}
}

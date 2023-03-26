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
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.auto.common.MoreElements.asPackage;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.hilt.processor.internal.kotlin.KotlinMetadataUtils.getMetadataUtil;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XAnnotationValue;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.MoreTypes;
import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor7;
import javax.lang.model.util.SimpleTypeVisitor7;

/** Static helper methods for writing a processor. */
public final class Processors {

  public static final String CONSTRUCTOR_NAME = "<init>";

  public static final String STATIC_INITIALIZER_NAME = "<clinit>";

  private static final String JAVA_CLASS = "java.lang.Class";

  /** Generates the aggregating metadata class for an aggregating annotation. */
  public static void generateAggregatingClass(
      String aggregatingPackage,
      AnnotationSpec aggregatingAnnotation,
      XTypeElement originatingElement,
      Class<?> generatorClass) throws IOException {
    ClassName name =
        ClassName.get(aggregatingPackage, "_" + getFullEnclosedName(originatingElement));
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(name)
            .addModifiers(PUBLIC)
            .addAnnotation(aggregatingAnnotation)
            .addJavadoc("This class should only be referenced by generated code! ")
            .addJavadoc("This class aggregates information across multiple compilations.\n");;
    JavaPoetExtKt.addOriginatingElement(builder, originatingElement);

    XProcessingEnv env = getProcessingEnv(originatingElement);
    addGeneratedAnnotation(builder, env, generatorClass);
    env.getFiler()
        .write(JavaFile.builder(name.packageName(), builder.build()).build(), Mode.Isolating);
  }

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /** Returns a map from {@link AnnotationMirror} attribute name to {@link AnnotationValue}s */
  public static ImmutableMap<String, AnnotationValue> getAnnotationValues(
      Elements elements, AnnotationMirror annotation) {
    ImmutableMap.Builder<String, AnnotationValue> annotationMembers = ImmutableMap.builder();
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
        : elements.getElementValuesWithDefaults(annotation).entrySet()) {
      annotationMembers.put(e.getKey().getSimpleName().toString(), e.getValue());
    }
    return annotationMembers.build();
  }

  /** Returns a map from {@link AnnotationMirror} attribute name to {@link AnnotationValue}s */
  public static ImmutableMap<String, XAnnotationValue> getAnnotationValues(XAnnotation annotation) {
    ImmutableMap.Builder<String, XAnnotationValue> annotationMembers = ImmutableMap.builder();
    for (XAnnotationValue value : annotation.getAnnotationValues()) {
      annotationMembers.put(value.getName(), value);
    }
    return annotationMembers.build();
  }

  /**
   * Returns a multimap from attribute name to the values that are an array of annotation mirrors.
   * The returned map will not contain mappings for any attributes that are not Annotation Arrays.
   *
   * <p>e.g. if the input was the annotation mirror for
   * <pre>
   *   {@literal @}Foo({{@literal @}Bar("hello"), {@literal @}Bar("world")})
   * </pre>
   * the map returned would have "value" map to a set containing the two @Bar annotation mirrors.
   */
  public static Multimap<String, AnnotationMirror> getAnnotationAnnotationArrayValues(
      Elements elements, AnnotationMirror annotation) {
    SetMultimap<String, AnnotationMirror> annotationMembers = LinkedHashMultimap.create();
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
        : elements.getElementValuesWithDefaults(annotation).entrySet()) {
      String attribute = e.getKey().getSimpleName().toString();
      Set<AnnotationMirror> annotationMirrors = new LinkedHashSet<>();
      e.getValue().accept(new AnnotationMirrorAnnotationValueVisitor(), annotationMirrors);
      annotationMembers.putAll(attribute, annotationMirrors);
    }
    return annotationMembers;
  }

  private static final class AnnotationMirrorAnnotationValueVisitor
      extends SimpleAnnotationValueVisitor7<Void, Set<AnnotationMirror>> {

    @Override
    public Void visitArray(List<? extends AnnotationValue> vals, Set<AnnotationMirror> types) {
      for (AnnotationValue val : vals) {
        val.accept(this, types);
      }
      return null;
    }

    @Override
    public Void visitAnnotation(AnnotationMirror a, Set<AnnotationMirror> annotationMirrors) {
      annotationMirrors.add(a);
      return null;
    }
  }

  /** Returns the {@link TypeElement} for a class attribute on an annotation. */
  public static TypeElement getAnnotationClassValue(
      Elements elements, AnnotationMirror annotation, String key) {
    return Iterables.getOnlyElement(getAnnotationClassValues(elements, annotation, key));
  }

  /** Returns the {@link TypeElement} for a class attribute on an annotation. */
  public static XTypeElement getAnnotationClassValue(XAnnotation annotation, String key) {
    return Iterables.getOnlyElement(getAnnotationClassValues(annotation, key));
  }

  /** Returns a list of {@link XTypeElement}s for a class attribute on an annotation. */
  public static ImmutableList<XTypeElement> getAnnotationClassValues(
      XAnnotation annotation, String key) {
    ImmutableList<XTypeElement> values = XAnnotations.getAsTypeElementList(annotation, key);

    ProcessorErrors.checkState(
        values.size() >= 1,
        annotation.getTypeElement(),
        "@%s, '%s' class is invalid or missing: %s",
        annotation.getName(),
        key,
        XAnnotations.toStableString(annotation));

    return values;
  }

  /** Returns a multimap from attribute name to elements for class valued attributes. */
  private static Multimap<String, DeclaredType> getAnnotationClassValues(
      Elements elements, AnnotationMirror annotation) {
    Element javaClass = elements.getTypeElement(JAVA_CLASS);
    SetMultimap<String, DeclaredType> annotationMembers = LinkedHashMultimap.create();
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e :
        elements.getElementValuesWithDefaults(annotation).entrySet()) {
      Optional<DeclaredType> returnType = getOptionalDeclaredType(e.getKey().getReturnType());
      if (returnType.isPresent() && returnType.get().asElement().equals(javaClass)) {
        String attribute = e.getKey().getSimpleName().toString();
        Set<DeclaredType> declaredTypes = new LinkedHashSet<DeclaredType>();
        e.getValue().accept(new DeclaredTypeAnnotationValueVisitor(), declaredTypes);
        annotationMembers.putAll(attribute, declaredTypes);
      }
    }
    return annotationMembers;
  }

  /** Returns a list of {@link TypeElement}s for a class attribute on an annotation. */
  public static ImmutableList<TypeElement> getAnnotationClassValues(
      Elements elements, AnnotationMirror annotation, String key) {
    ImmutableList<TypeElement> values = getOptionalAnnotationClassValues(elements, annotation, key);

    ProcessorErrors.checkState(
        values.size() >= 1,
        // TODO(b/152801981): Point to the annotation value rather than the annotated element.
        annotation.getAnnotationType().asElement(),
        "@%s, '%s' class is invalid or missing: %s",
        annotation.getAnnotationType().asElement().getSimpleName(),
        key,
        annotation);

    return values;
  }

  /** Returns a list of {@link TypeElement}s for a class attribute on an annotation. */
  public static ImmutableList<TypeElement> getOptionalAnnotationClassValues(
      Elements elements, AnnotationMirror annotation, String key) {
    return ImmutableList.copyOf(
        getAnnotationClassValues(elements, annotation).get(key).stream()
            .map(MoreTypes::asTypeElement)
            .collect(Collectors.toList()));
  }

  /** Returns a list of {@link XTypeElement}s for a class attribute on an annotation. */
  public static ImmutableList<XTypeElement> getOptionalAnnotationClassValues(
      XAnnotation annotation, String key) {
    XAnnotationValue annotationValue = annotation.getAnnotationValue(key);
    ImmutableList<XType> types;
    if (annotationValue.hasTypeListValue()) {
      types = ImmutableList.copyOf(annotationValue.asTypeList());
    } else if (annotationValue.hasTypeValue()) {
      types = ImmutableList.of(annotationValue.asType());
    } else {
      types = ImmutableList.of();
    }
    return types.stream().map(XType::getTypeElement).collect(toImmutableList());
  }

  private static final class DeclaredTypeAnnotationValueVisitor
      extends SimpleAnnotationValueVisitor7<Void, Set<DeclaredType>> {

    @Override public Void visitArray(
        List<? extends AnnotationValue> vals, Set<DeclaredType> types) {
      for (AnnotationValue val : vals) {
        val.accept(this, types);
      }
      return null;
    }

    @Override public Void visitType(TypeMirror t, Set<DeclaredType> types) {
      DeclaredType declared = MoreTypes.asDeclared(t);
      checkNotNull(declared);
      types.add(declared);
      return null;
    }
  }

  /**
   * If the received mirror represents a primitive type or an array of primitive types, this returns
   * the represented primitive type. Otherwise throws an IllegalStateException.
   */
  public static PrimitiveType getPrimitiveType(TypeMirror type) {
    return type.accept(
        new SimpleTypeVisitor7<PrimitiveType, Void> () {
          @Override public PrimitiveType visitArray(ArrayType type, Void unused) {
            return getPrimitiveType(type.getComponentType());
          }

          @Override public PrimitiveType visitPrimitive(PrimitiveType type, Void unused) {
            return type;
          }

          @Override public PrimitiveType defaultAction(TypeMirror type, Void unused) {
            throw new IllegalStateException("Unhandled type: " + type);
          }
        }, null /* the Void accumulator */);
  }

  /**
   * Returns an {@link Optional#of} the declared type if the received mirror represents a declared
   * type or an array of declared types, otherwise returns {@link Optional#empty}.
   */
  public static Optional<DeclaredType> getOptionalDeclaredType(TypeMirror type) {
    return Optional.ofNullable(
        type.accept(
            new SimpleTypeVisitor7<DeclaredType, Void>(null /* defaultValue */) {
              @Override
              public DeclaredType visitArray(ArrayType type, Void unused) {
                return MoreTypes.asDeclared(type.getComponentType());
              }

              @Override
              public DeclaredType visitDeclared(DeclaredType type, Void unused) {
                return type;
              }

              @Override
              public DeclaredType visitError(ErrorType type, Void unused) {
                return type;
              }
            },
            null /* the Void accumulator */));
  }

  /** Gets the values from an annotation value representing a string array. */
  public static ImmutableList<String> getStringArrayAnnotationValue(AnnotationValue value) {
    return value.accept(new SimpleAnnotationValueVisitor7<ImmutableList<String>, Void>() {
      @Override
      public ImmutableList<String> defaultAction(Object o, Void unused) {
        throw new IllegalStateException("Expected an array, got instead: " + o);
      }

      @Override
      public ImmutableList<String> visitArray(List<? extends AnnotationValue> values,
          Void unused) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (AnnotationValue value : values) {
          builder.add(getStringAnnotationValue(value));
        }
        return builder.build();
      }
    }, /* unused accumulator */ null);
  }

  /** Gets the values from an annotation value representing a string. */
  public static String getStringAnnotationValue(AnnotationValue value) {
    return value.accept(new SimpleAnnotationValueVisitor7<String, Void>() {
      @Override
      public String defaultAction(Object o, Void unused) {
        throw new IllegalStateException("Expected a string, got instead: " + o);
      }

      @Override
      public String visitString(String value, Void unused) {
        return value;
      }
    }, /* unused accumulator */ null);
  }

  public static XTypeElement getTopLevelType(XElement originalElement) {
    checkNotNull(originalElement);
    for (XElement e = originalElement; e != null; e = e.getEnclosingElement()) {
      if (isTopLevel(e)) {
        return XElements.asTypeElement(e);
      }
    }
    throw new IllegalStateException("Cannot find a top-level type for " + originalElement);
  }

  public static boolean isTopLevel(XElement element) {
    return element.getEnclosingElement() == null;
  }

  /** Returns true if the given element is annotated with the given annotation. */
  public static boolean hasAnnotation(Element element, Class<? extends Annotation> annotation) {
    return element.getAnnotation(annotation) != null;
  }

  /** Returns true if the given element has an annotation with the given class name. */
  public static boolean hasAnnotation(Element element, ClassName className) {
    return getAnnotationMirrorOptional(element, className).isPresent();
  }

  /** Returns true if the given element has an annotation with the given class name. */
  public static boolean hasAnnotation(AnnotationMirror mirror, ClassName className) {
    return hasAnnotation(mirror.getAnnotationType().asElement(), className);
  }

  /** Returns true if the given element has an annotation that is an error kind. */
  public static boolean hasErrorTypeAnnotation(XElement element) {
    return hasErrorTypeAnnotation(toJavac(element));
  }

  /** Returns true if the given element has an annotation that is an error kind. */
  public static boolean hasErrorTypeAnnotation(Element element) {
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      if (annotationMirror.getAnnotationType().getKind() == TypeKind.ERROR) {
        return true;
      }
    }
    return false;
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

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /**
   * Returns the fully qualified class name, with _ instead of . For elements that are not type
   * elements, this continues to append the simple name of elements. For example,
   * foo_bar_Outer_Inner_fooMethod.
   */
  public static String getFullEnclosedName(Element element) {
    Preconditions.checkNotNull(element);
    String qualifiedName = "";
    while (element != null) {
      if (element.getKind().equals(ElementKind.PACKAGE)) {
        qualifiedName = asPackage(element).getQualifiedName() + qualifiedName;
      } else {
        // This check is needed to keep the name stable when compiled with jdk8 vs jdk11. jdk11
        // contains newly added "module" enclosing elements of packages, which adds an addtional "_"
        // prefix to the name due to an empty module element compared with jdk8.
        if (!element.getSimpleName().toString().isEmpty()) {
          qualifiedName = "." + element.getSimpleName() + qualifiedName;
        }
      }
      element = element.getEnclosingElement();
    }
    return qualifiedName.replace('.', '_');
  }

  /**
   * Returns the fully qualified class name, with _ instead of . For elements that are not type
   * elements, this continues to append the simple name of elements. For example,
   * foo_bar_Outer_Inner_fooMethod.
   */
  public static String getFullEnclosedName(XElement element) {
    return getFullEnclosedName(toJavac(element));
  }

  /** Appends the given string to the end of the class name. */
  public static ClassName append(ClassName name, String suffix) {
    return name.peerClass(name.simpleName() + suffix);
  }

  /** Prepends the given string to the beginning of the class name. */
  public static ClassName prepend(ClassName name, String prefix) {
    return name.peerClass(prefix + name.simpleName());
  }

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /**
   * Removes the string {@code suffix} from the simple name of {@code type} and returns it.
   *
   * @throws BadInputException if the simple name of {@code type} does not end with {@code suffix}
   */
  public static ClassName removeNameSuffix(TypeElement type, String suffix) {
    ClassName originalName = ClassName.get(type);
    String originalSimpleName = originalName.simpleName();
    ProcessorErrors.checkState(originalSimpleName.endsWith(suffix),
        type, "Name of type %s must end with '%s'", originalName, suffix);
    String withoutSuffix =
        originalSimpleName.substring(0, originalSimpleName.length() - suffix.length());
    return originalName.peerClass(withoutSuffix);
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

  /**
   * Returns the annotation mirror from the given element that corresponds to the given class.
   *
   * @throws IllegalStateException if the given element isn't annotated with that annotation.
   */
  public static AnnotationMirror getAnnotationMirror(Element element, ClassName className) {
    Optional<AnnotationMirror> annotationMirror = getAnnotationMirrorOptional(element, className);
    if (annotationMirror.isPresent()) {
      return annotationMirror.get();
    } else {
      throw new IllegalStateException(
          String.format(
              "Couldn't find annotation %s on element %s. Found annotations: %s",
              className, element.getSimpleName(), element.getAnnotationMirrors()));
    }
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

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /** Returns {@code true} if element inherits directly or indirectly from the className */
  public static boolean isAssignableFrom(TypeElement element, ClassName className) {
    return isAssignableFromAnyOf(element, ImmutableSet.of(className));
  }

  /** Returns {@code true} if element inherits directly or indirectly from the className. */
  public static boolean isAssignableFrom(XTypeElement element, ClassName className) {
    return isAssignableFromAnyOf(toJavac(element), ImmutableSet.of(className));
  }

  /** Returns {@code true} if element inherits directly or indirectly from any of the classNames. */
  public static boolean isAssignableFromAnyOf(
      TypeElement element, ImmutableSet<ClassName> classNames) {
    for (ClassName className : classNames) {
      if (ClassName.get(element).equals(className)) {
        return true;
      }
    }

    TypeMirror superClass = element.getSuperclass();
    // None type is returned if this is an interface or Object
    // Error type is returned for classes that are generated by this processor
    if ((superClass.getKind() != TypeKind.NONE) && (superClass.getKind() != TypeKind.ERROR)) {
      Preconditions.checkState(superClass.getKind() == TypeKind.DECLARED);
      if (isAssignableFromAnyOf(MoreTypes.asTypeElement(superClass), classNames)) {
        return true;
      }
    }

    for (TypeMirror iface : element.getInterfaces()) {
      // Skip errors and keep looking. This is especially needed for classes generated by this
      // processor.
      if (iface.getKind() == TypeKind.ERROR) {
        continue;
      }
      Preconditions.checkState(iface.getKind() == TypeKind.DECLARED,
          "Interface type is %s", iface.getKind());
      if (isAssignableFromAnyOf(MoreTypes.asTypeElement(iface), classNames)) {
        return true;
      }
    }

    return false;
  }

  /** Returns methods from a given TypeElement, not including constructors. */
  public static ImmutableList<ExecutableElement> getMethods(TypeElement element) {
    ImmutableList.Builder<ExecutableElement> builder = ImmutableList.builder();
    for (Element e : element.getEnclosedElements()) {
      // Only look for executable elements, not fields, etc
      if (e instanceof ExecutableElement) {
        ExecutableElement method = (ExecutableElement) e;
        if (!method.getSimpleName().contentEquals(CONSTRUCTOR_NAME)
            && !method.getSimpleName().contentEquals(STATIC_INITIALIZER_NAME)) {
          builder.add(method);
        }
      }
    }
    return builder.build();
  }

  /**
   * Returns all transitive methods from a given TypeElement, not including constructors. Also does
   * not include methods from Object or that override methods on Object.
   */
  public static ImmutableList<ExecutableElement> getAllMethods(TypeElement element) {
    ImmutableList.Builder<ExecutableElement> builder = ImmutableList.builder();
    builder.addAll(
        Iterables.filter(
            getMethods(element),
            method -> {
              return !isObjectMethod(method);
            }));
    TypeMirror superclass = element.getSuperclass();
    if (superclass.getKind() != TypeKind.NONE) {
      TypeElement superclassElement = MoreTypes.asTypeElement(superclass);
      builder.addAll(getAllMethods(superclassElement));
    }
    for (TypeMirror iface : element.getInterfaces()) {
      builder.addAll(getAllMethods(MoreTypes.asTypeElement(iface)));
    }
    return builder.build();
  }

  /** Checks that the given element is not the error type. */
  public static void checkForCompilationError(TypeElement e) {
    ProcessorErrors.checkState(e.asType().getKind() != TypeKind.ERROR, e,
        "Unable to resolve the type %s. Look for compilation errors above related to this type.",
        e);
  }

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /** Returns MapKey annotated annotations found on an element. */
  public static ImmutableList<AnnotationMirror> getMapKeyAnnotations(Element element) {
    // Normally, we wouldn't need to handle Kotlin metadata because map keys are typically used
    // only on methods. However, with @BindValueIntoMap, this can be used on fields so we need
    // to check annotations on the property as well, just like with qualifiers.
    return getMetadataUtil().getAnnotationsAnnotatedWith(element, ClassNames.MAP_KEY);
  }

  /** Returns MapKey annotated annotations found on an element. */
  public static ImmutableList<XAnnotation> getMapKeyAnnotations(XElement element) {
    return getMapKeyAnnotations(toJavac(element)).stream()
        .map(annotationMirror -> toXProcessing(annotationMirror, getProcessingEnv(element)))
        .collect(toImmutableList());
  }

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /** Returns Qualifier annotated annotations found on an element. */
  public static ImmutableList<AnnotationMirror> getQualifierAnnotations(Element element) {
    return getMetadataUtil().getAnnotationsAnnotatedWith(element, ClassNames.QUALIFIER);
  }

  /** Returns Qualifier annotated annotations found on an element. */
  public static ImmutableList<XAnnotation> getQualifierAnnotations(XElement element) {
    return getQualifierAnnotations(toJavac(element)).stream()
        .map(annotationMirror -> toXProcessing(annotationMirror, getProcessingEnv(element)))
        .collect(toImmutableList());
  }

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /** Returns Scope annotated annotations found on an element. */
  public static ImmutableList<AnnotationMirror> getScopeAnnotations(Element element) {
    return getAnnotationsAnnotatedWith(element, ClassNames.SCOPE);
  }

  /** Returns Scope annotated annotations found on an element. */
  public static ImmutableList<XAnnotation> getScopeAnnotations(XElement element) {
    return ImmutableList.copyOf(
        element.getAnnotationsAnnotatedWith(ClassNames.SCOPE));
  }

  /** Returns annotations of element that are annotated with subAnnotation */
  public static ImmutableList<AnnotationMirror> getAnnotationsAnnotatedWith(
      Element element, ClassName subAnnotation) {
    ImmutableList.Builder<AnnotationMirror> builder = ImmutableList.builder();
    element.getAnnotationMirrors().stream()
        .filter(annotation -> hasAnnotation(annotation, subAnnotation))
        .forEach(builder::add);
    return builder.build();
  }

  /** Returns true if there are any annotations of element that are annotated with subAnnotation */
  public static boolean hasAnnotationsAnnotatedWith(Element element, ClassName subAnnotation) {
    return !getAnnotationsAnnotatedWith(element, subAnnotation).isEmpty();
  }

  /**
   * Returns true iff the given {@code method} is one of the public or protected methods on {@link
   * Object}, or an overridden version thereof.
   *
   * <p>This method ignores the return type of the given method, but this is generally fine since
   * two methods which only differ by their return type will cause a compiler error. (e.g. a
   * non-static method with the signature {@code int equals(Object)})
   */
  public static boolean isObjectMethod(ExecutableElement method) {
    // First check if this method is directly defined on Object
    Element enclosingElement = method.getEnclosingElement();
    if (enclosingElement.getKind() == ElementKind.CLASS
        && TypeName.get(enclosingElement.asType()).equals(TypeName.OBJECT)) {
      return true;
    }

    if (method.getModifiers().contains(STATIC)) {
      return false;
    }
    switch (method.getSimpleName().toString()) {
      case "equals":
        return (method.getParameters().size() == 1)
            && method.getParameters().get(0).asType().toString().equals("java.lang.Object");
      case "hashCode":
      case "toString":
      case "clone":
      case "getClass":
      case "notify":
      case "notifyAll":
      case "finalize":
        return method.getParameters().isEmpty();
      case "wait":
        if (method.getParameters().isEmpty()) {
          return true;
        } else if ((method.getParameters().size() == 1)
            && (method.getParameters().get(0).asType().toString().equals("long"))) {
          return true;
        } else if ((method.getParameters().size() == 2)
            && (method.getParameters().get(0).asType().toString().equals("long"))
            && (method.getParameters().get(1).asType().toString().equals("int"))) {
          return true;
        }
        return false;
      default:
        return false;
    }
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

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  /**
   * Returns true if the given method is annotated with one of the annotations Dagger recognizes for
   * abstract methods (e.g. @Binds).
   */
  public static boolean hasDaggerAbstractMethodAnnotation(ExecutableElement method) {
    return hasAnnotation(method, ClassNames.BINDS)
        || hasAnnotation(method, ClassNames.BINDS_OPTIONAL_OF)
        || hasAnnotation(method, ClassNames.MULTIBINDS)
        || hasAnnotation(method, ClassNames.CONTRIBUTES_ANDROID_INJECTOR);
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

  public static ImmutableSet<ClassName> toClassNames(Iterable<XTypeElement> elements) {
    return ImmutableSet.copyOf(elements).stream()
        .map(XTypeElement::getClassName)
        .collect(DaggerStreams.toImmutableSet());
  }

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  public static boolean requiresModuleInstance(Elements elements, TypeElement module) {
    // Binding methods that lack ABSTRACT or STATIC require module instantiation.
    // Required by Dagger.  See b/31489617.
    return ElementFilter.methodsIn(elements.getAllMembers(module)).stream()
            .filter(Processors::isBindingMethod)
            .map(ExecutableElement::getModifiers)
            .anyMatch(modifiers -> !modifiers.contains(ABSTRACT) && !modifiers.contains(STATIC))
        // TODO(erichang): Getting a new KotlinMetadataUtil each time isn't great here, but until
        // we have some sort of dependency management it will be difficult to share the instance.
        && !getMetadataUtil().isObjectOrCompanionObjectClass(module);
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

  // TODO(kuanyingchou): Remove this method once all usages are migrated to XProcessing.
  private static boolean isBindingMethod(ExecutableElement method) {
    return hasAnnotation(method, ClassNames.PROVIDES)
        || hasAnnotation(method, ClassNames.BINDS)
        || hasAnnotation(method, ClassNames.BINDS_OPTIONAL_OF)
        || hasAnnotation(method, ClassNames.MULTIBINDS);
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
          getAnnotationClassValue(
              topLevelType.getAnnotation(ClassNames.ORIGINATING_ELEMENT), "topLevelClass"));
    }

    return topLevelType;
  }

  private Processors() {}
}

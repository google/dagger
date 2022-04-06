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

import static com.google.auto.common.MoreElements.asPackage;
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.asVariable;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.GeneratedAnnotations;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.CaseFormat;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
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
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.extension.DaggerStreams;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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

  public static void generateAggregatingClass(
      String aggregatingPackage,
      AnnotationSpec aggregatingAnnotation,
      TypeElement element,
      Class<?> generatedAnnotationClass,
      ProcessingEnvironment env) throws IOException {
    ClassName name = ClassName.get(aggregatingPackage, "_" + getFullEnclosedName(element));
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(name)
            .addModifiers(PUBLIC)
            .addOriginatingElement(element)
            .addAnnotation(aggregatingAnnotation)
            .addJavadoc("This class should only be referenced by generated code! ")
            .addJavadoc("This class aggregates information across multiple compilations.\n");;

    addGeneratedAnnotation(builder, env, generatedAnnotationClass);

    JavaFile.builder(name.packageName(), builder.build()).build().writeTo(env.getFiler());
  }

  /** Returns a map from {@link AnnotationMirror} attribute name to {@link AnnotationValue}s */
  public static ImmutableMap<String, AnnotationValue> getAnnotationValues(Elements elements,
      AnnotationMirror annotation) {
    ImmutableMap.Builder<String, AnnotationValue> annotationMembers = ImmutableMap.builder();
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
        : elements.getElementValuesWithDefaults(annotation).entrySet()) {
      annotationMembers.put(e.getKey().getSimpleName().toString(), e.getValue());
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

  /** Returns an optional {@link TypeElement} for a class attribute on an annotation. */
  public static Optional<TypeElement> getOptionalAnnotationClassValue(
      Elements elements, AnnotationMirror annotation, String key) {
    return getAnnotationClassValues(elements, annotation).get(key).stream()
        .map(MoreTypes::asTypeElement)
        .collect(toOptional());
  }

  /** Returns a list of {@link TypeElement}s for a class attribute on an annotation. */
  public static ImmutableList<TypeElement> getOptionalAnnotationClassValues(
      Elements elements, AnnotationMirror annotation, String key) {
    return ImmutableList.copyOf(
        getAnnotationClassValues(elements, annotation).get(key).stream()
            .map(MoreTypes::asTypeElement)
            .collect(Collectors.toList()));
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

  /**
   * Returns the declared type if the received mirror represents a declared type or an array of
   * declared types, otherwise throws an {@link IllegalStateException}.
   */
  public static DeclaredType getDeclaredType(TypeMirror type) {
    return getOptionalDeclaredType(type)
        .orElseThrow(() -> new IllegalStateException("Not a declared type: " + type));
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

  /** Gets the values from an annotation value representing an int. */
  public static Boolean getBooleanAnnotationValue(AnnotationValue value) {
    return value.accept(
        new SimpleAnnotationValueVisitor7<Boolean, Void>() {
          @Override
          public Boolean defaultAction(Object o, Void unused) {
            throw new IllegalStateException("Expected a boolean, got instead: " + o);
          }

          @Override
          public Boolean visitBoolean(boolean value, Void unused) {
            return value;
          }
        }, /* unused accumulator */
        null);
  }

  /** Gets the values from an annotation value representing an int. */
  public static Integer getIntAnnotationValue(AnnotationValue value) {
    return value.accept(new SimpleAnnotationValueVisitor7<Integer, Void>() {
      @Override
      public Integer defaultAction(Object o, Void unused) {
        throw new IllegalStateException("Expected an int, got instead: " + o);
      }

      @Override
      public Integer visitInt(int value, Void unused) {
        return value;
      }
    }, /* unused accumulator */ null);
  }

  /** Gets the values from an annotation value representing a long. */
  public static Long getLongAnnotationValue(AnnotationValue value) {
    return value.accept(
        new SimpleAnnotationValueVisitor7<Long, Void>() {
          @Override
          public Long defaultAction(Object o, Void unused) {
            throw new IllegalStateException("Expected an int, got instead: " + o);
          }

          @Override
          public Long visitLong(long value, Void unused) {
            return value;
          }
        },
        null /* unused accumulator */);
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

  /** Gets the values from an annotation value representing a DeclaredType. */
  public static DeclaredType getDeclaredTypeAnnotationValue(AnnotationValue value) {
    return value.accept(
        new SimpleAnnotationValueVisitor7<DeclaredType, Void>() {
          @Override
          public DeclaredType defaultAction(Object o, Void unused) {
            throw new IllegalStateException("Expected a TypeMirror, got instead: " + o);
          }

          @Override
          public DeclaredType visitType(TypeMirror typeMirror, Void unused) {
            return MoreTypes.asDeclared(typeMirror);
          }
        }, /* unused accumulator */
        null);
  }

  private static final SimpleAnnotationValueVisitor7<ImmutableSet<VariableElement>, Void>
      ENUM_ANNOTATION_VALUE_VISITOR =
          new SimpleAnnotationValueVisitor7<ImmutableSet<VariableElement>, Void>() {
            @Override
            public ImmutableSet<VariableElement> defaultAction(Object o, Void unused) {
              throw new IllegalStateException(
                  "Expected an Enum or an Enum array, got instead: " + o);
            }

            @Override
            public ImmutableSet<VariableElement> visitArray(
                List<? extends AnnotationValue> values, Void unused) {
              ImmutableSet.Builder<VariableElement> builder = ImmutableSet.builder();
              for (AnnotationValue value : values) {
                builder.addAll(value.accept(this, null));
              }
              return builder.build();
            }

            @Override
            public ImmutableSet<VariableElement> visitEnumConstant(
                VariableElement value, Void unused) {
              return ImmutableSet.of(value);
            }
          };

  /** Gets the values from an annotation value representing a Enum array. */
  public static ImmutableSet<VariableElement> getEnumArrayAnnotationValue(AnnotationValue value) {
    return value.accept(ENUM_ANNOTATION_VALUE_VISITOR, /* unused accumulator */ null);
  }

  /** Converts an annotation value map to be keyed by the attribute name. */
  public static ImmutableMap<String, AnnotationValue> convertToAttributeNameMap(
      Map<? extends ExecutableElement, ? extends AnnotationValue> annotationValues) {
    ImmutableMap.Builder<String, AnnotationValue> builder = ImmutableMap.builder();
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
        : annotationValues.entrySet()) {
      String attribute = e.getKey().getSimpleName().toString();
      builder.put(attribute, e.getValue());
    }
    return builder.build();
  }

  /** Returns the given elements containing package element. */
  public static PackageElement getPackageElement(Element originalElement) {
    checkNotNull(originalElement);
    for (Element e = originalElement; e != null; e = e.getEnclosingElement()) {
      if (e instanceof PackageElement) {
        return (PackageElement) e;
      }
    }
    throw new IllegalStateException("Cannot find a package for " + originalElement);
  }

  public static TypeElement getTopLevelType(Element originalElement) {
    checkNotNull(originalElement);
    for (Element e = originalElement; e != null; e = e.getEnclosingElement()) {
      if (isTopLevel(e)) {
        return MoreElements.asType(e);
      }
    }
    throw new IllegalStateException("Cannot find a top-level type for " + originalElement);
  }

  /** Returns true if the given element is a top-level element. */
  public static boolean isTopLevel(Element element) {
    return element.getEnclosingElement().getKind() == ElementKind.PACKAGE;
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

  /** Returns true if the given element is annotated with the given annotation. */
  public static boolean hasAnnotation(
      AnnotationMirror mirror, Class<? extends Annotation> annotation) {
    return hasAnnotation(mirror.getAnnotationType().asElement(), annotation);
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
   * Returns all elements in the round that are annotated with at least 1 of the given
   * annotations.
   */
  @SafeVarargs
  public static ImmutableSet<Element> getElementsAnnotatedWith(RoundEnvironment roundEnv,
      Class<? extends Annotation>... annotations) {
    ImmutableSet.Builder<Element> builder = ImmutableSet.builder();
    for (Class<? extends Annotation> annotation : annotations){
      builder.addAll(roundEnv.getElementsAnnotatedWith(annotation));
    }

    return builder.build();
  }

  /**
   * Returns the name of a class, including prefixing with enclosing class names. i.e. for inner
   * class Foo enclosed by Bar, returns Bar_Foo instead of just Foo
   */
  public static String getEnclosedName(ClassName name) {
    return Joiner.on('_').join(name.simpleNames());
  }

  /** Returns the name of a class. See {@link #getEnclosedName(ClassName)}. */
  public static String getEnclosedName(TypeElement element) {
    return getEnclosedName(ClassName.get(element));
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
  public static ClassName getEnclosedClassName(TypeElement typeElement) {
    return getEnclosedClassName(ClassName.get(typeElement));
  }

  /** Returns the fully qualified class name, with _ instead of . */
  public static String getFullyQualifiedEnclosedClassName(ClassName className) {
    return className.packageName().replace('.', '_') + getEnclosedName(className);
  }

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
  public static ClassName removeNameSuffix(TypeElement type, String suffix) {
    ClassName originalName = ClassName.get(type);
    String originalSimpleName = originalName.simpleName();
    ProcessorErrors.checkState(originalSimpleName.endsWith(suffix),
        type, "Name of type %s must end with '%s'", originalName, suffix);
    String withoutSuffix =
        originalSimpleName.substring(0, originalSimpleName.length() - suffix.length());
    return originalName.peerClass(withoutSuffix);
  }

  /** @see #getAnnotationMirror(Element, ClassName) */
  public static AnnotationMirror getAnnotationMirror(
      Element element, Class<? extends Annotation> annotationClass) {
    return getAnnotationMirror(element, ClassName.get(annotationClass));
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
   * @throws {@link IllegalArgumentException} if 2 or more annotations are found.
   * @return {@link Optional#empty()} if no annotation is found on the element.
   */
  static Optional<AnnotationMirror> getAnnotationMirrorOptional(
      Element element, ClassName className) {
    return element.getAnnotationMirrors().stream()
        .filter(mirror -> ClassName.get(mirror.getAnnotationType()).equals(className))
        .collect(toOptional());
  }

  /** @return true if element inherits directly or indirectly from the className */
  public static boolean isAssignableFrom(TypeElement element, ClassName className) {
    return isAssignableFromAnyOf(element, ImmutableSet.of(className));
  }

  /** @return true if element inherits directly or indirectly from any of the classNames */
  public static boolean isAssignableFromAnyOf(TypeElement element,
      ImmutableSet<ClassName> classNames) {
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

  public static ImmutableList<ExecutableElement> getConstructors(TypeElement element) {
    ImmutableList.Builder<ExecutableElement> builder = ImmutableList.builder();
    for (Element enclosed : element.getEnclosedElements()) {
      // Only look for executable elements, not fields, etc
      if (enclosed instanceof ExecutableElement) {
        ExecutableElement method = (ExecutableElement) enclosed;
        if (method.getSimpleName().contentEquals(CONSTRUCTOR_NAME)) {
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

  private static void addInterfaceMethods(
      TypeElement type, ImmutableList.Builder<ExecutableElement> interfaceMethods) {
    for (TypeMirror interfaceMirror : type.getInterfaces()) {
      TypeElement interfaceElement = MoreTypes.asTypeElement(interfaceMirror);
      interfaceMethods.addAll(getMethods(interfaceElement));
      addInterfaceMethods(interfaceElement, interfaceMethods);
    }
  }

  /**
   * Finds methods of interfaces implemented by {@code type}. This method also checks the
   * superinterfaces of those interfaces. This method does not check the interfaces of any
   * superclass of {@code type}.
   */
  public static ImmutableList<ExecutableElement> methodsOnInterfaces(TypeElement type) {
    ImmutableList.Builder<ExecutableElement> interfaceMethods = new ImmutableList.Builder<>();
    addInterfaceMethods(type, interfaceMethods);
    return interfaceMethods.build();
  }

  /** Returns MapKey annotated annotations found on an element. */
  public static ImmutableList<AnnotationMirror> getMapKeyAnnotations(Element element) {
    return getAnnotationsAnnotatedWith(element, ClassName.get("dagger", "MapKey"));
  }

  /** Returns Qualifier annotated annotations found on an element. */
  public static ImmutableList<AnnotationMirror> getQualifierAnnotations(Element element) {
    // TODO(bcorso): Consolidate this logic with InjectionAnnotations in Dagger
    ImmutableSet<? extends AnnotationMirror> qualifiers =
        AnnotationMirrors.getAnnotatedAnnotations(element, ClassNames.QUALIFIER.canonicalName());
    KotlinMetadataUtil metadataUtil = KotlinMetadataUtils.getMetadataUtil();
    if (element.getKind() == ElementKind.FIELD
        // static fields are generally not supported, no need to get qualifier from kotlin metadata
        && !element.getModifiers().contains(STATIC)
        && metadataUtil.hasMetadata(element)) {
      VariableElement fieldElement = asVariable(element);
      return Stream.concat(
              qualifiers.stream(),
              metadataUtil.isMissingSyntheticPropertyForAnnotations(fieldElement)
                  ? Stream.empty()
                  : metadataUtil
                      .getSyntheticPropertyAnnotations(fieldElement, ClassNames.QUALIFIER)
                      .stream())
          .map(AnnotationMirrors.equivalence()::wrap)
          .distinct()
          .map(Wrapper::get)
          .collect(DaggerStreams.toImmutableList());
    } else {
      return ImmutableList.copyOf(qualifiers);
    }
  }

  /** Returns Scope annotated annotations found on an element. */
  public static ImmutableList<AnnotationMirror> getScopeAnnotations(Element element) {
    return getAnnotationsAnnotatedWith(element, ClassNames.SCOPE);
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

    if (method.getModifiers().contains(Modifier.STATIC)) {
      return false;
    }
    switch (method.getSimpleName().toString()) {
      case "equals":
        return (method.getParameters().size() == 1)
            && (method.getParameters().get(0).asType().toString().equals("java.lang.Object"));
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

  public static ParameterSpec parameterSpecFromVariableElement(VariableElement element) {
    return ParameterSpec.builder(
        ClassName.get(element.asType()),
        upperToLowerCamel(element.getSimpleName().toString()))
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

  /** @return A method spec for an empty constructor (useful for abstract Dagger modules). */
  public static MethodSpec privateEmptyConstructor() {
    return MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build();
  }

  /**
   * Returns true if the given method is annotated with one of the annotations Dagger recognizes
   * for abstract methods (e.g. @Binds).
   */
  public static boolean hasDaggerAbstractMethodAnnotation(ExecutableElement method) {
    return hasAnnotation(method, ClassNames.BINDS)
        || hasAnnotation(method, ClassNames.BINDS_OPTIONAL_OF)
        || hasAnnotation(method, ClassNames.MULTIBINDS)
        || hasAnnotation(method, ClassNames.CONTRIBUTES_ANDROID_INJECTOR);
  }

  public static ImmutableSet<TypeElement> toTypeElements(Elements elements, String[] classes) {
    return FluentIterable.from(classes).transform(elements::getTypeElement).toSet();
  }

  public static ImmutableSet<ClassName> toClassNames(Iterable<TypeElement> elements) {
    return FluentIterable.from(elements).transform(ClassName::get).toSet();
  }

  public static boolean requiresModuleInstance(Elements elements, TypeElement module) {
    // Binding methods that lack ABSTRACT or STATIC require module instantiation.
    // Required by Dagger.  See b/31489617.
    return ElementFilter.methodsIn(elements.getAllMembers(module)).stream()
        .filter(Processors::isBindingMethod)
        .map(ExecutableElement::getModifiers)
        .anyMatch(modifiers -> !modifiers.contains(ABSTRACT) && !modifiers.contains(STATIC))
        // TODO(erichang): Getting a new KotlinMetadataUtil each time isn't great here, but until
        // we have some sort of dependency management it will be difficult to share the instance.
        && !KotlinMetadataUtils.getMetadataUtil().isObjectOrCompanionObjectClass(module);
  }

  public static boolean hasVisibleEmptyConstructor(TypeElement type) {
    List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements());
    return constructors.isEmpty()
        || constructors.stream()
            .filter(constructor -> constructor.getParameters().isEmpty())
            .anyMatch(
                constructor ->
                    !constructor.getModifiers().contains(Modifier.PRIVATE)
                        );
  }

  private static boolean isBindingMethod(ExecutableElement method) {
    return hasAnnotation(method, ClassNames.PROVIDES)
        || hasAnnotation(method, ClassNames.BINDS)
        || hasAnnotation(method, ClassNames.BINDS_OPTIONAL_OF)
        || hasAnnotation(method, ClassNames.MULTIBINDS);
  }

  public static void addGeneratedAnnotation(
      TypeSpec.Builder typeSpecBuilder, ProcessingEnvironment env, Class<?> generatorClass) {
    addGeneratedAnnotation(typeSpecBuilder, env, generatorClass.getName());
  }

  public static void addGeneratedAnnotation(
      TypeSpec.Builder typeSpecBuilder, ProcessingEnvironment env, String generatorClass) {
    GeneratedAnnotations.generatedAnnotation(env.getElementUtils(), env.getSourceVersion())
        .ifPresent(
            annotation ->
                typeSpecBuilder.addAnnotation(
                    AnnotationSpec.builder(ClassName.get(annotation))
                        .addMember("value", "$S", generatorClass)
                        .build()));
  }

  public static AnnotationSpec getOriginatingElementAnnotation(TypeElement element) {
    TypeName rawType = rawTypeName(ClassName.get(getTopLevelType(element)));
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

  public static Optional<TypeElement> getOriginatingTestElement(
      Element element, Elements elements) {
    TypeElement topLevelType = getOriginatingTopLevelType(element, elements);
    return hasAnnotation(topLevelType, ClassNames.HILT_ANDROID_TEST)
        ? Optional.of(asType(topLevelType))
        : Optional.empty();
  }

  private static TypeElement getOriginatingTopLevelType(Element element, Elements elements) {
    TypeElement topLevelType = getTopLevelType(element);
    if (hasAnnotation(topLevelType, ClassNames.ORIGINATING_ELEMENT)) {
      return getOriginatingTopLevelType(
          getAnnotationClassValue(
              elements,
              getAnnotationMirror(topLevelType, ClassNames.ORIGINATING_ELEMENT),
              "topLevelClass"),
          elements);
    }

    return topLevelType;
  }

  private Processors() {}
}

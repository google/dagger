/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.isType;
import static com.google.auto.common.MoreTypes.asDeclared;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import dagger.Reusable;
import dagger.internal.codegen.compileroption.CompilerOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractElementVisitor8;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * A fork of {@link com.google.auto.common.SuperficialValidation}.
 *
 * <p>This fork makes a couple changes from the original:
 *
 * <ul>
 *   <li>Throws {@link ValidationException} rather than returning {@code false} for invalid types.
 *   <li>Fixes a bug that incorrectly validates error types in annotations (b/213880825)
 *   <li>Exposes extra methods needed to validate various parts of an element rather than just the
 *       entire element.
 * </ul>
 */
@Reusable
public final class DaggerSuperficialValidation {

  /**
   * Returns the type element with the given class name or throws {@link ValidationException} if it
   * is not accessible in the current compilation.
   */
  public static XTypeElement requireTypeElement(XProcessingEnv processingEnv, ClassName className) {
    return requireTypeElement(processingEnv, className.canonicalName());
  }

  /**
   * Returns the type element with the given class name or throws {@link ValidationException} if it
   * is not accessible in the current compilation.
   */
  public static XTypeElement requireTypeElement(XProcessingEnv processingEnv, String className) {
    XTypeElement type = processingEnv.findTypeElement(className);
    if (type == null) {
      throw new ValidationException.KnownErrorType(className);
    }
    return type;
  }

  private final boolean isStrictValidationEnabled;

  @Inject
  DaggerSuperficialValidation(CompilerOptions compilerOptions) {
    this(compilerOptions.strictSuperficialValidation());
  }

  private DaggerSuperficialValidation(boolean isStrictValidationEnabled) {
    this.isStrictValidationEnabled = isStrictValidationEnabled;
  }

  /**
   * Validates the {@link XElement#getType()} type of the given element.
   *
   * <p>Validating the type also validates any types it references, such as any type arguments or
   * type bounds. For an {@link ExecutableType}, the parameter and return types must be fully
   * defined, as must types declared in a {@code throws} clause or in the bounds of any type
   * parameters.
   */
  public void validateTypeOf(XElement element) {
    validateTypeOf(toJavac(element));
  }

  private void validateTypeOf(Element element) {
    try {
      validateType(Ascii.toLowerCase(element.getKind().name()), element.asType());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Validates the {@link XElement#getSuperType()} type of the given element.
   *
   * <p>Validating the type also validates any types it references, such as any type arguments or
   * type bounds.
   */
  public void validateSuperTypeOf(XTypeElement element) {
    validateSuperTypeOf(toJavac(element));
  }

  private void validateSuperTypeOf(TypeElement element) {
    try {
      validateType("superclass", element.getSuperclass());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Validates the {@link XExecutableElement#getThrownTypes()} types of the given element.
   *
   * <p>Validating the type also validates any types it references, such as any type arguments or
   * type bounds.
   */
  public void validateThrownTypesOf(XExecutableElement element) {
    validateThrownTypesOf(toJavac(element));
  }

  private void validateThrownTypesOf(ExecutableElement element) {
    try {
      validateTypes("thrown type", element.getThrownTypes());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Validates the annotation types of the given element.
   *
   * <p>Note: this method does not validate annotation values. This method is useful if you care
   * about the annotation's annotations (e.g. to check for {@code Scope} or {@code Qualifier}). In
   * such cases, we just need to validate the annotation's type.
   */
  public void validateAnnotationTypesOf(XElement element) {
    validateAnnotationTypesOf(toJavac(element));
  }

  /**
   * Validates the annotation types of the given element.
   *
   * <p>Note: this method does not validate annotation values. This method is useful if you care
   * about the annotation's annotations (e.g. to check for {@code Scope} or {@code Qualifier}). In
   * such cases, we just need to validate the annotation's type.
   */
  public void validateAnnotationTypesOf(Element element) {
    element
        .getAnnotationMirrors()
        .forEach(annotation -> validateAnnotationTypeOf(element, annotation));
  }

  /**
   * Validates the type of the given annotation.
   *
   * <p>The annotation is assumed to be annotating the given element, but this is not checked. The
   * element is only in the error message if a {@link ValidatationException} is thrown.
   *
   * <p>Note: this method does not validate annotation values. This method is useful if you care
   * about the annotation's annotations (e.g. to check for {@code Scope} or {@code Qualifier}). In
   * such cases, we just need to validate the annotation's type.
   */
  // TODO(bcorso): See CL/427767370 for suggestions to make this API clearer.
  public void validateAnnotationTypeOf(XElement element, XAnnotation annotation) {
    validateAnnotationTypeOf(toJavac(element), toJavac(annotation));
  }

  /**
   * Validates the type of the given annotation.
   *
   * <p>The annotation is assumed to be annotating the given element, but this is not checked. The
   * element is only in the error message if a {@link ValidatationException} is thrown.
   *
   * <p>Note: this method does not validate annotation values. This method is useful if you care
   * about the annotation's annotations (e.g. to check for {@code Scope} or {@code Qualifier}). In
   * such cases, we just need to validate the annotation's type.
   */
  public void validateAnnotationTypeOf(Element element, AnnotationMirror annotation) {
    try {
      validateType("annotation type", annotation.getAnnotationType());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(annotation).append(element);
    }
  }

  /** Validate the annotations of the given element. */
  public void validateAnnotationsOf(XElement element) {
    validateAnnotationsOf(toJavac(element));
  }

  public void validateAnnotationsOf(Element element) {
    try {
      validateAnnotations(element.getAnnotationMirrors());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  public void validateAnnotationOf(XElement element, XAnnotation annotation) {
    validateAnnotationOf(toJavac(element), toJavac(annotation));
  }

  public void validateAnnotationOf(Element element, AnnotationMirror annotation) {
    try {
      validateAnnotation(annotation);
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Validate the type hierarchy for the given type (with the given type description) within the
   * given element.
   *
   * <p>Validation includes all superclasses, interfaces, and type parameters of those types.
   */
  public void validateTypeHierarchyOf(String typeDescription, XElement element, XType type) {
    try {
      validateTypeHierarchy(typeDescription, type);
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(toJavac(element));
    }
  }

  private void validateTypeHierarchy(String desc, XType type) {
    validateType(desc, toJavac(type));
    try {
      type.getSuperTypes().forEach(supertype -> validateTypeHierarchy("supertype", supertype));
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(desc, toJavac(type));
    }
  }

  /**
   * Returns true if all of the given elements return true from {@link #validateElement(Element)}.
   */
  public void validateElements(Iterable<? extends Element> elements) {
    for (Element element : elements) {
      validateElement(element);
    }
  }

  private final ElementVisitor<Void, Void> elementValidatingVisitor =
      new AbstractElementVisitor8<Void, Void>() {
        @Override
        public Void visitPackage(PackageElement e, Void p) {
          // don't validate enclosed elements because it will return types in the package
          validateAnnotations(e.getAnnotationMirrors());
          return null;
        }

        @Override
        public Void visitType(TypeElement e, Void p) {
          validateBaseElement(e);
          validateElements(e.getTypeParameters());
          validateTypes("interface", e.getInterfaces());
          validateType("superclass", e.getSuperclass());
          return null;
        }

        @Override
        public Void visitVariable(VariableElement e, Void p) {
          validateBaseElement(e);
          return null;
        }

        @Override
        public Void visitExecutable(ExecutableElement e, Void p) {
          AnnotationValue defaultValue = e.getDefaultValue();
          validateBaseElement(e);
          if (defaultValue != null) {
            validateAnnotationValue(defaultValue, e.getReturnType());
          }
          validateType("return type", e.getReturnType());
          validateTypes("thrown type", e.getThrownTypes());
          validateElements(e.getTypeParameters());
          validateElements(e.getParameters());
          return null;
        }

        @Override
        public Void visitTypeParameter(TypeParameterElement e, Void p) {
          validateBaseElement(e);
          validateTypes("bound type", e.getBounds());
          return null;
        }

        @Override
        public Void visitUnknown(Element e, Void p) {
          // just assume that unknown elements are OK
          return null;
        }
      };

  /**
   * Returns true if all types referenced by the given element are defined. The exact meaning of
   * this depends on the kind of element. For packages, it means that all annotations on the package
   * are fully defined. For other element kinds, it means that types referenced by the element,
   * anything it contains, and any of its annotations element are all defined.
   */
  public void validateElement(XElement element) {
    validateElement(toJavac(element));
  }

  /**
   * Returns true if all types referenced by the given element are defined. The exact meaning of
   * this depends on the kind of element. For packages, it means that all annotations on the package
   * are fully defined. For other element kinds, it means that types referenced by the element,
   * anything it contains, and any of its annotations element are all defined.
   */
  public void validateElement(Element element) {
    try {
      element.accept(elementValidatingVisitor, null);
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  private void validateBaseElement(Element e) {
    validateType(Ascii.toLowerCase(e.getKind().name()), e.asType());
    validateAnnotations(e.getAnnotationMirrors());
    validateElements(e.getEnclosedElements());
  }

  private void validateTypes(String desc, Iterable<? extends TypeMirror> types) {
    for (TypeMirror type : types) {
      validateType(desc, type);
    }
  }

  /*
   * This visitor does not test type variables specifically, but it seems that that is not actually
   * an issue.  Javac turns the whole type parameter into an error type if it can't figure out the
   * bounds.
   */
  private final TypeVisitor<Void, Void> typeValidatingVisitor =
      new SimpleTypeVisitor8<Void, Void>() {
        @Override
        protected Void defaultAction(TypeMirror t, Void p) {
          return null;
        }

        @Override
        public Void visitArray(ArrayType t, Void p) {
          validateType("array component type", t.getComponentType());
          return null;
        }

        @Override
        public Void visitDeclared(DeclaredType t, Void p) {
          if (isStrictValidationEnabled) {
            // There's a bug in TypeVisitor which will visit the visitDeclared() method rather than
            // visitError() even when it's an ERROR kind. Thus, we check the kind directly here and
            // fail validation if it's an ERROR kind (see b/213880825).
            if (t.getKind() == TypeKind.ERROR) {
              throw new ValidationException.KnownErrorType(t);
            }
          }
          validateTypes("type argument", t.getTypeArguments());
          return null;
        }

        @Override
        public Void visitError(ErrorType t, Void p) {
          throw new ValidationException.KnownErrorType(t);
        }

        @Override
        public Void visitUnknown(TypeMirror t, Void p) {
          // just make the default choice for unknown types
          return defaultAction(t, p);
        }

        @Override
        public Void visitWildcard(WildcardType t, Void p) {
          TypeMirror extendsBound = t.getExtendsBound();
          TypeMirror superBound = t.getSuperBound();
          if (extendsBound != null) {
            validateType("extends bound type", extendsBound);
          }
          if (superBound != null) {
            validateType("super bound type", superBound);
          }
          return null;
        }

        @Override
        public Void visitExecutable(ExecutableType t, Void p) {
          validateTypes("parameter type", t.getParameterTypes());
          validateType("return type", t.getReturnType());
          validateTypes("thrown type", t.getThrownTypes());
          validateTypes("type variable", t.getTypeVariables());
          return null;
        }
      };

  /**
   * Returns true if the given type is fully defined. This means that the type itself is defined, as
   * are any types it references, such as any type arguments or type bounds. For an {@link
   * ExecutableType}, the parameter and return types must be fully defined, as must types declared
   * in a {@code throws} clause or in the bounds of any type parameters.
   */
  private void validateType(String desc, TypeMirror type) {
    try {
      type.accept(typeValidatingVisitor, null);
      if (isStrictValidationEnabled) {
        // Note: We don't actually expect to get an ERROR type here as it should have been caught
        // by the visitError() or visitDeclared()  methods above. However, we check here as a last
        // resort.
        if (type.getKind() == TypeKind.ERROR) {
          // In this case, the type is not guaranteed to be a DeclaredType, so we report the
          // toString() of the type. We could report using UnknownErrorType but the type's toString
          // may actually contain useful information.
          throw new ValidationException.KnownErrorType(type.toString());
        }
      }
    } catch (RuntimeException e) {
      throw ValidationException.from(e).append(desc, type);
    }
  }

  private void validateAnnotations(Iterable<? extends AnnotationMirror> annotationMirrors) {
    for (AnnotationMirror annotationMirror : annotationMirrors) {
      validateAnnotation(annotationMirror);
    }
  }

  private void validateAnnotation(AnnotationMirror annotationMirror) {
    try {
      validateType("annotation type", annotationMirror.getAnnotationType());
      validateAnnotationValues(annotationMirror.getElementValues());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(annotationMirror);
    }
  }

  private void validateAnnotationValues(
      Map<? extends ExecutableElement, ? extends AnnotationValue> valueMap) {
    valueMap.forEach(
        (method, annotationValue) -> {
          try {
            TypeMirror expectedType = method.getReturnType();
            validateAnnotationValue(annotationValue, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(String.format("annotation method: %s %s", method.getReturnType(), method));
          }
        });
  }

  private void validateAnnotationValue(AnnotationValue annotationValue, TypeMirror expectedType) {
    annotationValue.accept(valueValidatingVisitor, expectedType);
  }

  private final AnnotationValueVisitor<Void, TypeMirror> valueValidatingVisitor =
      new SimpleAnnotationValueVisitor8<Void, TypeMirror>() {
        @Override
        protected Void defaultAction(Object o, TypeMirror expectedType) {
          try {
            validateIsTypeOf(o.getClass(), expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("DEFAULT", o, expectedType));
          }
          return null;
        }

        @Override
        public Void visitString(String str, TypeMirror expectedType) {
          try {
            if (!MoreTypes.isTypeOf(String.class, expectedType)) {
              if (str.contentEquals("<error>")) {
                // Invalid annotation value types will visit visitString() with a value of "<error>"
                // Technically, we don't know the error type in this case, but it will be referred
                // to as "<error>" in the dependency trace, so we use that.
                throw new ValidationException.KnownErrorType("<error>");
              } else {
                throw new ValidationException.UnknownErrorType();
              }
            }
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("STRING", str, expectedType));
          }
          return null;
        }

        @Override
        public Void visitUnknown(AnnotationValue av, TypeMirror expectedType) {
          // just take the default action for the unknown
          defaultAction(av, expectedType);
          return null;
        }

        @Override
        public Void visitAnnotation(AnnotationMirror a, TypeMirror expectedType) {
          try {
            validateIsEquivalentType(a.getAnnotationType(), expectedType);
            validateAnnotation(a);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("ANNOTATION", a, expectedType));
          }
          return null;
        }

        @Override
        public Void visitArray(List<? extends AnnotationValue> values, TypeMirror expectedType) {
          try {
            if (!expectedType.getKind().equals(TypeKind.ARRAY)) {
              throw new ValidationException.UnknownErrorType();
            }
            TypeMirror componentType = MoreTypes.asArray(expectedType).getComponentType();
            for (AnnotationValue value : values) {
              value.accept(this, componentType);
            }
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("ARRAY", values, expectedType));
          }
          return null;
        }

        @Override
        public Void visitEnumConstant(VariableElement enumConstant, TypeMirror expectedType) {
          try {
            validateIsEquivalentType(asDeclared(enumConstant.asType()), expectedType);
            validateElement(enumConstant);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("ENUM_CONSTANT", enumConstant, expectedType));
          }
          return null;
        }

        @Override
        public Void visitType(TypeMirror type, TypeMirror expectedType) {
          try {
            // We could check assignability here, but would require a Types instance. Since this
            // isn't really the sort of thing that shows up in a bad AST from upstream compilation
            // we ignore the expected type and just validate the type.  It might be wrong, but
            // it's valid.
            validateType("annotation value type", type);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("TYPE", type, expectedType));
          }
          return null;
        }

        @Override
        public Void visitBoolean(boolean b, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Boolean.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("BOOLEAN", b, expectedType));
          }
          return null;
        }

        @Override
        public Void visitByte(byte b, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Byte.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("BYTE", b, expectedType));
          }
          return null;
        }

        @Override
        public Void visitChar(char c, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Character.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("CHAR", c, expectedType));
          }
          return null;
        }

        @Override
        public Void visitDouble(double d, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Double.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("DOUBLE", d, expectedType));
          }
          return null;
        }

        @Override
        public Void visitFloat(float f, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Float.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("FLOAT", f, expectedType));
          }
          return null;
        }

        @Override
        public Void visitInt(int i, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Integer.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("INT", i, expectedType));
          }
          return null;
        }

        @Override
        public Void visitLong(long l, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Long.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("LONG", l, expectedType));
          }
          return null;
        }

        @Override
        public Void visitShort(short s, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Short.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("SHORT", s, expectedType));
          }
          return null;
        }

        private <T> String exceptionMessage(String valueType, T value, TypeMirror expectedType) {
          return String.format(
              "annotation value (%s): value '%s' with expected type %s",
              valueType, value, expectedType);
        }
      };

  private void validateIsTypeOf(Class<?> clazz, TypeMirror expectedType) {
    if (!MoreTypes.isTypeOf(clazz, expectedType)) {
      throw new ValidationException.UnknownErrorType();
    }
  }

  private void validateIsEquivalentType(DeclaredType type, TypeMirror expectedType) {
    if (!MoreTypes.equivalence().equivalent(type, expectedType)) {
      throw new ValidationException.KnownErrorType(type);
    }
  }

  /**
   * A runtime exception that can be used during superficial validation to collect information about
   * unexpected exceptions during validation.
   */
  public abstract static class ValidationException extends RuntimeException {
    /** A {@link ValidationException} that originated from an unexpected exception. */
    public static final class UnexpectedException extends ValidationException {
      private UnexpectedException(Throwable throwable) {
        super(throwable);
      }
    }

    /** A {@link ValidationException} that originated from a known error type. */
    public static final class KnownErrorType extends ValidationException {
      private final String errorTypeName;

      private KnownErrorType(DeclaredType errorType) {
        Element errorElement = errorType.asElement();
        this.errorTypeName =
            isType(errorElement)
                ? asType(errorElement).getQualifiedName().toString()
                // Maybe this case should be handled by UnknownErrorType?
                : errorElement.getSimpleName().toString();
      }

      private KnownErrorType(String errorTypeName) {
        this.errorTypeName = errorTypeName;
      }

      public String getErrorTypeName() {
        return errorTypeName;
      }
    }

    /** A {@link ValidationException} that originated from an unknown error type. */
    public static final class UnknownErrorType extends ValidationException {}

    private static ValidationException from(Throwable throwable) {
      // We only ever create one instance of the ValidationException.
      return throwable instanceof ValidationException
          ? ((ValidationException) throwable)
          : new UnexpectedException(throwable);
    }

    private Optional<Element> lastReportedElement = Optional.empty();
    private final List<String> messages = new ArrayList<>();

    private ValidationException() {
      super("");
    }

    private ValidationException(Throwable throwable) {
      super("", throwable);
    }

    /**
     * Appends a message for the given element and returns this instance of {@link
     * ValidationException}
     */
    private ValidationException append(Element element) {
      lastReportedElement = Optional.of(element);
      return append(getMessageForElement(element));
    }

    /**
     * Appends a message for the given type mirror and returns this instance of {@link
     * ValidationException}
     */
    private ValidationException append(String desc, TypeMirror type) {
      return append(String.format("type (%s %s): %s", type.getKind().name(), desc, type));
    }

    /**
     * Appends a message for the given annotation mirror and returns this instance of {@link
     * ValidationException}
     */
    private ValidationException append(AnnotationMirror annotationMirror) {
      // Note: Calling #toString() directly on the annotation throws NPE (b/216180336).
      return append(String.format("annotation: %s", AnnotationMirrors.toString(annotationMirror)));
    }

    /** Appends the given message and returns this instance of {@link ValidationException} */
    private ValidationException append(String message) {
      messages.add(message);
      return this;
    }

    @Override
    public String getMessage() {
      return String.format("\n  Validation trace:\n    => %s", getTrace());
    }

    public String getTrace() {
      return String.join("\n    => ", getMessageInternal().reverse());
    }

    private ImmutableList<String> getMessageInternal() {
      if (!lastReportedElement.isPresent()) {
        return ImmutableList.copyOf(messages);
      }
      // Append any enclosing element information if needed.
      List<String> newMessages = new ArrayList<>(messages);
      Element element = lastReportedElement.get();
      while (shouldAppendEnclosingElement(element)) {
        element = element.getEnclosingElement();
        newMessages.add(getMessageForElement(element));
      }
      return ImmutableList.copyOf(newMessages);
    }

    private static boolean shouldAppendEnclosingElement(Element element) {
      return element.getEnclosingElement() != null
          // We don't report enclosing elements for types because the type name should contain any
          // enclosing type and package information we need.
          && !isType(element)
          && (isExecutable(element.getEnclosingElement()) || isType(element.getEnclosingElement()));
    }

    private static boolean isExecutable(Element element) {
      return element.getKind() == ElementKind.METHOD
          || element.getKind() == ElementKind.CONSTRUCTOR;
    }

    private String getMessageForElement(Element element) {
      return String.format("element (%s): %s", element.getKind().name(), element);
    }
  }
}

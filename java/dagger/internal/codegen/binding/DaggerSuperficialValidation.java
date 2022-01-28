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

package dagger.internal.codegen.binding;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.auto.common.MoreElements.isType;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * A fork of {@link com.google.auto.common.SuperficialValidation} that exposes validation for things
 * like annotations and annotation values.
 */
// TODO(bcorso): Consider contributing this to Auto-Common's SuperficialValidation.
public final class DaggerSuperficialValidation {
  /**
   * Validates the {@link XElement#getType()} type of the given element.
   *
   * <p>Validating the type also validates any types it references, such as any type arguments or
   * type bounds. For an {@link ExecutableType}, the parameter and return types must be fully
   * defined, as must types declared in a {@code throws} clause or in the bounds of any type
   * parameters.
   */
  public static void validateTypeOf(XElement element) {
    validateTypeOf(toJavac(element));
  }

  private static void validateTypeOf(Element element) {
    try {
      validateType(element.getKind() + " type", element.asType());
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
  public static void validateSuperTypeOf(XTypeElement element) {
    validateSuperTypeOf(toJavac(element));
  }

  private static void validateSuperTypeOf(TypeElement element) {
    try {
      validateType("super type", element.getSuperclass());
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
  public static void validateThrownTypesOf(XExecutableElement element) {
    validateThrownTypesOf(toJavac(element));
  }

  private static void validateThrownTypesOf(ExecutableElement element) {
    try {
      validateTypes("thrown type", element.getThrownTypes());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /** Validate the annotations of the given element. */
  public static void validateAnnotationsOf(XElement element) {
    validateAnnotationsOf(toJavac(element));
  }

  public static void validateAnnotationsOf(Element element) {
    try {
      validateAnnotations(element.getAnnotationMirrors());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Strictly validates the given annotation belonging to the given element.
   *
   * <p>This validation is considered "strict" because it will fail if an annotation's type is not
   * valid. This fixes the bug described in b/213880825, but cannot be applied indiscriminately or
   * it would break in many cases where we don't care about an annotation.
   *
   * <p>Note: This method does not actually check that the given annotation belongs to the given
   * element. The element is only used to given better context to the error message in the case that
   * validation fails.
   */
  public static void strictValidateAnnotationsOf(XElement element) {
    strictValidateAnnotationsOf(toJavac(element));
  }

  /**
   * Strictly validates the given annotation belonging to the given element.
   *
   * <p>This validation is considered "strict" because it will fail if an annotation's type is not
   * valid. This fixes the bug described in b/213880825, but cannot be applied indiscriminately or
   * it would break in many cases where we don't care about an annotation.
   *
   * <p>Note: This method does not actually check that the given annotation belongs to the given
   * element. The element is only used to given better context to the error message in the case that
   * validation fails.
   */
  public static void strictValidateAnnotationsOf(Element element) {
    element
        .getAnnotationMirrors()
        .forEach(annotation -> strictValidateAnnotationOf(element, annotation));
  }

  /**
   * Strictly validates the given annotation belonging to the given element.
   *
   * <p>This validation is considered "strict" because it will fail if an annotation's type is not
   * valid. This fixes the bug described in b/213880825, but cannot be applied indiscriminately or
   * it would break in many cases where we don't care about an annotation.
   *
   * <p>Note: This method does not actually check that the given annotation belongs to the given
   * element. The element is only used to given better context to the error message in the case that
   * validation fails.
   */
  public static void strictValidateAnnotationOf(XElement element, XAnnotation annotation) {
    strictValidateAnnotationOf(toJavac(element), toJavac(annotation));
  }

  /**
   * Strictly validates the given annotation belonging to the given element.
   *
   * <p>This validation is considered "strict" because it will fail if an annotation's type is not
   * valid. This fixes the bug described in b/213880825, but cannot be applied indiscriminately or
   * it would break in many cases where we don't care about an annotation.
   *
   * <p>Note: This method does not actually check that the given annotation belongs to the given
   * element. The element is only used to given better context to the error message in the case that
   * validation fails.
   */
  public static void strictValidateAnnotationOf(Element element, AnnotationMirror annotation) {
    try {
      strictValidateAnnotation(annotation);
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  /**
   * Returns true if all of the given elements return true from {@link #validateElement(Element)}.
   */
  public static void validateElements(Iterable<? extends Element> elements) {
    for (Element element : elements) {
      validateElement(element);
    }
  }

  private static final ElementVisitor<Void, Void> ELEMENT_VALIDATING_VISITOR =
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
  public static void validateElement(XElement element) {
    validateElement(toJavac(element));
  }

  /**
   * Returns true if all types referenced by the given element are defined. The exact meaning of
   * this depends on the kind of element. For packages, it means that all annotations on the package
   * are fully defined. For other element kinds, it means that types referenced by the element,
   * anything it contains, and any of its annotations element are all defined.
   */
  public static void validateElement(Element element) {
    try {
      element.accept(ELEMENT_VALIDATING_VISITOR, null);
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(element);
    }
  }

  private static void validateBaseElement(Element e) {
    validateType(e.getKind() + " type", e.asType());
    validateAnnotations(e.getAnnotationMirrors());
    validateElements(e.getEnclosedElements());
  }

  private static void validateTypes(String desc, Iterable<? extends TypeMirror> types) {
    for (TypeMirror type : types) {
      validateType(desc, type);
    }
  }

  /*
   * This visitor does not test type variables specifically, but it seems that that is not actually
   * an issue.  Javac turns the whole type parameter into an error type if it can't figure out the
   * bounds.
   */
  private static final TypeVisitor<Void, Void> TYPE_VALIDATING_VISITOR =
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
          validateTypes("type argument", t.getTypeArguments());
          return null;
        }

        @Override
        public Void visitError(ErrorType t, Void p) {
          throw ValidationException.create();
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
  private static void validateType(String desc, TypeMirror type) {
    try {
      type.accept(TYPE_VALIDATING_VISITOR, null);
    } catch (RuntimeException e) {
      throw ValidationException.from(e)
          .append(String.format("(%s) %s: %s", type.getKind(), desc, type));
    }
  }

  private static void validateAnnotations(Iterable<? extends AnnotationMirror> annotationMirrors) {
    for (AnnotationMirror annotationMirror : annotationMirrors) {
      validateAnnotation(annotationMirror);
    }
  }

  /**
   * This validation is the same as {@link #validateAnnotation(AnnotationMirror)} but also validates
   * the annotation's kind directly to look for {@link TypeKind.ERROR} types.
   *
   * <p>See b/213880825.
   */
  // TODO(bcorso): Merge this into the normal validateAnnotation() method. For now, this method is
  // separated to avoid breaking existing usages that aren't setup to handle this extra validation.
  private static void strictValidateAnnotation(AnnotationMirror annotationMirror) {
    try {
      validateType("annotation type", annotationMirror.getAnnotationType());
      // There's a bug in TypeVisitor specifically when validating annotation types which will
      // visit the visitDeclared() method rather than visitError() even when it's an ERROR kind.
      // Thus, we check the kind directly here and fail validation if it's an ERROR kind.
      if (annotationMirror.getAnnotationType().getKind() == TypeKind.ERROR) {
        throw ValidationException.create();
      }
      validateAnnotationValues(annotationMirror.getElementValues());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(annotationMirror);
    }
  }

  private static void validateAnnotation(AnnotationMirror annotationMirror) {
    try {
      validateType("annotation type", annotationMirror.getAnnotationType());
      validateAnnotationValues(annotationMirror.getElementValues());
    } catch (RuntimeException exception) {
      throw ValidationException.from(exception).append(annotationMirror);
    }
  }

  private static void validateAnnotationValues(
      Map<? extends ExecutableElement, ? extends AnnotationValue> valueMap) {
    valueMap.forEach(
        (method, annotationValue) -> {
          try {
            TypeMirror expectedType = method.getReturnType();
            validateAnnotationValue(annotationValue, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append("annotation value: " + method.getSimpleName());
          }
        });
  }

  private static final AnnotationValueVisitor<Void, TypeMirror> VALUE_VALIDATING_VISITOR =
      new SimpleAnnotationValueVisitor8<Void, TypeMirror>() {
        @Override
        protected Void defaultAction(Object o, TypeMirror expectedType) {
          try {
            validateIsTypeOf(o.getClass(), expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("default", o, expectedType));
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
                .append(exceptionMessage("annotation", a, expectedType));
          }
          return null;
        }

        @Override
        public Void visitArray(List<? extends AnnotationValue> values, TypeMirror expectedType) {
          try {
            if (!expectedType.getKind().equals(TypeKind.ARRAY)) {
              throw ValidationException.create();
            }
            TypeMirror componentType = MoreTypes.asArray(expectedType).getComponentType();
            for (AnnotationValue value : values) {
              value.accept(this, componentType);
            }
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("array", values, expectedType));
          }
          return null;
        }

        @Override
        public Void visitEnumConstant(VariableElement enumConstant, TypeMirror expectedType) {
          try {
            validateIsEquivalentType(enumConstant.asType(), expectedType);
            validateElement(enumConstant);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("enumConstant", enumConstant, expectedType));
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
            validateType("type", type);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("type", type, expectedType));
          }
          return null;
        }

        @Override
        public Void visitBoolean(boolean b, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Boolean.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("boolean", b, expectedType));
          }
          return null;
        }

        @Override
        public Void visitByte(byte b, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Byte.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("byte", b, expectedType));
          }
          return null;
        }

        @Override
        public Void visitChar(char c, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Character.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("char", c, expectedType));
          }
          return null;
        }

        @Override
        public Void visitDouble(double d, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Double.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("double", d, expectedType));
          }
          return null;
        }

        @Override
        public Void visitFloat(float f, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Float.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("float", f, expectedType));
          }
          return null;
        }

        @Override
        public Void visitInt(int i, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Integer.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("int", i, expectedType));
          }
          return null;
        }

        @Override
        public Void visitLong(long l, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Long.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("long", l, expectedType));
          }
          return null;
        }

        @Override
        public Void visitShort(short s, TypeMirror expectedType) {
          try {
            validateIsTypeOf(Short.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.from(exception)
                .append(exceptionMessage("short", s, expectedType));
          }
          return null;
        }

        private <T> String exceptionMessage(String valueType, T value, TypeMirror expectedType) {
          return String.format(
              "'%s' annotation value, %s, with expected type: %s", valueType, value, expectedType);
        }
      };

  private static void validateIsTypeOf(Class<?> clazz, TypeMirror expectedType) {
    if (!MoreTypes.isTypeOf(clazz, expectedType)) {
      throw ValidationException.create();
    }
  }

  private static void validateIsEquivalentType(TypeMirror type, TypeMirror expectedType) {
    if (!MoreTypes.equivalence().equivalent(type, expectedType)) {
      throw ValidationException.create();
    }
  }

  /**
   * A runtime exception that can be used during superficial validation to collect information about
   * unexpected exceptions during validation.
   */
  public static final class ValidationException extends RuntimeException {
    private static ValidationException create() {
      return new ValidationException();
    }

    private static ValidationException from(Throwable throwable) {
      // We only ever create one instance of the ValidationException.
      return throwable instanceof ValidationException
          ? ((ValidationException) throwable)
          : new ValidationException(throwable);
    }

    private Optional<Element> lastReportedElement = Optional.empty();
    private final boolean fromUnexpectedThrowable;
    private final List<String> messages = new ArrayList<>();

    private ValidationException() {
      super("");
      fromUnexpectedThrowable = false;
    }

    private ValidationException(Throwable throwable) {
      super("", throwable);
      fromUnexpectedThrowable = true;
    }

    /** Returns {@code true} if this exception was created from an unexpected throwable. */
    public boolean fromUnexpectedThrowable() {
      return fromUnexpectedThrowable;
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
      return String.format(
          "\n  Validation trace:\n    => %s",
          String.join("\n    => ", getMessageInternal().reverse()));
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
      return String.format("%s element: %s", element.getKind(), element);
    }
  }

  private static void validateAnnotationValue(
      AnnotationValue annotationValue, TypeMirror expectedType) {
    annotationValue.accept(VALUE_VALIDATING_VISITOR, expectedType);
  }

  private DaggerSuperficialValidation() {}
}

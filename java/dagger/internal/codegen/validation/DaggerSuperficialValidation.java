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

package dagger.internal.codegen.validation;

import static com.google.common.collect.Lists.reverse;
import static java.util.stream.Collectors.joining;

import com.google.auto.common.MoreTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
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
 * A fork of {@link com.google.auto.common.SuperficialValidation} that exposes validation for
 * things like annotations and annotation values.
 */
// TODO(bcorso): Consider contributing this to Auto-Common's SuperficialValidation.
public final class DaggerSuperficialValidation {
  /**
   * Returns true if all of the given elements return true from {@link #validateElement(Element)}.
   */
  public static boolean validateElements(Iterable<? extends Element> elements) {
    return StreamSupport.stream(elements.spliterator(), false)
        .allMatch(DaggerSuperficialValidation::validateElement);
  }

  private static final ElementVisitor<Boolean, Void> ELEMENT_VALIDATING_VISITOR =
      new AbstractElementVisitor8<Boolean, Void>() {
        @Override
        public Boolean visitPackage(PackageElement e, Void p) {
          // don't validate enclosed elements because it will return types in the package
          return validateAnnotations(e.getAnnotationMirrors());
        }

        @Override
        public Boolean visitType(TypeElement e, Void p) {
          return isValidBaseElement(e)
              && validateElements(e.getTypeParameters())
              && validateTypes("interface", e.getInterfaces())
              && validateType("superclass", e.getSuperclass());
        }

        @Override
        public Boolean visitVariable(VariableElement e, Void p) {
          return isValidBaseElement(e);
        }

        @Override
        public Boolean visitExecutable(ExecutableElement e, Void p) {
          AnnotationValue defaultValue = e.getDefaultValue();
          return isValidBaseElement(e)
              && (defaultValue == null || validateAnnotationValue(defaultValue, e.getReturnType()))
              && validateType("return type", e.getReturnType())
              && validateTypes("thrown type", e.getThrownTypes())
              && validateElements(e.getTypeParameters())
              && validateElements(e.getParameters());
        }

        @Override
        public Boolean visitTypeParameter(TypeParameterElement e, Void p) {
          return isValidBaseElement(e) && validateTypes("bound type", e.getBounds());
        }

        @Override
        public Boolean visitUnknown(Element e, Void p) {
          // just assume that unknown elements are OK
          return true;
        }
      };

  /**
   * Returns true if all types referenced by the given element are defined. The exact meaning of
   * this depends on the kind of element. For packages, it means that all annotations on the package
   * are fully defined. For other element kinds, it means that types referenced by the element,
   * anything it contains, and any of its annotations element are all defined.
   */
  public static boolean validateElement(Element element) {
    try {
      return element.accept(ELEMENT_VALIDATING_VISITOR, null);
    } catch (RuntimeException exception) {
      throw ValidationException.create(
          String.format("%s element: %s", element.getKind(), element), exception);
    }
  }

  private static boolean isValidBaseElement(Element e) {
    return validateType(e.getKind() + " type", e.asType())
        && validateAnnotations(e.getAnnotationMirrors())
        && validateElements(e.getEnclosedElements());
  }

  private static boolean validateTypes(String desc, Iterable<? extends TypeMirror> types) {
    for (TypeMirror type : types) {
      if (!validateType(desc, type)) {
        return false;
      }
    }
    return true;
  }

  /*
   * This visitor does not test type variables specifically, but it seems that that is not actually
   * an issue.  Javac turns the whole type parameter into an error type if it can't figure out the
   * bounds.
   */
  private static final TypeVisitor<Boolean, Void> TYPE_VALIDATING_VISITOR =
      new SimpleTypeVisitor8<Boolean, Void>() {
        @Override
        protected Boolean defaultAction(TypeMirror t, Void p) {
          return true;
        }

        @Override
        public Boolean visitArray(ArrayType t, Void p) {
          return validateType("array component type", t.getComponentType());
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          return validateTypes("type argument", t.getTypeArguments());
        }

        @Override
        public Boolean visitError(ErrorType t, Void p) {
          return false;
        }

        @Override
        public Boolean visitUnknown(TypeMirror t, Void p) {
          // just make the default choice for unknown types
          return defaultAction(t, p);
        }

        @Override
        public Boolean visitWildcard(WildcardType t, Void p) {
          TypeMirror extendsBound = t.getExtendsBound();
          TypeMirror superBound = t.getSuperBound();
          return (extendsBound == null || validateType("extends bound type", extendsBound))
              && (superBound == null || validateType("super bound type", superBound));
        }

        @Override
        public Boolean visitExecutable(ExecutableType t, Void p) {
          return validateTypes("parameter type", t.getParameterTypes())
              && validateType("return type", t.getReturnType())
              && validateTypes("thrown type", t.getThrownTypes())
              && validateTypes("type variable", t.getTypeVariables());
        }
      };

  /**
   * Returns true if the given type is fully defined. This means that the type itself is defined, as
   * are any types it references, such as any type arguments or type bounds. For an {@link
   * ExecutableType}, the parameter and return types must be fully defined, as must types declared
   * in a {@code throws} clause or in the bounds of any type parameters.
   */
  public static boolean validateType(String desc, TypeMirror type) {
    try {
      return type.accept(TYPE_VALIDATING_VISITOR, null);
    } catch (RuntimeException e) {
      throw ValidationException.create(String.format("(%s) %s: %s", type.getKind(), desc, type), e);
    }
  }

  public static boolean validateAnnotations(
      Iterable<? extends AnnotationMirror> annotationMirrors) {
    for (AnnotationMirror annotationMirror : annotationMirrors) {
      if (!validateAnnotation(annotationMirror)) {
        return false;
      }
    }
    return true;
  }

  public static boolean validateAnnotation(AnnotationMirror annotationMirror) {
    try {
      return validateType("annotation type", annotationMirror.getAnnotationType())
          && validateAnnotationValues(annotationMirror.getElementValues());
    } catch (RuntimeException exception) {
      throw ValidationException.create("annotation: " + annotationMirror, exception);
    }
  }

  public static boolean validateAnnotationValues(
      Map<? extends ExecutableElement, ? extends AnnotationValue> valueMap) {
    return valueMap.entrySet().stream()
        .allMatch(
            valueEntry -> {
              try {
                TypeMirror expectedType = valueEntry.getKey().getReturnType();
                return validateAnnotationValue(valueEntry.getValue(), expectedType);
              } catch (RuntimeException exception) {
                throw ValidationException.create(
                    "annotation value: " + valueEntry.getKey().getSimpleName(), exception);
              }
            });
  }

  private static final AnnotationValueVisitor<Boolean, TypeMirror> VALUE_VALIDATING_VISITOR =
      new SimpleAnnotationValueVisitor8<Boolean, TypeMirror>() {
        @Override
        protected Boolean defaultAction(Object o, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(o.getClass(), expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(
                exceptionMessage("default", o, expectedType), exception);
          }
        }

        @Override
        public Boolean visitUnknown(AnnotationValue av, TypeMirror expectedType) {
          // just take the default action for the unknown
          return defaultAction(av, expectedType);
        }

        @Override
        public Boolean visitAnnotation(AnnotationMirror a, TypeMirror expectedType) {
          try {
            return MoreTypes.equivalence().equivalent(a.getAnnotationType(), expectedType)
                && validateAnnotation(a);
          } catch (RuntimeException exception) {
            throw ValidationException.create(
                exceptionMessage("annotation", a, expectedType), exception);
          }
        }

        @Override
        public Boolean visitArray(List<? extends AnnotationValue> values, TypeMirror expectedType) {
          try {
            if (!expectedType.getKind().equals(TypeKind.ARRAY)) {
              return false;
            }
            TypeMirror componentType = MoreTypes.asArray(expectedType).getComponentType();
            return values.stream().allMatch(value -> value.accept(this, componentType));
          } catch (RuntimeException exception) {
            throw ValidationException.create(
                exceptionMessage("array", values, expectedType), exception);
          }
        }

        @Override
        public Boolean visitEnumConstant(VariableElement enumConstant, TypeMirror expectedType) {
          try {
            return MoreTypes.equivalence().equivalent(enumConstant.asType(), expectedType)
                && validateElement(enumConstant);
          } catch (RuntimeException exception) {
            throw ValidationException.create(
                exceptionMessage("enumConstant", enumConstant, expectedType), exception);
          }
        }

        @Override
        public Boolean visitType(TypeMirror type, TypeMirror expectedType) {
          try {
            // We could check assignability here, but would require a Types instance. Since this
            // isn't really the sort of thing that shows up in a bad AST from upstream compilation
            // we ignore the expected type and just validate the type.  It might be wrong, but
            // it's valid.
            return validateType("type", type);
          } catch (RuntimeException exception) {
            throw ValidationException.create(
                exceptionMessage("type", type, expectedType), exception);
          }
        }

        @Override
        public Boolean visitBoolean(boolean b, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(Boolean.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(
                exceptionMessage("boolean", b, expectedType), exception);
          }
        }

        @Override
        public Boolean visitByte(byte b, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(Byte.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(exceptionMessage("byte", b, expectedType), exception);
          }
        }

        @Override
        public Boolean visitChar(char c, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(Character.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(exceptionMessage("char", c, expectedType), exception);
          }
        }

        @Override
        public Boolean visitDouble(double d, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(Double.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(
                exceptionMessage("double", d, expectedType), exception);
          }
        }

        @Override
        public Boolean visitFloat(float f, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(Float.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(exceptionMessage("float", f, expectedType), exception);
          }
        }

        @Override
        public Boolean visitInt(int i, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(Integer.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(exceptionMessage("int", i, expectedType), exception);
          }
        }

        @Override
        public Boolean visitLong(long l, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(Long.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(exceptionMessage("long", l, expectedType), exception);
          }
        }

        @Override
        public Boolean visitShort(short s, TypeMirror expectedType) {
          try {
            return MoreTypes.isTypeOf(Short.TYPE, expectedType);
          } catch (RuntimeException exception) {
            throw ValidationException.create(exceptionMessage("short", s, expectedType), exception);
          }
        }

        private <T> String exceptionMessage(String valueType, T value, TypeMirror expectedType) {
          return String.format(
              "'%s' annotation value, %s, with expected type: %s", valueType, value, expectedType);
        }
      };

  /**
   * A runtime exception that can be used during superficial validation to collect information about
   * unexpected exceptions during validation.
   */
  public static final class ValidationException extends RuntimeException {
    public static ValidationException create(String message, Throwable throwable) {
      // We only ever create one instance of the ValidationException.
      ValidationException validationException =
          throwable instanceof ValidationException
              ? ((ValidationException) throwable)
              : new ValidationException(throwable);
      validationException.messages.add(message);
      return validationException;
    }

    private final List<String> messages = new ArrayList<>();

    private ValidationException(Throwable throwable) {
      super("", throwable);
    }

    @Override
    public String getMessage() {
      return String.format(
          "\n  Validation trace:\n    => %s",
          reverse(messages).stream().collect(joining("\n    => ")));
    }
  }

  public static boolean validateAnnotationValue(
      AnnotationValue annotationValue, TypeMirror expectedType) {
    return annotationValue.accept(VALUE_VALIDATING_VISITOR, expectedType);
  }

  private DaggerSuperficialValidation() {}
}

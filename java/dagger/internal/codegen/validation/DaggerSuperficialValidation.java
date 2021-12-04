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

import com.google.auto.common.MoreTypes;
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
              && validateTypes(e.getInterfaces())
              && validateType(e.getSuperclass());
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
              && validateType(e.getReturnType())
              && validateTypes(e.getThrownTypes())
              && validateElements(e.getTypeParameters())
              && validateElements(e.getParameters());
        }

        @Override
        public Boolean visitTypeParameter(TypeParameterElement e, Void p) {
          return isValidBaseElement(e) && validateTypes(e.getBounds());
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
    return element.accept(ELEMENT_VALIDATING_VISITOR, null);
  }

  private static boolean isValidBaseElement(Element e) {
    return validateType(e.asType())
        && validateAnnotations(e.getAnnotationMirrors())
        && validateElements(e.getEnclosedElements());
  }

  public static boolean validateTypes(Iterable<? extends TypeMirror> types) {
    for (TypeMirror type : types) {
      if (!validateType(type)) {
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
          return validateType(t.getComponentType());
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          return validateTypes(t.getTypeArguments());
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
          return (extendsBound == null || validateType(extendsBound))
              && (superBound == null || validateType(superBound));
        }

        @Override
        public Boolean visitExecutable(ExecutableType t, Void p) {
          return validateTypes(t.getParameterTypes())
              && validateType(t.getReturnType())
              && validateTypes(t.getThrownTypes())
              && validateTypes(t.getTypeVariables());
        }
      };

  /**
   * Returns true if the given type is fully defined. This means that the type itself is defined, as
   * are any types it references, such as any type arguments or type bounds. For an {@link
   * ExecutableType}, the parameter and return types must be fully defined, as must types declared
   * in a {@code throws} clause or in the bounds of any type parameters.
   */
  public static boolean validateType(TypeMirror type) {
    return type.accept(TYPE_VALIDATING_VISITOR, null);
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
    return validateType(annotationMirror.getAnnotationType())
        && validateAnnotationValues(annotationMirror.getElementValues());
  }

  public static boolean validateAnnotationValues(
      Map<? extends ExecutableElement, ? extends AnnotationValue> valueMap) {
    return valueMap.entrySet().stream()
        .allMatch(
            valueEntry -> {
              TypeMirror expectedType = valueEntry.getKey().getReturnType();
              return validateAnnotationValue(valueEntry.getValue(), expectedType);
            });
  }

  private static final AnnotationValueVisitor<Boolean, TypeMirror> VALUE_VALIDATING_VISITOR =
      new SimpleAnnotationValueVisitor8<Boolean, TypeMirror>() {
        @Override
        protected Boolean defaultAction(Object o, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(o.getClass(), expectedType);
        }

        @Override
        public Boolean visitUnknown(AnnotationValue av, TypeMirror expectedType) {
          // just take the default action for the unknown
          return defaultAction(av, expectedType);
        }

        @Override
        public Boolean visitAnnotation(AnnotationMirror a, TypeMirror expectedType) {
          return MoreTypes.equivalence().equivalent(a.getAnnotationType(), expectedType)
              && validateAnnotation(a);
        }

        @Override
        public Boolean visitArray(List<? extends AnnotationValue> values, TypeMirror expectedType) {
          if (!expectedType.getKind().equals(TypeKind.ARRAY)) {
            return false;
          }
          TypeMirror componentType = MoreTypes.asArray(expectedType).getComponentType();
          return values.stream().allMatch(value -> value.accept(this, componentType));
        }

        @Override
        public Boolean visitEnumConstant(VariableElement enumConstant, TypeMirror expectedType) {
          return MoreTypes.equivalence().equivalent(enumConstant.asType(), expectedType)
              && validateElement(enumConstant);
        }

        @Override
        public Boolean visitType(TypeMirror type, TypeMirror ignored) {
          // We could check assignability here, but would require a Types instance. Since this
          // isn't really the sort of thing that shows up in a bad AST from upstream compilation
          // we ignore the expected type and just validate the type.  It might be wrong, but
          // it's valid.
          return validateType(type);
        }

        @Override
        public Boolean visitBoolean(boolean b, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(Boolean.TYPE, expectedType);
        }

        @Override
        public Boolean visitByte(byte b, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(Byte.TYPE, expectedType);
        }

        @Override
        public Boolean visitChar(char c, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(Character.TYPE, expectedType);
        }

        @Override
        public Boolean visitDouble(double d, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(Double.TYPE, expectedType);
        }

        @Override
        public Boolean visitFloat(float f, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(Float.TYPE, expectedType);
        }

        @Override
        public Boolean visitInt(int i, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(Integer.TYPE, expectedType);
        }

        @Override
        public Boolean visitLong(long l, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(Long.TYPE, expectedType);
        }

        @Override
        public Boolean visitShort(short s, TypeMirror expectedType) {
          return MoreTypes.isTypeOf(Short.TYPE, expectedType);
        }
      };

  public static boolean validateAnnotationValue(
      AnnotationValue annotationValue, TypeMirror expectedType) {
    return annotationValue.accept(VALUE_VALIDATING_VISITOR, expectedType);
  }

  private DaggerSuperficialValidation() {}
}

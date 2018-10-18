/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;

import com.google.auto.common.MoreElements;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;

/**
 * Validates members injection requests (members injection methods on components and requests for
 * {@code MembersInjector<Foo>}).
 */
final class MembersInjectionValidator {

  @Inject
  MembersInjectionValidator() {}

  /** Reports errors if a request for a {@code MembersInjector<Foo>}) is invalid. */
  ValidationReport<Element> validateMembersInjectionRequest(
      Element requestElement, TypeMirror membersInjectedType) {
    ValidationReport.Builder<Element> report = ValidationReport.about(requestElement);
    checkQualifiers(report, requestElement);
    membersInjectedType.accept(VALIDATE_MEMBERS_INJECTED_TYPE, report);
    return report.build();
  }

  /**
   * Reports errors if a members injection method on a component is invalid.
   *
   * @throws IllegalArgumentException if the method doesn't have exactly one parameter
   */
  ValidationReport<ExecutableElement> validateMembersInjectionMethod(
      ExecutableElement method, TypeMirror membersInjectedType) {
    checkArgument(
        method.getParameters().size() == 1, "expected a method with one parameter: %s", method);

    ValidationReport.Builder<ExecutableElement> report = ValidationReport.about(method);
    checkQualifiers(report, method);
    checkQualifiers(report, method.getParameters().get(0));
    membersInjectedType.accept(VALIDATE_MEMBERS_INJECTED_TYPE, report);
    return report.build();
  }

  private void checkQualifiers(ValidationReport.Builder<?> report, Element element) {
    for (AnnotationMirror qualifier : getQualifiers(element)) {
      report.addError("Cannot inject members into qualified types", element, qualifier);
      break; // just report on the first qualifier, in case there is more than one
    }
  }

  private static final TypeVisitor<Void, ValidationReport.Builder<?>>
      VALIDATE_MEMBERS_INJECTED_TYPE =
          new SimpleTypeVisitor8<Void, ValidationReport.Builder<?>>() {
            // Only declared types can be members-injected.
            @Override
            protected Void defaultAction(TypeMirror type, ValidationReport.Builder<?> report) {
              report.addError("Cannot inject members into " + type);
              return null;
            }

            @Override
            public Void visitDeclared(DeclaredType type, ValidationReport.Builder<?> report) {
              if (type.getTypeArguments().isEmpty()) {
                // If the type is the erasure of a generic type, that means the user referred to
                // Foo<T> as just 'Foo', which we don't allow.  (This is a judgement call; we
                // *could* allow it and instantiate the type bounds, but we don't.)
                if (!MoreElements.asType(type.asElement()).getTypeParameters().isEmpty()) {
                  report.addError("Cannot inject members into raw type " + type);
                }
              } else {
                // If the type has arguments, validate that each type argument is declared.
                // Otherwise the type argument may be a wildcard (or other type), and we can't
                // resolve that to actual types.  For array type arguments, validate the type of the
                // array.
                for (TypeMirror arg : type.getTypeArguments()) {
                  if (!arg.accept(DECLARED_OR_ARRAY, null)) {
                    report.addError(
                        "Cannot inject members into types with unbounded type arguments: " + type);
                  }
                }
              }
              return null;
            }
          };

  // TODO(dpb): Can this be inverted so it explicitly rejects wildcards or type variables?
  // This logic is hard to describe.
  private static final TypeVisitor<Boolean, Void> DECLARED_OR_ARRAY =
      new SimpleTypeVisitor8<Boolean, Void>(false) {
        @Override
        public Boolean visitArray(ArrayType arrayType, Void p) {
          return arrayType
              .getComponentType()
              .accept(
                  new SimpleTypeVisitor8<Boolean, Void>(false) {
                    @Override
                    public Boolean visitDeclared(DeclaredType declaredType, Void p) {
                      for (TypeMirror arg : declaredType.getTypeArguments()) {
                        if (!arg.accept(this, null)) {
                          return false;
                        }
                      }
                      return true;
                    }

                    @Override
                    public Boolean visitArray(ArrayType arrayType, Void p) {
                      return arrayType.getComponentType().accept(this, null);
                    }

                    @Override
                    public Boolean visitPrimitive(PrimitiveType primitiveType, Void p) {
                      return true;
                    }
                  },
                  null);
        }

        @Override
        public Boolean visitDeclared(DeclaredType t, Void p) {
          return true;
        }
      };
}

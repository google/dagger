/*
 * Copyright (C) 2022 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.asType;

import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

/** Utility class for getting field and method descriptors. */
public final class ElementDescriptors {
  private ElementDescriptors() {}

  /**
   * Returns the field descriptor of the given {@code element}.
   *
   * <p>This is useful for matching Kotlin Metadata JVM Signatures with elements from the AST.
   *
   * <p>For reference, see the <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2">JVM
   * specification, section 4.3.2</a>.
   */
  public static String getFieldDescriptor(VariableElement element) {
    return element.getSimpleName() + ":" + getDescriptor(element.asType());
  }

  /**
   * Returns the method descriptor of the given {@code element}.
   *
   * <p>This is useful for matching Kotlin Metadata JVM Signatures with elements from the AST.
   *
   * <p>For reference, see the <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">JVM
   * specification, section 4.3.3</a>.
   */
  public static String getMethodDescriptor(ExecutableElement element) {
    return element.getSimpleName() + getDescriptor(element.asType());
  }

  private static String getDescriptor(TypeMirror t) {
    return t.accept(JVM_DESCRIPTOR_TYPE_VISITOR, null);
  }

  private static final SimpleTypeVisitor8<String, Void> JVM_DESCRIPTOR_TYPE_VISITOR =
      new SimpleTypeVisitor8<String, Void>() {

        @Override
        public String visitArray(ArrayType arrayType, Void v) {
          return "[" + getDescriptor(arrayType.getComponentType());
        }

        @Override
        public String visitDeclared(DeclaredType declaredType, Void v) {
          return "L" + getInternalName(declaredType.asElement()) + ";";
        }

        @Override
        public String visitError(ErrorType errorType, Void v) {
          // For descriptor generating purposes we don't need a fully modeled type since we are
          // only interested in obtaining the class name in its "internal form".
          return visitDeclared(errorType, v);
        }

        @Override
        public String visitExecutable(ExecutableType executableType, Void v) {
          String parameterDescriptors =
              executableType.getParameterTypes().stream()
                  .map(ElementDescriptors::getDescriptor)
                  .collect(Collectors.joining());
          String returnDescriptor = getDescriptor(executableType.getReturnType());
          return "(" + parameterDescriptors + ")" + returnDescriptor;
        }

        @Override
        public String visitIntersection(IntersectionType intersectionType, Void v) {
          // For a type variable with multiple bounds: "the erasure of a type variable is determined
          // by the first type in its bound" - JVM Spec Sec 4.4
          return getDescriptor(intersectionType.getBounds().get(0));
        }

        @Override
        public String visitNoType(NoType noType, Void v) {
          return "V";
        }

        @Override
        public String visitPrimitive(PrimitiveType primitiveType, Void v) {
          switch (primitiveType.getKind()) {
            case BOOLEAN:
              return "Z";
            case BYTE:
              return "B";
            case SHORT:
              return "S";
            case INT:
              return "I";
            case LONG:
              return "J";
            case CHAR:
              return "C";
            case FLOAT:
              return "F";
            case DOUBLE:
              return "D";
            default:
              throw new IllegalArgumentException("Unknown primitive type.");
          }
        }

        @Override
        public String visitTypeVariable(TypeVariable typeVariable, Void v) {
          // The erasure of a type variable is the erasure of its leftmost bound. - JVM Spec Sec 4.6
          return getDescriptor(typeVariable.getUpperBound());
        }

        @Override
        public String defaultAction(TypeMirror typeMirror, Void v) {
          throw new IllegalArgumentException("Unsupported type: " + typeMirror);
        }

        @Override
        public String visitWildcard(WildcardType wildcardType, Void v) {
          return "";
        }

        /**
         * Returns the name of this element in its "internal form".
         *
         * <p>For reference, see the <a
         * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2">JVM
         * specification, section 4.2</a>.
         */
        private String getInternalName(Element element) {
          try {
            TypeElement typeElement = asType(element);
            switch (typeElement.getNestingKind()) {
              case TOP_LEVEL:
                return typeElement.getQualifiedName().toString().replace('.', '/');
              case MEMBER:
                return getInternalName(typeElement.getEnclosingElement())
                    + "$"
                    + typeElement.getSimpleName();
              default:
                throw new IllegalArgumentException("Unsupported nesting kind.");
            }
          } catch (IllegalArgumentException e) {
            // Not a TypeElement, try something else...
          }

          if (element instanceof QualifiedNameable) {
            QualifiedNameable qualifiedNameElement = (QualifiedNameable) element;
            return qualifiedNameElement.getQualifiedName().toString().replace('.', '/');
          }

          return element.getSimpleName().toString();
        }
      };
}

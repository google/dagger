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

package dagger.internal.codegen.xprocessing;

import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isField;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static androidx.room.compiler.processing.XElementKt.isMethodParameter;
import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static androidx.room.compiler.processing.XElementKt.isVariableElement;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.XAnnotated;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XEnumEntry;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XHasModifiers;
import androidx.room.compiler.processing.XMemberContainer;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import java.util.Collection;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
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

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XElement} helper methods. */
public final class XElements {

  // TODO(bcorso): Replace usages with getJvmName() once it exists.
  /** Returns the simple name of the member container. */
  public static String getSimpleName(XMemberContainer memberContainer) {
    return memberContainer.getClassName().simpleName();
  }

  // TODO(bcorso): Replace usages with getJvmName() once it exists.
  /** Returns the simple name of the element. */
  public static String getSimpleName(XElement element) {
    return toJavac(element).getSimpleName().toString();
  }

  /**
   * Returns the closest enclosing element that is a {@link XTypeElement} or throws an {@link
   * IllegalStateException} if one doesn't exists.
   */
  public static XTypeElement closestEnclosingTypeElement(XElement element) {
    return optionalClosestEnclosingTypeElement(element)
        .orElseThrow(() -> new IllegalStateException("No enclosing TypeElement for: " + element));
  }

  /**
   * Returns {@code true} if {@code encloser} is equal to or transitively encloses {@code enclosed}.
   */
  public static boolean transitivelyEncloses(XElement encloser, XElement enclosed) {
    XElement current = enclosed;
    while (current != null) {
      if (current.equals(encloser)) {
        return true;
      }
      current = current.getEnclosingElement();
    }
    return false;
  }

  private static Optional<XTypeElement> optionalClosestEnclosingTypeElement(XElement element) {
    if (isTypeElement(element)) {
      return Optional.of(asTypeElement(element));
    } else if (isConstructor(element)) {
      return Optional.of(asConstructor(element).getEnclosingElement());
    } else if (isMethod(element)) {
      return optionalClosestEnclosingTypeElement(asMethod(element).getEnclosingElement());
    } else if (isField(element)) {
      return optionalClosestEnclosingTypeElement(asField(element).getEnclosingElement());
    } else if (isMethodParameter(element)) {
      return optionalClosestEnclosingTypeElement(asMethodParameter(element).getEnclosingElement());
    }
    return Optional.empty();
  }

  public static boolean isAbstract(XElement element) {
    return asHasModifiers(element).isAbstract();
  }

  public static boolean isPrivate(XElement element) {
    return asHasModifiers(element).isPrivate();
  }

  public static boolean isStatic(XElement element) {
    return asHasModifiers(element).isStatic();
  }

  // TODO(bcorso): Ideally we would modify XElement to extend XHasModifiers to prevent possible
  // runtime exceptions if the element does not extend XHasModifiers. However, for Dagger's purpose
  // all usages should be on elements that do extend XHasModifiers, so generalizing this for
  // XProcessing is probably overkill for now.
  private static XHasModifiers asHasModifiers(XElement element) {
    // In javac, Element implements HasModifiers but in XProcessing XElement does not.
    // Currently, the elements that do not extend XHasModifiers are XMemberContainer, XEnumEntry,
    // XVariableElement. Though most instances of XMemberContainer will extend XHasModifiers through
    // XTypeElement instead.
    checkArgument(element instanceof XHasModifiers, "Element %s does not have modifiers", element);
    return (XHasModifiers) element;
  }

  public static boolean isPackage(XElement element) {
    return toJavac(element).getKind() == ElementKind.PACKAGE;
  }

  public static boolean isEnumEntry(XElement element) {
    return element instanceof XEnumEntry;
  }

  public static boolean isEnum(XElement element) {
    return toJavac(element).getKind() == ElementKind.ENUM;
  }

  public static boolean isExecutable(XElement element) {
    return isConstructor(element) || isMethod(element);
  }

  public static XExecutableElement asExecutable(XElement element) {
    checkState(isExecutable(element));
    return (XExecutableElement) element;
  }

  public static XTypeElement asTypeElement(XElement element) {
    checkState(isTypeElement(element));
    return (XTypeElement) element;
  }

  // TODO(bcorso): Rename this and the XElementKt.isMethodParameter to isExecutableParameter.
  public static XExecutableParameterElement asMethodParameter(XElement element) {
    checkState(isMethodParameter(element));
    return (XExecutableParameterElement) element;
  }

  public static XFieldElement asField(XElement element) {
    checkState(isField(element));
    return (XFieldElement) element;
  }

  public static XVariableElement asVariable(XElement element) {
    checkState(isVariableElement(element));
    return (XVariableElement) element;
  }

  public static XConstructorElement asConstructor(XElement element) {
    checkState(isConstructor(element));
    return (XConstructorElement) element;
  }

  public static XMethodElement asMethod(XElement element) {
    checkState(isMethod(element));
    return (XMethodElement) element;
  }

  public static ImmutableSet<XAnnotation> getAnnotatedAnnotations(
      XAnnotated annotated, ClassName annotationName) {
    return annotated.getAllAnnotations().stream()
        .filter(annotation -> annotation.getType().getTypeElement().hasAnnotation(annotationName))
        .collect(toImmutableSet());
  }

  /** Returns {@code true} if {@code annotated} is annotated with any of the given annotations. */
  public static boolean hasAnyAnnotation(XAnnotated annotated, ClassName... annotations) {
    return hasAnyAnnotation(annotated, ImmutableSet.copyOf(annotations));
  }

  /** Returns {@code true} if {@code annotated} is annotated with any of the given annotations. */
  public static boolean hasAnyAnnotation(XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream().anyMatch(annotated::hasAnnotation);
  }

  /**
   * Returns any annotation from {@code annotations} that annotates {@code annotated} or else {@code
   * Optional.empty()}.
   */
  public static Optional<XAnnotation> getAnyAnnotation(
      XAnnotated annotated, ClassName... annotations) {
    return getAnyAnnotation(annotated, ImmutableSet.copyOf(annotations));
  }

  /**
   * Returns any annotation from {@code annotations} that annotates {@code annotated} or else {@code
   * Optional.empty()}.
   */
  public static Optional<XAnnotation> getAnyAnnotation(
      XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream()
        .filter(annotated::hasAnnotation)
        .map(annotated::getAnnotation)
        .findFirst();
  }

  /** Returns all annotations from {@code annotations} that annotate {@code annotated}. */
  public static ImmutableSet<XAnnotation> getAllAnnotations(
      XAnnotated annotated, Collection<ClassName> annotations) {
    return annotations.stream()
        .filter(annotated::hasAnnotation)
        .map(annotated::getAnnotation)
        .collect(toImmutableSet());
  }

  /**
   * Returns the field descriptor of the given {@code element}.
   *
   * <p>This is useful for matching Kotlin Metadata JVM Signatures with elements from the AST.
   *
   * <p>For reference, see the <a
   * href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2">JVM
   * specification, section 4.3.2</a>.
   */
  public static String getFieldDescriptor(XFieldElement element) {
    return getFieldDescriptor(toJavac(element));
  }

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
  // TODO(bcorso): Expose getMethodDescriptor() method in XProcessing instead.
  public static String getMethodDescriptor(XMethodElement element) {
    return getMethodDescriptor(toJavac(element));
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

  private static String getDescriptor(TypeMirror type) {
    return type.accept(JVM_DESCRIPTOR_TYPE_VISITOR, null);
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
                  .map(XElements::getDescriptor)
                  .collect(joining());
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
            TypeElement typeElement = MoreElements.asType(element);
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

  private XElements() {}
}

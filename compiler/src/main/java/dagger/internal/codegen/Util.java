/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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

import dagger.internal.Keys;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * Utilities for handling types in annotation processors
 */
final class Util {
  private Util() {
  }

  public static PackageElement getPackage(Element type) {
    while (type.getKind() != ElementKind.PACKAGE) {
      type = type.getEnclosingElement();
    }
    return (PackageElement) type;
  }

  /**
   * Returns the supertype, or {@code null} if the supertype is a platform
   * class. This is intended for annotation processors that assume platform
   * classes will never be annotated with application annotations.
   */
  public static TypeMirror getApplicationSupertype(TypeElement type) {
    TypeMirror supertype = type.getSuperclass();
    return Keys.isPlatformType(supertype.toString()) ? null : supertype;
  }

  /** Returns a fully qualified class name to complement {@code type}. */
  public static String adapterName(TypeElement typeElement, String suffix) {
    StringBuilder builder = new StringBuilder();
    rawTypeToString(builder, typeElement, '$');
    builder.append(suffix);
    return builder.toString();
  }

  /** Returns a string for {@code type}. Primitive types are always boxed. */
  public static String typeToString(TypeMirror type) {
    StringBuilder result = new StringBuilder();
    typeToString(type, result, '.');
    return result.toString();
  }

  /** Returns a string for the raw type of {@code type}. Primitive types are always boxed. */
  public static String rawTypeToString(TypeMirror type, char innerClassSeparator) {
    if (!(type instanceof DeclaredType)) {
      throw new IllegalArgumentException("Unexpected type: " + type);
    }
    StringBuilder result = new StringBuilder();
    DeclaredType declaredType = (DeclaredType) type;
    rawTypeToString(result, (TypeElement) declaredType.asElement(), innerClassSeparator);
    return result.toString();
  }

  /**
   * Appends a string for {@code type} to {@code result}. Primitive types are
   * always boxed.
   *
   * @param innerClassSeparator either '.' or '$', which will appear in a
   *     class name like "java.lang.Map.Entry" or "java.lang.Map$Entry".
   *     Use '.' for references to existing types in code. Use '$' to define new
   *     class names and for strings that will be used by runtime reflection.
   */
  public static void typeToString(final TypeMirror type, final StringBuilder result,
      final char innerClassSeparator) {
    type.accept(new SimpleTypeVisitor6<Void, Void>() {
      @Override public Void visitDeclared(DeclaredType declaredType, Void v) {
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        rawTypeToString(result, typeElement, innerClassSeparator);
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (!typeArguments.isEmpty()) {
          result.append("<");
          for (int i = 0; i < typeArguments.size(); i++) {
            if (i != 0) {
              result.append(", ");
            }
            typeToString(typeArguments.get(i), result, innerClassSeparator);
          }
          result.append(">");
        }
        return null;
      }
      @Override public Void visitPrimitive(PrimitiveType primitiveType, Void v) {
        result.append(box((PrimitiveType) type).getName());
        return null;
      }
      @Override public Void visitArray(ArrayType arrayType, Void v) {
        TypeMirror type = arrayType.getComponentType();
        if (type instanceof PrimitiveType) {
          result.append(type.toString()); // Don't box, since this is an array.
        } else {
          typeToString(arrayType.getComponentType(), result, innerClassSeparator);
        }
        result.append("[]");
        return null;
      }
      @Override public Void visitTypeVariable(TypeVariable typeVariable, Void v) {
        result.append(typeVariable.asElement().getSimpleName());
        return null;
      }
      @Override public Void visitError(ErrorType errorType, Void v) {
        // There's already an error but it may not have been reported (most likely
        // a missing import). If we throw an UnsupportedOperationException here
        // we'll obscure the real error, so just continue.
        result.append("error");
        return null;
      }
      @Override protected Void defaultAction(TypeMirror typeMirror, Void v) {
        throw new UnsupportedOperationException(
            "Unexpected TypeKind " + typeMirror.getKind() + " for "  + typeMirror);
      }
    }, null);
  }

  private static final AnnotationValueVisitor<Object, Void> VALUE_EXTRACTOR
      = new SimpleAnnotationValueVisitor6<Object, Void>() {
    @Override protected Object defaultAction(Object o, Void v) {
      return o;
    }
    @Override public Object visitArray(List<? extends AnnotationValue> values, Void v) {
      Object[] result = new Object[values.size()];
      for (int i = 0; i < values.size(); i++) {
        result[i] = values.get(i).accept(this, null);
      }
      return result;
    }
  };

  /**
   * Returns the annotation on {@code element} formatted as a Map. This returns
   * a Map rather than an instance of the annotation interface to work-around
   * the fact that Class and Class[] fields won't work at code generation time.
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5089128
   */
  public static Map<String, Object> getAnnotation(Class<?> annotationType, Element element) {
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (!rawTypeToString(annotation.getAnnotationType(), '$')
          .equals(annotationType.getName())) {
        continue;
      }

      Map<String, Object> result = new LinkedHashMap<String, Object>();
      for (Method m : annotationType.getMethods()) {
        result.put(m.getName(), m.getDefaultValue());
      }
      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
          : annotation.getElementValues().entrySet()) {
        String name = e.getKey().getSimpleName().toString();
        Object value = e.getValue().accept(VALUE_EXTRACTOR, null);
        Object defaultValue = result.get(name);
        if (!lenientIsInstance(defaultValue.getClass(), value)) {
          throw new IllegalStateException(String.format(
              "Value of %s.%s is a %s but expected a %s\n    value: %s",
              annotationType, name, value.getClass().getName(), defaultValue.getClass().getName(),
              value instanceof Object[] ? Arrays.toString((Object[]) value) : value));
        }
        result.put(name, value);
      }
      return result;
    }

    return null; // Annotation not found.
  }

  /**
   * Returns true if {@code value} can be assigned to {@code expectedClass}.
   * Like {@link Class#isInstance} but more lenient for {@code Class<?>} values.
   */
  private static boolean lenientIsInstance(Class<?> expectedClass, Object value) {
    if (expectedClass.isArray()) {
      Class<?> componentType = expectedClass.getComponentType();
      if (!(value instanceof Object[])) {
        return false;
      }
      for (Object element : (Object[]) value) {
        if (!lenientIsInstance(componentType, element)) return false;
      }
      return true;
    } else if (expectedClass == Class.class) {
      return value instanceof TypeMirror;
    } else {
      return expectedClass == value.getClass();
    }
  }

  // TODO(sgoldfed): better format for other types of elements?
  static String elementToString(Element element) {
    switch (element.getKind()) {
      case FIELD:
      // fall through
      case CONSTRUCTOR:
      // fall through
      case METHOD:
        return element.getEnclosingElement() + "." + element;
      default:
        return element.toString();
    }
  }

  static void rawTypeToString(StringBuilder result, TypeElement type,
      char innerClassSeparator) {
    String packageName = getPackage(type).getQualifiedName().toString();
    String qualifiedName = type.getQualifiedName().toString();
    if (packageName.isEmpty()) {
        result.append(qualifiedName.replace('.', innerClassSeparator));
    } else {
      result.append(packageName);
      result.append('.');
      result.append(
          qualifiedName.substring(packageName.length() + 1).replace('.', innerClassSeparator));
    }
  }

  private static Class<?> box(PrimitiveType primitiveType) {
    switch (primitiveType.getKind()) {
      case BYTE:
        return Byte.class;
      case SHORT:
        return Short.class;
      case INT:
        return Integer.class;
      case LONG:
        return Long.class;
      case FLOAT:
        return Float.class;
      case DOUBLE:
        return Double.class;
      case BOOLEAN:
        return Boolean.class;
      case CHAR:
        return Character.class;
      case VOID:
        return Void.class;
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns the no-args constructor for {@code type}, or null if no such
   * constructor exists.
   */
  public static ExecutableElement getNoArgsConstructor(TypeElement type) {
    for (Element enclosed : type.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
        continue;
      }
      ExecutableElement constructor = (ExecutableElement) enclosed;
      if (constructor.getParameters().isEmpty()) {
        return constructor;
      }
    }
    return null;
  }

  /**
   * Returns true if generated code can invoke {@code constructor}. That is, if
   * the constructor is non-private and its enclosing class is either a
   * top-level class or a static nested class.
   */
  public static boolean isCallableConstructor(ExecutableElement constructor) {
    if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
      return false;
    }
    TypeElement type = (TypeElement) constructor.getEnclosingElement();
    return type.getEnclosingElement().getKind() == ElementKind.PACKAGE
        || type.getModifiers().contains(Modifier.STATIC);
  }

  /**
   * Returns a user-presentable string like {@code
   * coffee.CoffeeModule#provideHeater()}.
   */
  public static String methodName(ExecutableElement method) {
    return ((TypeElement) method.getEnclosingElement()).getQualifiedName()
        + "." + method.getSimpleName() + "()";
  }

  public static boolean isInterface(TypeMirror typeMirror) {
    return typeMirror instanceof DeclaredType
        && ((DeclaredType) typeMirror).asElement().getKind() == ElementKind.INTERFACE;
  }

  static boolean isStatic(Element element) {
    for (Modifier modifier : element.getModifiers()) {
      if (modifier.equals(Modifier.STATIC)) {
        return true;
      }
    }
    return false;
  }
}

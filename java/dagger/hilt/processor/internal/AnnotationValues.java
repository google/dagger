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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;
import static com.google.auto.common.MoreTypes.asTypeElement;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

/** A utility class for working with {@link AnnotationValue} instances. */
// TODO(bcorso): Update auto-common maven import so we can use it rather than this copy.
public final class AnnotationValues {

  private AnnotationValues() {}

  private static class DefaultVisitor<T> extends SimpleAnnotationValueVisitor8<T, Void> {
    final Class<T> clazz;

    DefaultVisitor(Class<T> clazz) {
      this.clazz = checkNotNull(clazz);
    }

    @Override
    public T defaultAction(Object o, Void unused) {
      throw new IllegalArgumentException(
          "Expected a " + clazz.getSimpleName() + ", got instead: " + o);
    }
  }

  private static final class TypeMirrorVisitor extends DefaultVisitor<DeclaredType> {
    static final TypeMirrorVisitor INSTANCE = new TypeMirrorVisitor();

    TypeMirrorVisitor() {
      super(DeclaredType.class);
    }

    @Override
    public DeclaredType visitType(TypeMirror value, Void unused) {
      return MoreTypes.asDeclared(value);
    }
  }

  /**
   * Returns the value as a class.
   *
   * @throws IllegalArgumentException if the value is not a class.
   */
  public static DeclaredType getTypeMirror(AnnotationValue value) {
    return TypeMirrorVisitor.INSTANCE.visit(value);
  }

  private static final class EnumVisitor extends DefaultVisitor<VariableElement> {
    static final EnumVisitor INSTANCE = new EnumVisitor();

    EnumVisitor() {
      super(VariableElement.class);
    }

    @Override
    public VariableElement visitEnumConstant(VariableElement value, Void unused) {
      return value;
    }
  }

  /** Returns a class array value as a set of {@link TypeElement}. */
  public static ImmutableSet<TypeElement> getTypeElements(AnnotationValue value) {
    return getAnnotationValues(value).stream()
        .map(AnnotationValues::getTypeElement)
        .collect(toImmutableSet());
  }

  /** Returns a class value as a {@link TypeElement}. */
  public static TypeElement getTypeElement(AnnotationValue value) {
    return asTypeElement(getTypeMirror(value));
  }

  /**
   * Returns the value as a VariableElement.
   *
   * @throws IllegalArgumentException if the value is not an enum.
   */
  public static VariableElement getEnum(AnnotationValue value) {
    return EnumVisitor.INSTANCE.visit(value);
  }

  /** Returns a string array value as a set of strings. */
  public static ImmutableSet<String> getStrings(AnnotationValue value) {
    return getAnnotationValues(value).stream()
        .map(AnnotationValues::getString)
        .collect(toImmutableSet());
  }

  /**
   * Returns the value as a string.
   *
   * @throws IllegalArgumentException if the value is not a string.
   */
  public static String getString(AnnotationValue value) {
    return valueOfType(value, String.class);
  }

  /**
   * Returns the value as a boolean.
   *
   * @throws IllegalArgumentException if the value is not a boolean.
   */
  public static boolean getBoolean(AnnotationValue value) {
    return valueOfType(value, Boolean.class);
  }

  private static <T> T valueOfType(AnnotationValue annotationValue, Class<T> type) {
    Object value = annotationValue.getValue();
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException(
          "Expected " + type.getSimpleName() + ", got instead: " + value);
    }
    return type.cast(value);
  }

  /** Returns the int value of an annotation */
  public static int getIntValue(AnnotationMirror annotation, String valueName) {
    return (int) getAnnotationValue(annotation, valueName).getValue();
  }

  /** Returns an optional int value of an annotation if the value name is present */
  public static Optional<Integer> getOptionalIntValue(
      AnnotationMirror annotation, String valueName) {
    return isValuePresent(annotation, valueName)
        ? Optional.of(getIntValue(annotation, valueName))
        : Optional.empty();
  }

  /** Returns the String value of an annotation */
  public static String getStringValue(AnnotationMirror annotation, String valueName) {
    return (String) getAnnotationValue(annotation, valueName).getValue();
  }

  /** Returns an optional String value of an annotation if the value name is present */
  public static Optional<String> getOptionalStringValue(
      AnnotationMirror annotation, String valueName) {
    return isValuePresent(annotation, valueName)
        ? Optional.of(getStringValue(annotation, valueName))
        : Optional.empty();
  }

  /** Returns the int array value of an annotation */
  public static int[] getIntArrayValue(AnnotationMirror annotation, String valueName) {
    return getAnnotationValues(getAnnotationValue(annotation, valueName)).stream()
        .mapToInt(it -> (int) it.getValue())
        .toArray();
  }

  /** Returns the String array value of an annotation */
  public static String[] getStringArrayValue(AnnotationMirror annotation, String valueName) {
    return getAnnotationValues(getAnnotationValue(annotation, valueName)).stream()
        .map(it -> (String) it.getValue())
        .toArray(String[]::new);
  }

  private static boolean isValuePresent(AnnotationMirror annotation, String valueName) {
    return getAnnotationValuesWithDefaults(annotation).keySet().stream()
        .anyMatch(member -> member.getSimpleName().contentEquals(valueName));
  }

  /**
   * Returns the list of values represented by an array annotation value.
   *
   * @throws IllegalArgumentException unless {@code annotationValue} represents an array
   */
  public static ImmutableList<AnnotationValue> getAnnotationValues(
      AnnotationValue annotationValue) {
    return annotationValue.accept(AS_ANNOTATION_VALUES, null);
  }

  private static final AnnotationValueVisitor<ImmutableList<AnnotationValue>, String>
      AS_ANNOTATION_VALUES =
          new SimpleAnnotationValueVisitor8<ImmutableList<AnnotationValue>, String>() {
            @Override
            public ImmutableList<AnnotationValue> visitArray(
                List<? extends AnnotationValue> vals, String elementName) {
              return ImmutableList.copyOf(vals);
            }

            @Override
            protected ImmutableList<AnnotationValue> defaultAction(Object o, String elementName) {
              throw new IllegalArgumentException(elementName + " is not an array: " + o);
            }
          };
}

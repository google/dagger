/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.MapKey;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.AnnotationMirrors.getAnnotatedAnnotations;
import static com.google.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.transform;
import static dagger.internal.codegen.writer.Snippet.makeParametersSnippet;
import static javax.lang.model.util.ElementFilter.methodsIn;

/**
 * Methods for extracting {@link MapKey} annotations and key snippets from binding elements.
 */
final class MapKeys {

  /**
   * If {@code bindingElement} is annotated with a {@link MapKey} annotation, returns it.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one {@code MapKey}
   *     annotation
   */
  static Optional<? extends AnnotationMirror> getMapKey(Element bindingElement) {
    ImmutableSet<? extends AnnotationMirror> mapKeys = getMapKeys(bindingElement);
    return mapKeys.isEmpty()
        ? Optional.<AnnotationMirror>absent()
        : Optional.of(getOnlyElement(mapKeys));
  }

  /**
   * Returns all of the {@link MapKey} annotations that annotate {@code bindingElement}.
   */
  static ImmutableSet<? extends AnnotationMirror> getMapKeys(Element bindingElement) {
    return getAnnotatedAnnotations(bindingElement, MapKey.class);
  }

  /**
   * Returns the map key type for an unwrapped {@link MapKey} annotation type. If the single member
   * type is primitive, returns the boxed type.
   *
   * @throws IllegalArgumentException if {@code mapKeyAnnotationType} is not an annotation type or
   *     has more than one member, or if its single member is an array
   * @throws NoSuchElementException if the annotation has no members
   */
  public static DeclaredType getUnwrappedMapKeyType(
      final DeclaredType mapKeyAnnotationType, final Types types) {
    checkArgument(
        MoreTypes.asTypeElement(mapKeyAnnotationType).getKind() == ElementKind.ANNOTATION_TYPE,
        "%s is not an annotation type",
        mapKeyAnnotationType);

    final ExecutableElement onlyElement =
        getOnlyElement(methodsIn(mapKeyAnnotationType.asElement().getEnclosedElements()));

    SimpleTypeVisitor6<DeclaredType, Void> keyTypeElementVisitor =
        new SimpleTypeVisitor6<DeclaredType, Void>() {

          @Override
          public DeclaredType visitArray(ArrayType t, Void p) {
            throw new IllegalArgumentException(
                mapKeyAnnotationType + "." + onlyElement.getSimpleName() + " cannot be an array");
          }

          @Override
          public DeclaredType visitPrimitive(PrimitiveType t, Void p) {
            return MoreTypes.asDeclared(types.boxedClass(t).asType());
          }

          @Override
          public DeclaredType visitDeclared(DeclaredType t, Void p) {
            return t;
          }
        };
    return keyTypeElementVisitor.visit(onlyElement.getReturnType());
  }

  /**
   * Returns the name of the generated class that contains the static {@code create} methods for a
   * {@link MapKey} annotation type.
   */
  public static ClassName getMapKeyCreatorClassName(TypeElement mapKeyType) {
    ClassName enclosingClassName = ClassName.fromTypeElement(mapKeyType);
    return enclosingClassName
        .topLevelClassName()
        .peerNamed(enclosingClassName.classFileName() + "Creator");
  }

  /**
   * Returns a snippet for the map key specified by the {@link MapKey} annotation on
   * {@code bindingElement}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one {@code MapKey}
   *     annotation
   * @throws IllegalStateException if {@code bindingElement} is not annotated with a {@code MapKey}
   *     annotation
   */
  static Snippet getMapKeySnippet(Element bindingElement) {
    AnnotationMirror mapKey = getMapKey(bindingElement).get();
    TypeElement mapKeyAnnotationType = MoreTypes.asTypeElement(mapKey.getAnnotationType());
    ClassName mapKeyCreator = MapKeys.getMapKeyCreatorClassName(mapKeyAnnotationType);
    if (mapKeyAnnotationType.getAnnotation(MapKey.class).unwrapValue()) {
      AnnotationValue memberToUnwrap = getOnlyElement(mapKey.getElementValues().values());
      return new MapKeySnippetExceptArrays(mapKeyCreator).visit(memberToUnwrap, memberToUnwrap);
    } else {
      return annotationSnippet(mapKey, new MapKeySnippet(mapKeyCreator));
    }
  }

  /**
   * Returns a snippet to create the visited value in code. Expects its parameter to be a class with
   * static creation methods for all nested annotation types.
   *
   * <p>Note that {@link AnnotationValue#toString()} is the source-code representation of the value
   * <em>when used in an annotation</em>, which is not always the same as the representation needed
   * when creating the value in a method body.
   *
   * <p>For example, inside an annotation, a nested array of {@code int}s is simply
   * <code>{1, 2, 3}</code>, but in code it would have to be <code> new int[] {1, 2, 3}</code>.
   */
  private static class MapKeySnippet
      extends SimpleAnnotationValueVisitor6<Snippet, AnnotationValue> {

    final ClassName mapKeyCreator;

    MapKeySnippet(ClassName mapKeyCreator) {
      this.mapKeyCreator = mapKeyCreator;
    }

    @Override
    public Snippet visitEnumConstant(VariableElement c, AnnotationValue p) {
      return Snippet.format(
          "%s.%s", TypeNames.forTypeMirror(c.getEnclosingElement().asType()), c.getSimpleName());
    }

    @Override
    public Snippet visitAnnotation(AnnotationMirror a, AnnotationValue p) {
      return annotationSnippet(a, this);
    }

    @Override
    public Snippet visitType(TypeMirror t, AnnotationValue p) {
      return Snippet.format("%s.class", TypeNames.forTypeMirror(t));
    }

    @Override
    public Snippet visitString(String s, AnnotationValue p) {
      return Snippet.format("%s", p);
    }

    @Override
    public Snippet visitByte(byte b, AnnotationValue p) {
      return Snippet.format("(byte) %s", b);
    }

    @Override
    public Snippet visitChar(char c, AnnotationValue p) {
      return Snippet.format("%s", p);
    }

    @Override
    public Snippet visitDouble(double d, AnnotationValue p) {
      return Snippet.format("%sD", d);
    }

    @Override
    public Snippet visitFloat(float f, AnnotationValue p) {
      return Snippet.format("%sF", f);
    }

    @Override
    public Snippet visitInt(int i, AnnotationValue p) {
      return Snippet.format("(int) %s", i);
    }

    @Override
    public Snippet visitLong(long i, AnnotationValue p) {
      return Snippet.format("%sL", i);
    }

    @Override
    public Snippet visitShort(short s, AnnotationValue p) {
      return Snippet.format("(short) %s", s);
    }

    @Override
    protected Snippet defaultAction(Object o, AnnotationValue p) {
      return Snippet.format("%s", o);
    }

    @Override
    public Snippet visitArray(List<? extends AnnotationValue> values, AnnotationValue p) {
      ImmutableList.Builder<Snippet> snippets = ImmutableList.builder();
      for (int i = 0; i < values.size(); i++) {
        snippets.add(this.visit(values.get(i), p));
      }
      return Snippet.format("{%s}", makeParametersSnippet(snippets.build()));
    }
  }

  /**
   * Returns a snippet for the visited value. Expects its parameter to be a class with static
   * creation methods for all nested annotation types.
   *
   * <p>Throws {@link IllegalArgumentException} if the visited value is an array.
   */
  private static class MapKeySnippetExceptArrays extends MapKeySnippet {

    MapKeySnippetExceptArrays(ClassName mapKeyCreator) {
      super(mapKeyCreator);
    }

    @Override
    public Snippet visitArray(List<? extends AnnotationValue> values, AnnotationValue p) {
      throw new IllegalArgumentException("Cannot unwrap arrays");
    }
  }

  /**
   * Returns a snippet that calls a static method on {@code mapKeySnippet.mapKeyCreator} to create
   * an annotation from {@code mapKeyAnnotation}.
   */
  private static Snippet annotationSnippet(
      AnnotationMirror mapKeyAnnotation, final MapKeySnippet mapKeySnippet) {
    return Snippet.format(
        "%s.create%s(%s)",
        mapKeySnippet.mapKeyCreator,
        mapKeyAnnotation.getAnnotationType().asElement().getSimpleName(),
        makeParametersSnippet(
            transform(
                getAnnotationValuesWithDefaults(mapKeyAnnotation).entrySet(),
                new Function<Map.Entry<ExecutableElement, AnnotationValue>, Snippet>() {
                  @Override
                  public Snippet apply(Map.Entry<ExecutableElement, AnnotationValue> entry) {
                    return ARRAY_LITERAL_PREFIX.visit(
                        entry.getKey().getReturnType(),
                        mapKeySnippet.visit(entry.getValue(), entry.getValue()));
                  }
                })));
  }

  /**
   * If the visited type is an array, prefixes the parameter snippet with {@code new T[]}, where
   * {@code T} is the raw array component type.
   */
  private static final SimpleTypeVisitor6<Snippet, Snippet> ARRAY_LITERAL_PREFIX =
      new SimpleTypeVisitor6<Snippet, Snippet>() {

        @Override
        public Snippet visitArray(ArrayType t, Snippet p) {
          return Snippet.format("new %s[] %s", RAW_TYPE_NAME.visit(t.getComponentType()), p);
        }

        @Override
        protected Snippet defaultAction(TypeMirror e, Snippet p) {
          return p;
        }
      };

  /**
   * If the visited type is an array, returns the name of its raw component type; otherwise returns
   * the name of the type itself.
   */
  private static final SimpleTypeVisitor6<TypeName, Void> RAW_TYPE_NAME =
      new SimpleTypeVisitor6<TypeName, Void>() {
        @Override
        public TypeName visitDeclared(DeclaredType t, Void p) {
          return ClassName.fromTypeElement(MoreTypes.asTypeElement(t));
        }

        @Override
        protected TypeName defaultAction(TypeMirror e, Void p) {
          return TypeNames.forTypeMirror(e);
        }
      };

  private MapKeys() {}
}

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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.MapKey;
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
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.SourceFiles.classFileName;
import static javax.lang.model.util.ElementFilter.methodsIn;

/**
 * Methods for extracting {@link MapKey} annotations and key code blocks from binding elements.
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
   * Returns the annotation value if {@code mapKey}'s type is annotated with
   * {@link MapKey @MapKey(unwrapValue = true)}.
   *
   * @throws IllegalArgumentException if {@code mapKey}'s type is not annotated with
   *     {@link MapKey @MapKey} at all.
   */
  static Optional<? extends AnnotationValue> unwrapValue(AnnotationMirror mapKey) {
    MapKey mapKeyAnnotation = mapKey.getAnnotationType().asElement().getAnnotation(MapKey.class);
    checkArgument(
        mapKeyAnnotation != null, "%s is not annotated with @MapKey", mapKey.getAnnotationType());
    return mapKeyAnnotation.unwrapValue()
        ? Optional.of(getOnlyElement(mapKey.getElementValues().values()))
        : Optional.<AnnotationValue>absent();
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
    ClassName mapKeyTypeName = ClassName.get(mapKeyType);
    return mapKeyTypeName.topLevelClassName().peerClass(classFileName(mapKeyTypeName) + "Creator");
  }

  /**
   * Returns a code block for the map key specified by the {@link MapKey} annotation on
   * {@code bindingElement}.
   *
   * @throws IllegalArgumentException if the element is annotated with more than one {@code MapKey}
   *     annotation
   * @throws IllegalStateException if {@code bindingElement} is not annotated with a {@code MapKey}
   *     annotation
   */
  static CodeBlock getMapKeyExpression(Element bindingElement) {
    AnnotationMirror mapKey = getMapKey(bindingElement).get();
    ClassName mapKeyCreator =
        getMapKeyCreatorClassName(MoreTypes.asTypeElement(mapKey.getAnnotationType()));
    Optional<? extends AnnotationValue> unwrappedValue = unwrapValue(mapKey);
    if (unwrappedValue.isPresent()) {
      return new MapKeyExpressionExceptArrays(mapKeyCreator)
          .visit(unwrappedValue.get(), unwrappedValue.get());
    } else {
      return annotationExpression(mapKey, new MapKeyExpression(mapKeyCreator));
    }
  }

  /**
   * Returns a code block to create the visited value in code. Expects its parameter to be a class
   * with static creation methods for all nested annotation types.
   *
   * <p>Note that {@link AnnotationValue#toString()} is the source-code representation of the value
   * <em>when used in an annotation</em>, which is not always the same as the representation needed
   * when creating the value in a method body.
   *
   * <p>For example, inside an annotation, a nested array of {@code int}s is simply
   * {@code {1, 2, 3}}, but in code it would have to be {@code new int[] {1, 2, 3}}.
   */
  private static class MapKeyExpression
      extends SimpleAnnotationValueVisitor6<CodeBlock, AnnotationValue> {

    final ClassName mapKeyCreator;

    MapKeyExpression(ClassName mapKeyCreator) {
      this.mapKeyCreator = mapKeyCreator;
    }

    @Override
    public CodeBlock visitEnumConstant(VariableElement c, AnnotationValue p) {
      return CodeBlock.of(
          "$T.$L", TypeName.get(c.getEnclosingElement().asType()), c.getSimpleName());
    }

    @Override
    public CodeBlock visitAnnotation(AnnotationMirror a, AnnotationValue p) {
      return annotationExpression(a, this);
    }

    @Override
    public CodeBlock visitType(TypeMirror t, AnnotationValue p) {
      return CodeBlock.of("$T.class", TypeName.get(t));
    }

    @Override
    public CodeBlock visitString(String s, AnnotationValue p) {
      return CodeBlock.of("$S", s);
    }

    @Override
    public CodeBlock visitByte(byte b, AnnotationValue p) {
      return CodeBlock.of("(byte) $L", b);
    }

    @Override
    public CodeBlock visitChar(char c, AnnotationValue p) {
      return CodeBlock.of("$L", p);
    }

    @Override
    public CodeBlock visitDouble(double d, AnnotationValue p) {
      return CodeBlock.of("$LD", d);
    }

    @Override
    public CodeBlock visitFloat(float f, AnnotationValue p) {
      return CodeBlock.of("$LF", f);
    }

    @Override
    public CodeBlock visitInt(int i, AnnotationValue p) {
      return CodeBlock.of("(int) $L", i);
    }

    @Override
    public CodeBlock visitLong(long i, AnnotationValue p) {
      return CodeBlock.of("$LL", i);
    }

    @Override
    public CodeBlock visitShort(short s, AnnotationValue p) {
      return CodeBlock.of("(short) $L", s);
    }

    @Override
    protected CodeBlock defaultAction(Object o, AnnotationValue p) {
      return CodeBlock.of("$L", o);
    }

    @Override
    public CodeBlock visitArray(List<? extends AnnotationValue> values, AnnotationValue p) {
      ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
      for (int i = 0; i < values.size(); i++) {
        codeBlocks.add(this.visit(values.get(i), p));
      }
      return CodeBlock.of("{$L}", makeParametersCodeBlock(codeBlocks.build()));
    }
  }

  /**
   * Returns a code block for the visited value. Expects its parameter to be a class with static
   * creation methods for all nested annotation types.
   *
   * <p>Throws {@link IllegalArgumentException} if the visited value is an array.
   */
  private static class MapKeyExpressionExceptArrays extends MapKeyExpression {

    MapKeyExpressionExceptArrays(ClassName mapKeyCreator) {
      super(mapKeyCreator);
    }

    @Override
    public CodeBlock visitArray(List<? extends AnnotationValue> values, AnnotationValue p) {
      throw new IllegalArgumentException("Cannot unwrap arrays");
    }
  }

  /**
   * Returns a code block that calls a static method on {@code mapKeyCodeBlock.mapKeyCreator} to
   * create an annotation from {@code mapKeyAnnotation}.
   */
  private static CodeBlock annotationExpression(
      AnnotationMirror mapKeyAnnotation, final MapKeyExpression mapKeyExpression) {
    return CodeBlock.of(
        "$T.create$L($L)",
        mapKeyExpression.mapKeyCreator,
        mapKeyAnnotation.getAnnotationType().asElement().getSimpleName(),
        makeParametersCodeBlock(
            transform(
                getAnnotationValuesWithDefaults(mapKeyAnnotation).entrySet(),
                new Function<Map.Entry<ExecutableElement, AnnotationValue>, CodeBlock>() {
                  @Override
                  public CodeBlock apply(Map.Entry<ExecutableElement, AnnotationValue> entry) {
                    return ARRAY_LITERAL_PREFIX.visit(
                        entry.getKey().getReturnType(),
                        mapKeyExpression.visit(entry.getValue(), entry.getValue()));
                  }
                })));
  }

  /**
   * If the visited type is an array, prefixes the parameter code block with {@code new T[]}, where
   * {@code T} is the raw array component type.
   */
  private static final SimpleTypeVisitor6<CodeBlock, CodeBlock> ARRAY_LITERAL_PREFIX =
      new SimpleTypeVisitor6<CodeBlock, CodeBlock>() {

        @Override
        public CodeBlock visitArray(ArrayType t, CodeBlock p) {
          return CodeBlock.of("new $T[] $L", RAW_TYPE_NAME.visit(t.getComponentType()), p);
        }

        @Override
        protected CodeBlock defaultAction(TypeMirror e, CodeBlock p) {
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
          return ClassName.get(MoreTypes.asTypeElement(t));
        }

        @Override
        protected TypeName defaultAction(TypeMirror e, Void p) {
          return TypeName.get(e);
        }
      };

  private MapKeys() {}
}

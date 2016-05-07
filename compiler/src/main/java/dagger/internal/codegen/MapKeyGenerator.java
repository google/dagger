/*
 * Copyright (C) 2014 Google, Inc.
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
import com.google.auto.value.AutoAnnotation;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.MapKey;
import dagger.internal.codegen.MapKeyGenerator.MapKeyCreatorSpecification;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.MapKeys.getMapKeyCreatorClassName;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.methodsIn;

/**
 * Generates classes that create annotations required to instantiate {@link MapKey}s.
 *
 * @since 2.0
 */
final class MapKeyGenerator extends SourceFileGenerator<MapKeyCreatorSpecification> {

  /**
   * Specification of the {@link MapKey} annotation and the annotation type to generate.
   */
  @AutoValue
  abstract static class MapKeyCreatorSpecification {
    /**
     * The {@link MapKey}-annotated annotation.
     */
    abstract TypeElement mapKeyElement();

    /**
     * The annotation type to write create methods for. For wrapped {@link MapKey}s, this is
     * {@link #mapKeyElement()}. For unwrapped {@code MapKey}s whose single element is an
     * annotation, this is that annotation element.
     */
    abstract TypeElement annotationElement();

    /**
     * Returns a specification for a wrapped {@link MapKey}-annotated annotation.
     */
    static MapKeyCreatorSpecification wrappedMapKey(TypeElement mapKeyElement) {
      return new AutoValue_MapKeyGenerator_MapKeyCreatorSpecification(mapKeyElement, mapKeyElement);
    }

    /**
     * Returns a specification for an unwrapped {@link MapKey}-annotated annotation whose single
     * element is a nested annotation.
     */
    static MapKeyCreatorSpecification unwrappedMapKeyWithAnnotationValue(
        TypeElement mapKeyElement, TypeElement annotationElement) {
      return new AutoValue_MapKeyGenerator_MapKeyCreatorSpecification(
          mapKeyElement, annotationElement);
    }
  }

  MapKeyGenerator(Filer filer, Elements elements) {
    super(filer, elements);
  }

  @Override
  ClassName nameGeneratedType(MapKeyCreatorSpecification mapKeyCreatorType) {
    return getMapKeyCreatorClassName(mapKeyCreatorType.mapKeyElement());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(
      MapKeyCreatorSpecification mapKeyCreatorType) {
    return Optional.of(mapKeyCreatorType.mapKeyElement());
  }

  @Override
  Optional<TypeSpec.Builder> write(
      ClassName generatedTypeName, MapKeyCreatorSpecification mapKeyCreatorType) {
    TypeSpec.Builder mapKeyCreatorBuilder =
        classBuilder(generatedTypeName).addModifiers(PUBLIC, FINAL);

    mapKeyCreatorBuilder.addMethod(constructorBuilder().addModifiers(PRIVATE).build());

    for (TypeElement annotationElement :
        nestedAnnotationElements(mapKeyCreatorType.annotationElement())) {
      mapKeyCreatorBuilder.addMethod(buildCreateMethod(generatedTypeName, annotationElement));
    }

    return Optional.of(mapKeyCreatorBuilder);
  }

  private MethodSpec buildCreateMethod(
      ClassName mapKeyGeneratedTypeName, TypeElement annotationElement) {
    String createMethodName = "create" + annotationElement.getSimpleName();
    MethodSpec.Builder createMethod =
        methodBuilder(createMethodName)
            .addAnnotation(AutoAnnotation.class)
            .addModifiers(PUBLIC, STATIC)
            .returns(TypeName.get(annotationElement.asType()));

    ImmutableList.Builder<CodeBlock> parameters = ImmutableList.builder();
    for (ExecutableElement annotationMember : methodsIn(annotationElement.getEnclosedElements())) {
      String parameterName = annotationMember.getSimpleName().toString();
      TypeName parameterType = TypeName.get(annotationMember.getReturnType());
      createMethod.addParameter(parameterType, parameterName);
      parameters.add(CodeBlock.of("$L", parameterName));
    }

    ClassName autoAnnotationClass = mapKeyGeneratedTypeName.peerClass(
        "AutoAnnotation_" + mapKeyGeneratedTypeName.simpleName() + "_" + createMethodName);
    createMethod.addStatement(
        "return new $T($L)", autoAnnotationClass, makeParametersCodeBlock(parameters.build()));
    return createMethod.build();
  }

  private static Set<TypeElement> nestedAnnotationElements(TypeElement annotationElement) {
    return nestedAnnotationElements(annotationElement, new LinkedHashSet<TypeElement>());
  }

  @CanIgnoreReturnValue
  private static Set<TypeElement> nestedAnnotationElements(
      TypeElement annotationElement, Set<TypeElement> annotationElements) {
    if (annotationElements.add(annotationElement)) {
      for (ExecutableElement method : methodsIn(annotationElement.getEnclosedElements())) {
        TRAVERSE_NESTED_ANNOTATIONS.visit(method.getReturnType(), annotationElements);
      }
    }
    return annotationElements;
  }

  private static final SimpleTypeVisitor6<Void, Set<TypeElement>> TRAVERSE_NESTED_ANNOTATIONS =
      new SimpleTypeVisitor6<Void, Set<TypeElement>>() {
        @Override
        public Void visitDeclared(DeclaredType t, Set<TypeElement> p) {
          TypeElement typeElement = MoreTypes.asTypeElement(t);
          if (typeElement.getKind() == ElementKind.ANNOTATION_TYPE) {
            nestedAnnotationElements(typeElement, p);
          }
          return null;
        }
      };
}

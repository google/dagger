/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AnnotationExpression.createMethodName;
import static dagger.internal.codegen.AnnotationExpression.getAnnotationCreatorClassName;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * Generates classes that create annotation instances for an annotation type. The generated class
 * will have a private empty constructor, a static method that creates the annotation type itself,
 * and a static method that creates each annotation type that is nested in the top-level annotation
 * type.
 *
 * <p>So for an example annotation:
 *
 * <pre>
 *   {@literal @interface} Foo {
 *     String s();
 *     int i();
 *     Bar bar(); // an annotation defined elsewhere
 *   }
 * </pre>
 *
 * the generated class will look like:
 *
 * <pre>
 *   public final class FooCreator {
 *     private FooCreator() {}
 *
 *     public static Foo createFoo(String s, int i, Bar bar) { … }
 *     public static Bar createBar(…) { … }
 *   }
 * </pre>
 */
class AnnotationCreatorGenerator extends SourceFileGenerator<TypeElement> {
  private static final ClassName AUTO_ANNOTATION =
      ClassName.get("com.google.auto.value", "AutoAnnotation");

  @Inject
  AnnotationCreatorGenerator(Filer filer, DaggerElements elements, SourceVersion sourceVersion) {
    super(filer, elements, sourceVersion);
  }

  @Override
  ClassName nameGeneratedType(TypeElement annotationType) {
    return getAnnotationCreatorClassName(annotationType);
  }

  @Override
  Element originatingElement(TypeElement annotationType) {
    return annotationType;
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, TypeElement annotationType) {
    TypeSpec.Builder annotationCreatorBuilder =
        classBuilder(generatedTypeName)
            .addModifiers(PUBLIC, FINAL)
            .addMethod(constructorBuilder().addModifiers(PRIVATE).build());

    for (TypeElement annotationElement : annotationsToCreate(annotationType)) {
      annotationCreatorBuilder.addMethod(buildCreateMethod(generatedTypeName, annotationElement));
    }

    return Optional.of(annotationCreatorBuilder);
  }

  private MethodSpec buildCreateMethod(ClassName generatedTypeName, TypeElement annotationElement) {
    String createMethodName = createMethodName(annotationElement);
    MethodSpec.Builder createMethod =
        methodBuilder(createMethodName)
            .addAnnotation(AUTO_ANNOTATION)
            .addModifiers(PUBLIC, STATIC)
            .returns(TypeName.get(annotationElement.asType()));

    ImmutableList.Builder<CodeBlock> parameters = ImmutableList.builder();
    for (ExecutableElement annotationMember : methodsIn(annotationElement.getEnclosedElements())) {
      String parameterName = annotationMember.getSimpleName().toString();
      TypeName parameterType = TypeName.get(annotationMember.getReturnType());
      createMethod.addParameter(parameterType, parameterName);
      parameters.add(CodeBlock.of("$L", parameterName));
    }

    ClassName autoAnnotationClass =
        generatedTypeName.peerClass(
            "AutoAnnotation_" + generatedTypeName.simpleName() + "_" + createMethodName);
    createMethod.addStatement(
        "return new $T($L)", autoAnnotationClass, makeParametersCodeBlock(parameters.build()));
    return createMethod.build();
  }

  /**
   * Returns the annotation types for which {@code @AutoAnnotation static Foo createFoo(…)} methods
   * should be written.
   */
  protected Set<TypeElement> annotationsToCreate(TypeElement annotationElement) {
    return nestedAnnotationElements(annotationElement, new LinkedHashSet<>());
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

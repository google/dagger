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

package dagger.internal.codegen.writing;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.binding.AnnotationExpression.createMethodName;
import static dagger.internal.codegen.binding.AnnotationExpression.getAnnotationCreatorClassName;
import static dagger.internal.codegen.javapoet.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.base.SourceFileGenerator;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;

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
public class AnnotationCreatorGenerator extends SourceFileGenerator<XTypeElement> {
  private static final ClassName AUTO_ANNOTATION =
      ClassName.get("com.google.auto.value", "AutoAnnotation");

  @Inject
  AnnotationCreatorGenerator(XFiler filer, XProcessingEnv processingEnv) {
    super(filer, processingEnv);
  }

  @Override
  public XElement originatingElement(XTypeElement annotationType) {
    return annotationType;
  }

  @Override
  public ImmutableList<TypeSpec.Builder> topLevelTypes(XTypeElement annotationType) {
    ClassName generatedTypeName = getAnnotationCreatorClassName(annotationType);
    TypeSpec.Builder annotationCreatorBuilder =
        classBuilder(generatedTypeName)
            .addModifiers(PUBLIC, FINAL)
            .addMethod(constructorBuilder().addModifiers(PRIVATE).build());

    for (XTypeElement annotationElement : annotationsToCreate(annotationType)) {
      annotationCreatorBuilder.addMethod(buildCreateMethod(generatedTypeName, annotationElement));
    }

    return ImmutableList.of(annotationCreatorBuilder);
  }

  private MethodSpec buildCreateMethod(
      ClassName generatedTypeName, XTypeElement annotationElement) {
    String createMethodName = createMethodName(annotationElement);
    MethodSpec.Builder createMethod =
        methodBuilder(createMethodName)
            .addAnnotation(AUTO_ANNOTATION)
            .addModifiers(PUBLIC, STATIC)
            .returns(annotationElement.getType().getTypeName());

    ImmutableList.Builder<CodeBlock> parameters = ImmutableList.builder();
    for (XMethodElement annotationMember : annotationElement.getDeclaredMethods()) {
      String parameterName = getSimpleName(annotationMember);
      TypeName parameterType = annotationMember.getReturnType().getTypeName();
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
  protected Set<XTypeElement> annotationsToCreate(XTypeElement annotationElement) {
    return nestedAnnotationElements(annotationElement, new LinkedHashSet<>());
  }

  @CanIgnoreReturnValue
  private static Set<XTypeElement> nestedAnnotationElements(
      XTypeElement annotationElement, Set<XTypeElement> annotationElements) {
    if (annotationElements.add(annotationElement)) {
      for (XMethodElement method : annotationElement.getDeclaredMethods()) {
        XTypeElement returnType = method.getReturnType().getTypeElement();
        // Return type may be null if it doesn't return a type or type is not known
        if (returnType != null && returnType.isAnnotationClass()) {
          // Ignore the return value since this method is just an accumulator method.
          nestedAnnotationElements(returnType, annotationElements);
        }
      }
    }
    return annotationElements;
  }
}

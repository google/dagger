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

import static androidx.room.compiler.processing.XTypeKt.isArray;
import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static dagger.internal.codegen.binding.AnnotationExpression.createMethodName;
import static dagger.internal.codegen.binding.AnnotationExpression.getAnnotationCreatorClassName;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static dagger.internal.codegen.xprocessing.XFunSpecs.methodBuilder;
import static dagger.internal.codegen.xprocessing.XTypes.asArray;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.rewrapType;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.xprocessing.XFunSpecs;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
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
  private static final XClassName AUTO_ANNOTATION =
      XClassName.get("com.google.auto.value", "AutoAnnotation");

  @Inject
  AnnotationCreatorGenerator(XFiler filer, XProcessingEnv processingEnv) {
    super(filer, processingEnv);
  }

  @Override
  public XElement originatingElement(XTypeElement annotationType) {
    return annotationType;
  }

  @Override
  public ImmutableList<XTypeSpec> topLevelTypes(XTypeElement annotationType) {
    XClassName generatedTypeName = getAnnotationCreatorClassName(annotationType);
    XTypeSpecs.Builder annotationCreatorBuilder =
        XTypeSpecs.classBuilder(generatedTypeName)
            .addModifiers(PUBLIC, FINAL)
            .addFunction(constructorBuilder().addModifiers(PRIVATE).build());

    for (XTypeElement annotationElement : annotationsToCreate(annotationType)) {
      annotationCreatorBuilder.addFunction(buildCreateMethod(generatedTypeName, annotationElement));
    }

    return ImmutableList.of(annotationCreatorBuilder.build());
  }

  private XFunSpec buildCreateMethod(XClassName generatedTypeName, XTypeElement annotationElement) {
    String createMethodName = createMethodName(annotationElement);
    XFunSpecs.Builder createMethod =
        methodBuilder(createMethodName)
            .addAnnotation(AUTO_ANNOTATION)
            .addModifiers(PUBLIC, STATIC)
            .returns(annotationElement.getType().asTypeName());

    ImmutableList.Builder<XCodeBlock> parameters = ImmutableList.builder();
    for (XMethodElement annotationMember : annotationElement.getDeclaredMethods()) {
      String parameterName = getSimpleName(annotationMember);
      XTypeName parameterType = maybeRewrapKClass(annotationMember.getReturnType()).asTypeName();
      createMethod.addParameter(parameterName, parameterType);
      parameters.add(XCodeBlock.of("%N", parameterName));
    }

    XClassName autoAnnotationClass =
        generatedTypeName.peerClass(
            "AutoAnnotation_" + generatedTypeName.getSimpleName() + "_" + createMethodName);
    createMethod.addStatement(
        "return %L",
        XCodeBlock.ofNewInstance(
            autoAnnotationClass, "%L", makeParametersCodeBlock(parameters.build())));
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
        XType returnType = method.getReturnType();
        XTypeElement maybeAnnotationType =
            isArray(returnType)
                ? asArray(returnType).getComponentType().getTypeElement()
                : returnType.getTypeElement();
        // Return type may be null if it doesn't return a type or type is not known
        if (maybeAnnotationType != null && maybeAnnotationType.isAnnotationClass()) {
          // Ignore the return value since this method is just an accumulator method.
          nestedAnnotationElements(maybeAnnotationType, annotationElements);
        }
      }
    }
    return annotationElements;
  }

  // TODO(b/264464791): This KClass -> Class replacement can be removed once this bug is fixed.
  private XType maybeRewrapKClass(XType type) {
    return isArray(type)
        ? getProcessingEnv(type).getArrayType(maybeRewrapKClass(asArray(type).getComponentType()))
        : isTypeOf(type, XTypeNames.KCLASS) ? rewrapType(type, XTypeNames.CLASS) : type;
  }
}

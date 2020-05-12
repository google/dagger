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

package dagger.hilt.android.processor.internal.androidentrypoint;

import com.google.auto.common.MoreElements;
import com.google.auto.common.Visibility;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/** Generates an Hilt View class for the @AndroidEntryPoint annotated class. */
public final class ViewGenerator {
  private final ProcessingEnvironment env;
  private final AndroidEntryPointMetadata metadata;
  private final ClassName generatedClassName;

  public ViewGenerator(ProcessingEnvironment env, AndroidEntryPointMetadata metadata) {
    this.env = env;
    this.metadata = metadata;

    generatedClassName = metadata.generatedClassName();
  }

  // @Generated("ViewGenerator")
  // abstract class Hilt_$CLASS extends $BASE implements
  //    ComponentManagerHolder<ViewComponentManager<$CLASS_EntryPoint>> {
  //   ...
  // }
  public void generate() throws IOException {
    // Note: we do not use the Generators helper methods here because injection is called
    // from the constructor where the double-check pattern doesn't work (due to the super
    // constructor being called before fields are initialized) and because it isn't necessary
    // since the object isn't done constructing yet.

    TypeSpec.Builder builder =
        TypeSpec.classBuilder(generatedClassName.simpleName())
            .addOriginatingElement(metadata.element())
            .superclass(metadata.baseClassName())
            .addModifiers(metadata.generatedClassModifiers());

    Generators.addGeneratedBaseClassJavadoc(builder, AndroidClassNames.ANDROID_ENTRY_POINT);
    Processors.addGeneratedAnnotation(builder, env, getClass());
    Generators.copyLintAnnotations(metadata.element(), builder);

    metadata.baseElement().getTypeParameters().stream()
        .map(TypeVariableName::get)
        .forEachOrdered(builder::addTypeVariable);

    Generators.addComponentOverride(metadata, builder);

    Generators.addInjectionMethods(metadata, builder);

    ElementFilter.constructorsIn(metadata.baseElement().getEnclosedElements()).stream()
        .filter(this::isConstructorVisibleToGeneratedClass)
        .forEach(constructor -> builder.addMethod(constructorMethod(constructor)));

    JavaFile.builder(generatedClassName.packageName(), builder.build())
        .build()
        .writeTo(env.getFiler());
  }

  private boolean isConstructorVisibleToGeneratedClass(ExecutableElement constructorElement) {
    if (Visibility.ofElement(constructorElement) == Visibility.DEFAULT
        && !isInOurPackage(constructorElement)) {
      return false;
    } else if (Visibility.ofElement(constructorElement) == Visibility.PRIVATE) {
      return false;
    }

    // We extend the base class, so both protected and public methods are always accessible.
    return true;
  }

  /**
   * Returns a pass-through constructor matching the base class's provided constructorElement. The
   * generated constructor simply calls super(), then inject().
   *
   * <p>Eg
   *
   * <pre>
   *   Hilt_$CLASS(Context context, ...) {
   *     super(context, ...);
   *     inject();
   *   }
   * </pre>
   */
  private MethodSpec constructorMethod(ExecutableElement constructorElement) {
    MethodSpec.Builder constructor =
        Generators.copyConstructor(constructorElement).toBuilder();

    if (isRestrictedApiConstructor(constructorElement)) {
      // 4 parameter constructors are only available on @TargetApi(21).
      constructor.addAnnotation(
          AnnotationSpec.builder(AndroidClassNames.TARGET_API).addMember("value", "21").build());
    }

    constructor.addStatement("inject()");

    return constructor.build();
  }

  private boolean isRestrictedApiConstructor(ExecutableElement constructor) {
    if (constructor.getParameters().size() != 4) {
      return false;
    }

    List<? extends VariableElement> constructorParams = constructor.getParameters();
    for (int i = 0; i < constructorParams.size(); i++) {
      TypeMirror type = constructorParams.get(i).asType();
      Element element = env.getTypeUtils().asElement(type);
      switch (i) {
        case 0:
          if (!isFirstRestrictedParameter(element)) {
            return false;
          }
          break;
        case 1:
          if (!isSecondRestrictedParameter(element)) {
            return false;
          }
          break;
        case 2:
          if (!isThirdRestrictedParameter(type)) {
            return false;
          }
          break;
        case 3:
          if (!isFourthRestrictedParameter(type)) {
            return false;
          }
          break;
        default:
          return false;
      }
    }

    return true;
  }

  private static boolean isFourthRestrictedParameter(TypeMirror type) {
    return type.getKind().isPrimitive()
        && Processors.getPrimitiveType(type).getKind() == TypeKind.INT;
  }

  private static boolean isThirdRestrictedParameter(TypeMirror type) {
    return type.getKind().isPrimitive()
        && Processors.getPrimitiveType(type).getKind() == TypeKind.INT;
  }

  private static boolean isSecondRestrictedParameter(Element element) {
    return element instanceof TypeElement
        && Processors.isAssignableFrom(
            MoreElements.asType(element), AndroidClassNames.ATTRIBUTE_SET);
  }

  private static boolean isFirstRestrictedParameter(Element element) {
    return element instanceof TypeElement
        && Processors.isAssignableFrom(MoreElements.asType(element), AndroidClassNames.CONTEXT);
  }

  private boolean isInOurPackage(ExecutableElement constructorElement) {
    return MoreElements.getPackage(constructorElement)
        .equals(MoreElements.getPackage(metadata.element()));
  }
}

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

import static androidx.room.compiler.processing.XTypeKt.isInt;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeParameterElement;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.Processors;
import java.util.List;

/** Generates an Hilt View class for the @AndroidEntryPoint annotated class. */
public final class ViewGenerator {
  private final XProcessingEnv env;
  private final AndroidEntryPointMetadata metadata;
  private final ClassName generatedClassName;

  public ViewGenerator(XProcessingEnv env, AndroidEntryPointMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
    generatedClassName = metadata.generatedClassName();
  }

  // @Generated("ViewGenerator")
  // abstract class Hilt_$CLASS extends $BASE implements
  //    ComponentManagerHolder<ViewComponentManager<$CLASS_EntryPoint>> {
  //   ...
  // }
  public void generate() {
    // Note: we do not use the Generators helper methods here because injection is called
    // from the constructor where the double-check pattern doesn't work (due to the super
    // constructor being called before fields are initialized) and because it isn't necessary
    // since the object isn't done constructing yet.

    TypeSpec.Builder builder =
        TypeSpec.classBuilder(generatedClassName.simpleName())
            .superclass(metadata.baseClassName())
            .addModifiers(metadata.generatedClassModifiers());

    JavaPoetExtKt.addOriginatingElement(builder, metadata.element());
    Generators.addGeneratedBaseClassJavadoc(builder, AndroidClassNames.ANDROID_ENTRY_POINT);
    Processors.addGeneratedAnnotation(builder, env, getClass());
    Generators.copyLintAnnotations(metadata.element(), builder);
    Generators.copySuppressAnnotations(metadata.element(), builder);

    metadata.baseElement().getTypeParameters().stream()
        .map(XTypeParameterElement::getTypeVariableName)
        .forEachOrdered(builder::addTypeVariable);

    Generators.addComponentOverride(metadata, builder);
    Generators.addInjectionMethods(metadata, builder);

    metadata.baseElement().getConstructors().stream()
        .filter(constructor -> Generators.isConstructorVisibleToSubclass(
            constructor, metadata.element()))
        .map(this::constructorMethod)
        .forEach(builder::addMethod);

    env.getFiler()
        .write(
            JavaFile.builder(generatedClassName.packageName(), builder.build()).build(),
            XFiler.Mode.Isolating);
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
   *     if (!isInEditMode()) {
   *       inject();
   *     }
   *   }
   * </pre>
   */
  private MethodSpec constructorMethod(XConstructorElement constructor) {
    MethodSpec.Builder builder = Generators.copyConstructor(constructor).toBuilder();

    // TODO(b/210544481): Once this bug is fixed we should require that the user adds this
    // annotation to their constructor and we'll propagate it from there rather than trying to
    // guess whether this needs @TargetApi from the signature. This check is a bit flawed. For
    // example, the user could write a 5 parameter constructor that calls the restricted 4 parameter
    // constructor and we would miss adding @TargetApi to it.
    if (isRestrictedApiConstructor(constructor)) {
      // 4 parameter constructors are only available on @TargetApi(21).
      builder.addAnnotation(
          AnnotationSpec.builder(AndroidClassNames.TARGET_API).addMember("value", "21").build());
    }

    builder.beginControlFlow("if(!isInEditMode())")
        .addStatement("inject()")
        .endControlFlow();

    return builder.build();
  }

  private boolean isRestrictedApiConstructor(XConstructorElement constructor) {
    if (constructor.getParameters().size() != 4) {
      return false;
    }

    List<XExecutableParameterElement> constructorParams = constructor.getParameters();
    for (int i = 0; i < constructorParams.size(); i++) {
      XType type = constructorParams.get(i).getType();
      switch (i) {
        case 0:
          if (!isFirstRestrictedParameter(type)) {
            return false;
          }
          break;
        case 1:
          if (!isSecondRestrictedParameter(type)) {
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

  private static boolean isFourthRestrictedParameter(XType type) {
    return isPrimitive(type) && isInt(type);
  }

  private static boolean isThirdRestrictedParameter(XType type) {
    return isPrimitive(type) && isInt(type);
  }

  private static boolean isSecondRestrictedParameter(XType type) {
    return isDeclared(type)
        && Processors.isAssignableFrom(type.getTypeElement(), AndroidClassNames.ATTRIBUTE_SET);
  }

  private static boolean isFirstRestrictedParameter(XType type) {
    return isDeclared(type)
        && Processors.isAssignableFrom(type.getTypeElement(), AndroidClassNames.CONTEXT);
  }
}

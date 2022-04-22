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

import static dagger.hilt.processor.internal.ElementDescriptors.getMethodDescriptor;

import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.android.processor.internal.MoreTypes;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;

/** Generates an Hilt BroadcastReceiver class for the @AndroidEntryPoint annotated class. */
public final class BroadcastReceiverGenerator {

  private static final String ON_RECEIVE_DESCRIPTOR =
      "onReceive(Landroid/content/Context;Landroid/content/Intent;)V";

  private final ProcessingEnvironment env;
  private final AndroidEntryPointMetadata metadata;
  private final ClassName generatedClassName;

  public BroadcastReceiverGenerator(
      ProcessingEnvironment env, AndroidEntryPointMetadata metadata) {
    this.env = env;
    this.metadata = metadata;

    generatedClassName = metadata.generatedClassName();
  }

  // @Generated("BroadcastReceiverGenerator")
  // abstract class Hilt_$CLASS extends $BASE {
  //   ...
  // }
  public void generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(generatedClassName.simpleName())
            .addOriginatingElement(metadata.element())
            .superclass(metadata.baseClassName())
            .addModifiers(metadata.generatedClassModifiers())
            .addMethod(onReceiveMethod());

    Generators.addGeneratedBaseClassJavadoc(builder, AndroidClassNames.ANDROID_ENTRY_POINT);
    Processors.addGeneratedAnnotation(builder, env, getClass());
    Generators.copyConstructors(metadata.baseElement(), builder);

    metadata.baseElement().getTypeParameters().stream()
        .map(TypeVariableName::get)
        .forEachOrdered(builder::addTypeVariable);

    Generators.addInjectionMethods(metadata, builder);
    Generators.copyLintAnnotations(metadata.element(), builder);
    Generators.copySuppressAnnotations(metadata.element(), builder);

    // Add an unused field used as a marker to let the bytecode injector know this receiver will
    // need to be injected with a super.onReceive call. This is only necessary if no concrete
    // onReceive call is implemented in any of the super classes.
    if (metadata.requiresBytecodeInjection() && !isOnReceiveImplemented(metadata.baseElement())) {
      builder.addField(
          FieldSpec.builder(
                  TypeName.BOOLEAN,
                  "onReceiveBytecodeInjectionMarker",
                  Modifier.PRIVATE,
                  Modifier.FINAL)
              .initializer("false")
              .build());
    }

    JavaFile.builder(generatedClassName.packageName(),
        builder.build()).build().writeTo(env.getFiler());
  }

  private static boolean isOnReceiveImplemented(TypeElement typeElement) {
    boolean isImplemented =
        ElementFilter.methodsIn(typeElement.getEnclosedElements()).stream()
            .anyMatch(
                methodElement ->
                    getMethodDescriptor(methodElement).equals(ON_RECEIVE_DESCRIPTOR)
                        && !methodElement.getModifiers().contains(Modifier.ABSTRACT));
    if (isImplemented) {
      return true;
    } else if (typeElement.getSuperclass().getKind() != TypeKind.NONE) {
      return isOnReceiveImplemented(MoreTypes.asTypeElement(typeElement.getSuperclass()));
    } else {
      return false;
    }
  }

  // @Override
  // public void onReceive(Context context, Intent intent) {
  //   inject(context);
  //   super.onReceive();
  // }
  private MethodSpec onReceiveMethod() throws IOException {
    MethodSpec.Builder method =
        MethodSpec.methodBuilder("onReceive")
            .addAnnotation(Override.class)
            .addAnnotation(AndroidClassNames.CALL_SUPER)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(ParameterSpec.builder(AndroidClassNames.CONTEXT, "context").build())
            .addParameter(ParameterSpec.builder(AndroidClassNames.INTENT, "intent").build())
            .addStatement("inject(context)");

    if (metadata.overridesAndroidEntryPointClass()) {
      // We directly call super.onReceive here because we know Hilt base classes have a
      // non-abstract onReceive method. However, because the Hilt base class may not be generated
      // already we cannot fall down to the below logic to find it.
      method.addStatement("super.onReceive(context, intent)");
    } else {
      // Get the onReceive method element from BroadcastReceiver.
      ExecutableElement onReceiveElement =
          Iterables.getOnlyElement(
              MoreTypes.findMethods(
                  env.getElementUtils()
                      .getTypeElement(AndroidClassNames.BROADCAST_RECEIVER.toString()),
                  "onReceive"));

      // If the base class or one of its super classes implements onReceive, call super.onReceive()
      MoreTypes.findInheritedMethod(env.getTypeUtils(), metadata.baseElement(), onReceiveElement)
          .filter(onReceive -> !onReceive.getModifiers().contains(Modifier.ABSTRACT))
          .ifPresent(onReceive -> method.addStatement("super.onReceive(context, intent)"));
    }

    return method.build();
  }
}

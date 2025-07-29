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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XTypeParameterElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XExecutableTypes;
import java.io.IOException;
import java.util.Optional;
import javax.lang.model.element.Modifier;

/** Generates an Hilt BroadcastReceiver class for the @AndroidEntryPoint annotated class. */
public final class BroadcastReceiverGenerator {
  private static final String ON_RECEIVE_DESCRIPTOR =
      "onReceive(Landroid/content/Context;Landroid/content/Intent;)V";

  private final XProcessingEnv env;
  private final AndroidEntryPointMetadata metadata;
  private final ClassName generatedClassName;

  public BroadcastReceiverGenerator(XProcessingEnv env, AndroidEntryPointMetadata metadata) {
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
            .superclass(metadata.baseClassName())
            .addModifiers(metadata.generatedClassModifiers())
            .addMethod(onReceiveMethod());

    // Add an annotation used as a marker to let the bytecode injector know this receiver
    // will need to be injected with a super.onReceive call. This is only necessary if no concrete
    // onReceive call is implemented in any of the super classes.
    if (metadata.requiresBytecodeInjection() && !isOnReceiveImplemented(metadata.baseElement())) {
      builder.addAnnotation(ClassNames.ON_RECEIVE_BYTECODE_INJECTION_MARKER);
    }

    JavaPoetExtKt.addOriginatingElement(builder, metadata.element());
    Generators.addGeneratedBaseClassJavadoc(builder, AndroidClassNames.ANDROID_ENTRY_POINT);
    Processors.addGeneratedAnnotation(builder, env, getClass());
    Generators.copyConstructors(metadata.baseElement(), builder, metadata.element());

    metadata.baseElement().getTypeParameters().stream()
        .map(XTypeParameterElement::getTypeVariableName)
        .forEachOrdered(builder::addTypeVariable);

    Generators.addInjectionMethods(metadata, builder);
    Generators.copyLintAnnotations(metadata.element(), builder);
    Generators.copySuppressAnnotations(metadata.element(), builder);

    env.getFiler()
        .write(
            JavaFile.builder(generatedClassName.packageName(), builder.build()).build(),
            XFiler.Mode.Isolating);
  }

  private static boolean isOnReceiveImplemented(XTypeElement typeElement) {
    boolean isImplemented =
        typeElement.getDeclaredMethods().stream()
            .filter(method -> !method.isAbstract())
            .anyMatch(method -> method.getJvmDescriptor().equals(ON_RECEIVE_DESCRIPTOR));
    if (isImplemented) {
      return true;
    } else if (typeElement.getSuperClass() != null) {
      return isOnReceiveImplemented(typeElement.getSuperClass().getTypeElement());
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
      XMethodElement onReceiveMethod =
          getOnlyElement(
              findMethodsByName(
                  env.requireTypeElement(AndroidClassNames.BROADCAST_RECEIVER), "onReceive"));

      // If the base class or one of its super classes implements onReceive, call super.onReceive()
      findMethodBySubsignature(metadata.baseElement(), onReceiveMethod)
          .filter(onReceive -> !onReceive.isAbstract())
          .ifPresent(onReceive -> method.addStatement("super.onReceive(context, intent)"));
    }
    return method.build();
  }

  private Optional<XMethodElement> findMethodBySubsignature(
      XTypeElement typeElement, XMethodElement method) {
    String methodName = getSimpleName(method);
    XType currentType = typeElement.getType();
    Optional<XMethodElement> match = Optional.empty();
    while (!match.isPresent() && currentType != null) {
      match =
          findMethodsByName(currentType.getTypeElement(), methodName).stream()
              .filter(m -> XExecutableTypes.isSubsignature(m, method))
              .collect(toOptional());
      currentType = currentType.getTypeElement().getSuperClass();
    }
    return match;
  }

  private static ImmutableSet<XMethodElement> findMethodsByName(
      XTypeElement typeElement, String name) {
    return typeElement.getDeclaredMethods().stream()
        .filter(m -> getSimpleName(m).contentEquals(name))
        .collect(toImmutableSet());
  }
}

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

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static kotlin.streams.jdk8.StreamsKt.asStream;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XAnnotated;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeParameterElement;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.MethodSignature;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XElements;
import java.io.IOException;
import java.util.Optional;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;

/** Generates an Hilt Activity class for the @AndroidEntryPoint annotated class. */
public final class ActivityGenerator {
  private enum ActivityMethod {
    ON_CREATE(AndroidClassNames.BUNDLE),
    ON_STOP(),
    ON_DESTROY();

    @SuppressWarnings("ImmutableEnumChecker")
    private final MethodSignature signature;

    ActivityMethod(TypeName... parameterTypes) {
      String methodName = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
      this.signature = MethodSignature.of(methodName, parameterTypes);
    }
  }

  private static final FieldSpec SAVED_STATE_HANDLE_HOLDER_FIELD =
      FieldSpec.builder(AndroidClassNames.SAVED_STATE_HANDLE_HOLDER, "savedStateHandleHolder")
          .addModifiers(Modifier.PRIVATE)
          .build();

  private final XProcessingEnv env;
  private final AndroidEntryPointMetadata metadata;
  private final ClassName generatedClassName;

  public ActivityGenerator(XProcessingEnv env, AndroidEntryPointMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
    generatedClassName = metadata.generatedClassName();
  }

  // @Generated("ActivityGenerator")
  // abstract class Hilt_$CLASS extends $BASE implements ComponentManager<?> {
  //   ...
  // }
  public void generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(generatedClassName.simpleName())
            .superclass(metadata.baseClassName())
            .addModifiers(metadata.generatedClassModifiers());

    JavaPoetExtKt.addOriginatingElement(builder, metadata.element());
    Generators.addGeneratedBaseClassJavadoc(builder, AndroidClassNames.ANDROID_ENTRY_POINT);
    Processors.addGeneratedAnnotation(builder, env, getClass());

      Generators.copyConstructors(
          metadata.baseElement(),
          CodeBlock.builder().addStatement("_initHiltInternal()").build(),
          builder,
          metadata.element());
      builder.addMethod(init());
      if (!metadata.overridesAndroidEntryPointClass()) {
        builder
            .addField(SAVED_STATE_HANDLE_HOLDER_FIELD)
            .addMethod(initSavedStateHandleHolderMethod())
            .addMethod(onCreateComponentActivity())
            .addMethod(onDestroyComponentActivity());
      }

    metadata.baseElement().getTypeParameters().stream()
        .map(XTypeParameterElement::getTypeVariableName)
        .forEachOrdered(builder::addTypeVariable);

    Generators.addComponentOverride(metadata, builder);
    Generators.copyLintAnnotations(metadata.element(), builder);
    Generators.copySuppressAnnotations(metadata.element(), builder);

    Generators.addInjectionMethods(metadata, builder);

    if (Processors.isAssignableFrom(metadata.baseElement(), AndroidClassNames.COMPONENT_ACTIVITY)
        && !metadata.overridesAndroidEntryPointClass()) {
      builder.addMethod(getDefaultViewModelProviderFactory());
    }

    env.getFiler()
        .write(
            JavaFile.builder(generatedClassName.packageName(), builder.build()).build(),
            XFiler.Mode.Isolating);
  }

  // private void init() {
  //   addOnContextAvailableListener(new OnContextAvailableListener() {
  //     @Override
  //     public void onContextAvailable(Context context) {
  //       inject();
  //     }
  //   });
  // }
  private MethodSpec init() {
    return MethodSpec.methodBuilder("_initHiltInternal")
        .addModifiers(Modifier.PRIVATE)
        .addStatement(
            "addOnContextAvailableListener($L)",
            TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(AndroidClassNames.ON_CONTEXT_AVAILABLE_LISTENER)
                .addMethod(
                    MethodSpec.methodBuilder("onContextAvailable")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(AndroidClassNames.CONTEXT, "context")
                        .addStatement("inject()")
                        .build())
                .build())
        .build();
  }

  // @Override
  // public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
  //   return DefaultViewModelFactories.getActivityFactory(
  //       this, super.getDefaultViewModelProviderFactory());
  // }
  private MethodSpec getDefaultViewModelProviderFactory() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("getDefaultViewModelProviderFactory")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(AndroidClassNames.VIEW_MODEL_PROVIDER_FACTORY);

    if (metadata.allowsOptionalInjection()) {
      builder
          .beginControlFlow("if (!optionalInjectParentUsesHilt(optionalInjectGetParent()))")
          .addStatement("return super.getDefaultViewModelProviderFactory()")
          .endControlFlow();
    }

    return builder
        .addStatement(
            "return $T.getActivityFactory(this, super.getDefaultViewModelProviderFactory())",
            AndroidClassNames.DEFAULT_VIEW_MODEL_FACTORIES)
        .build();
  }

  // @Override
  // public void onCreate(Bundle bundle) {
  //   super.onCreate(savedInstanceState);
  //   initSavedStateHandleHolder();
  // }
  //
  private MethodSpec onCreateComponentActivity() {
    XMethodElement nearestSuperClassMethod =
        nearestSuperClassMethod(ActivityMethod.ON_CREATE, metadata);
    if (nearestSuperClassMethod.isFinal()) {
      env.getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Do not mark onCreate as final in base Activity class, as Hilt needs to override it"
                  + " to inject SavedStateHandle.",
              nearestSuperClassMethod);
    }
    ParameterSpec.Builder parameterBuilder =
        ParameterSpec.builder(AndroidClassNames.BUNDLE, "savedInstanceState");
    MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("onCreate");
    if (isNullable(nearestSuperClassMethod.getParameters().get(0))) {
      parameterBuilder.addAnnotation(AndroidClassNames.NULLABLE);
    }
    androidEntryPointMethod(ActivityMethod.ON_CREATE, metadata)
        .ifPresent(
            method -> {
              if (method.hasAnnotation(AndroidClassNames.UI_THREAD)) {
                methodBuilder.addAnnotation(AndroidClassNames.UI_THREAD);
              }
            });
    return methodBuilder
        .addAnnotation(AndroidClassNames.CALL_SUPER)
        .addAnnotation(Override.class)
        .addModifiers(XElements.getModifiers(nearestSuperClassMethod))
        .addParameter(parameterBuilder.build())
        .addStatement("super.onCreate(savedInstanceState)")
        .addStatement("initSavedStateHandleHolder()")
        .build();
  }

  // private void initSavedStateHandleHolder() {
  //   savedStateHandleHolder = componentManager().getSavedStateHandleHolder();
  //   if (savedStateHandleHolder.isInvalid()) {
  //     savedStateHandleHolder.setExtras(getDefaultViewModelCreationExtras());
  //   }
  // }
  private MethodSpec initSavedStateHandleHolderMethod() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("initSavedStateHandleHolder")
        .addModifiers(Modifier.PRIVATE);
    if (metadata.allowsOptionalInjection()) {
      builder.beginControlFlow("if (optionalInjectParentUsesHilt(optionalInjectGetParent()))");
    }
    builder
        .addStatement(
            "$N = componentManager().getSavedStateHandleHolder()", SAVED_STATE_HANDLE_HOLDER_FIELD)
        .beginControlFlow("if ($N.isInvalid())", SAVED_STATE_HANDLE_HOLDER_FIELD)
        .addStatement(
            "$N.setExtras(getDefaultViewModelCreationExtras())", SAVED_STATE_HANDLE_HOLDER_FIELD)
        .endControlFlow();
    if (metadata.allowsOptionalInjection()) {
      builder.endControlFlow();
    }
    return builder.build();
  }

  private static boolean isNullable(XExecutableParameterElement element) {
    return hasNullableAnnotation(element) || hasNullableAnnotation(element.getType());
  }

  private static boolean hasNullableAnnotation(XAnnotated element) {
    return element.getAllAnnotations().stream()
        .anyMatch(annotation -> annotation.getClassName().simpleName().equals("Nullable"));
  }

  // @Override
  // public void onDestroy() {
  //   super.onDestroy();
  //   if (savedStateHandleHolder != null) {
  //     savedStateHandleHolder.clear();
  //   }
  // }
  private MethodSpec onDestroyComponentActivity() {
    XMethodElement nearestSuperClassMethod =
        nearestSuperClassMethod(ActivityMethod.ON_DESTROY, metadata);
    if (nearestSuperClassMethod.isFinal()) {
      env.getMessager()
          .printMessage(
              Diagnostic.Kind.ERROR,
              "Do not mark onDestroy as final in base Activity class, as Hilt needs to override it"
                  + " to clean up SavedStateHandle.",
              nearestSuperClassMethod);
    }
    return MethodSpec.methodBuilder("onDestroy")
        .addAnnotation(Override.class)
        .addModifiers(XElements.getModifiers(nearestSuperClassMethod))
        .addStatement("super.onDestroy()")
        .beginControlFlow("if ($N != null)", SAVED_STATE_HANDLE_HOLDER_FIELD)
        .addStatement("$N.clear()", SAVED_STATE_HANDLE_HOLDER_FIELD)
        .endControlFlow()
        .build();
  }

  private static Optional<XMethodElement> androidEntryPointMethod(
      ActivityMethod activityMethod, AndroidEntryPointMetadata metadata) {
    return metadata.element().getDeclaredMethods().stream()
        .filter(method -> MethodSignature.of(method).equals(activityMethod.signature))
        .collect(toOptional());
  }

  private static XMethodElement nearestSuperClassMethod(
      ActivityMethod activityMethod, AndroidEntryPointMetadata metadata) {
    ImmutableList<XMethodElement> methodOnBaseElement =
        asStream(metadata.baseElement().getAllMethods())
            .filter(method -> MethodSignature.of(method).equals(activityMethod.signature))
            .collect(toImmutableList());
    checkState(methodOnBaseElement.size() >= 1);
    return Iterables.getLast(methodOnBaseElement);
  }
}

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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

/** Generates an Hilt Activity class for the @AndroidEntryPoint annotated class. */
public final class ActivityGenerator {
  private final ProcessingEnvironment env;
  private final AndroidEntryPointMetadata metadata;
  private final ClassName generatedClassName;

  public ActivityGenerator(ProcessingEnvironment env, AndroidEntryPointMetadata metadata) {
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
            .addOriginatingElement(metadata.element())
            .superclass(metadata.baseClassName())
            .addModifiers(metadata.generatedClassModifiers());

    Generators.addGeneratedBaseClassJavadoc(builder, AndroidClassNames.ANDROID_ENTRY_POINT);
    Processors.addGeneratedAnnotation(builder, env, getClass());

      Generators.copyConstructors(
          metadata.baseElement(),
          CodeBlock.builder().addStatement("_initHiltInternal()").build(),
          builder);
      builder.addMethod(init());

    metadata.baseElement().getTypeParameters().stream()
        .map(TypeVariableName::get)
        .forEachOrdered(builder::addTypeVariable);

    Generators.addComponentOverride(metadata, builder);
    Generators.copyLintAnnotations(metadata.element(), builder);
    Generators.copySuppressAnnotations(metadata.element(), builder);

    Generators.addInjectionMethods(metadata, builder);

    if (Processors.isAssignableFrom(metadata.baseElement(), AndroidClassNames.COMPONENT_ACTIVITY)
        && !metadata.overridesAndroidEntryPointClass()) {
      builder.addMethod(getDefaultViewModelProviderFactory());
    }

    JavaFile.builder(generatedClassName.packageName(), builder.build())
        .build()
        .writeTo(env.getFiler());
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
}

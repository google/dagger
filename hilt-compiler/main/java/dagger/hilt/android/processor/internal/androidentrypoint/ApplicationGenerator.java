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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static kotlin.streams.jdk8.StreamsKt.asStream;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeParameterElement;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.ComponentNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XElements;
import java.io.IOException;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;

/** Generates an Hilt Application for an @AndroidEntryPoint app class. */
public final class ApplicationGenerator {
  private final XProcessingEnv env;
  private final AndroidEntryPointMetadata metadata;
  private final ClassName wrapperClassName;
  private final ComponentNames componentNames;

  public ApplicationGenerator(XProcessingEnv env, AndroidEntryPointMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
    this.wrapperClassName = metadata.generatedClassName();
    this.componentNames = ComponentNames.withoutRenaming();
  }

  // @Generated("ApplicationGenerator")
  // abstract class Hilt_$APP extends $BASE implements ComponentManager<ApplicationComponent> {
  //   ...
  // }
  public void generate() throws IOException {
    TypeSpec.Builder typeSpecBuilder =
        TypeSpec.classBuilder(wrapperClassName.simpleName())
            .superclass(metadata.baseClassName())
            .addModifiers(metadata.generatedClassModifiers())
            .addField(injectedField());

    JavaPoetExtKt.addOriginatingElement(typeSpecBuilder, metadata.element());

    typeSpecBuilder
        .addField(componentManagerField())
        .addMethod(componentManagerMethod());

    Generators.addGeneratedBaseClassJavadoc(typeSpecBuilder, AndroidClassNames.HILT_ANDROID_APP);
    Processors.addGeneratedAnnotation(typeSpecBuilder, env, getClass());

    metadata.baseElement().getTypeParameters().stream()
        .map(XTypeParameterElement::getTypeVariableName)
        .forEachOrdered(typeSpecBuilder::addTypeVariable);

    Generators.copyLintAnnotations(metadata.element(), typeSpecBuilder);
    Generators.copySuppressAnnotations(metadata.element(), typeSpecBuilder);
    Generators.addComponentOverride(metadata, typeSpecBuilder);

    if (hasCustomInject()) {
      typeSpecBuilder.addSuperinterface(AndroidClassNames.HAS_CUSTOM_INJECT);
        typeSpecBuilder.addMethod(customInjectMethod()).addMethod(injectionMethod());
    } else {
        typeSpecBuilder.addMethod(onCreateMethod()).addMethod(injectionMethod());
    }

    env.getFiler()
        .write(
            JavaFile.builder(metadata.elementClassName().packageName(), typeSpecBuilder.build())
                .build(),
            XFiler.Mode.Isolating);
  }

  private boolean hasCustomInject() {
    boolean hasCustomInject = metadata.element().hasAnnotation(AndroidClassNames.CUSTOM_INJECT);
    if (hasCustomInject) {
      // Check that the Hilt base class does not already define a customInject implementation.
      ImmutableSet<XMethodElement> customInjectMethods =
          Stream.concat(
                  metadata.element().getDeclaredMethods().stream(),
                  asStream(metadata.baseElement().getAllMethods()))
              .filter(method -> getSimpleName(method).contentEquals("customInject"))
              .filter(method -> method.getParameters().isEmpty())
              .collect(toImmutableSet());

      for (XMethodElement customInjectMethod : customInjectMethods) {
        ProcessorErrors.checkState(
            customInjectMethod.isAbstract() && customInjectMethod.isProtected(),
            customInjectMethod,
            "%s#%s, must have modifiers `abstract` and `protected` when using @CustomInject.",
            XElements.toStableString(customInjectMethod.getEnclosingElement()),
            XElements.toStableString(customInjectMethod));
      }
    }
    return hasCustomInject;
  }

  // private final ApplicationComponentManager<ApplicationComponent> componentManager =
  //     new ApplicationComponentManager(/* creatorType */);
  private FieldSpec componentManagerField() {
    ParameterSpec managerParam = metadata.componentManagerParam();
    return FieldSpec.builder(managerParam.type, managerParam.name)
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
        .initializer("new $T($L)", AndroidClassNames.APPLICATION_COMPONENT_MANAGER, creatorType())
        .build();
  }

  // protected ApplicationComponentManager<ApplicationComponent> componentManager() {
  //   return componentManager();
  // }
  private MethodSpec componentManagerMethod() {
    return MethodSpec.methodBuilder("componentManager")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .returns(metadata.componentManagerParam().type)
        .addStatement("return $N", metadata.componentManagerParam())
        .build();
  }

  // new Supplier<ApplicationComponent>() {
  //   @Override
  //   public ApplicationComponent get() {
  //     return DaggerApplicationComponent.builder()
  //         .applicationContextModule(new ApplicationContextModule(Hilt_$APP.this))
  //         .build();
  //   }
  // }
  private TypeSpec creatorType() {
    return TypeSpec.anonymousClassBuilder("")
        .addSuperinterface(AndroidClassNames.COMPONENT_SUPPLIER)
        .addMethod(
            MethodSpec.methodBuilder("get")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.OBJECT)
                .addCode(componentBuilder())
                .build())
        .build();
  }

  // return DaggerApplicationComponent.builder()
  //     .applicationContextModule(new ApplicationContextModule(Hilt_$APP.this))
  //     .build();
  private CodeBlock componentBuilder() {
    ClassName component =
        componentNames.generatedComponent(
            metadata.elementClassName(), AndroidClassNames.SINGLETON_COMPONENT);
    return CodeBlock.builder()
        .addStatement(
            "return $T.builder()$Z" + ".applicationContextModule(new $T($T.this))$Z" + ".build()",
            Processors.prepend(Processors.getEnclosedClassName(component), "Dagger"),
            AndroidClassNames.APPLICATION_CONTEXT_MODULE,
            wrapperClassName)
        .build();
  }

  // @CallSuper
  // @Override
  // public void onCreate() {
  //   hiltInternalInject();
  //   super.onCreate();
  // }
  private MethodSpec onCreateMethod() {
    return MethodSpec.methodBuilder("onCreate")
        .addAnnotation(AndroidClassNames.CALL_SUPER)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addStatement("hiltInternalInject()")
        .addStatement("super.onCreate()")
        .build();
  }

  // public void hiltInternalInject() {
  //   if (!injected) {
  //     injected = true;
  //     // This is a known unsafe cast but should be fine if the only use is
  //     // $APP extends Hilt_$APP
  //     generatedComponent().inject(($APP) this);
  //   }
  // }
  private MethodSpec injectionMethod() {
    return MethodSpec.methodBuilder("hiltInternalInject")
        .addModifiers(Modifier.PROTECTED)
        .beginControlFlow("if (!injected)")
        .addStatement("injected = true")
        .addCode(injectCodeBlock())
        .endControlFlow()
        .build();
  }

  // private boolean injected = false;
  private static FieldSpec injectedField() {
    return FieldSpec.builder(TypeName.BOOLEAN, "injected")
        .addModifiers(Modifier.PRIVATE)
        .initializer("false")
        .build();
  }

  // @Override
  // public final void customInject() {
  //   hiltInternalInject();
  // }
  private MethodSpec customInjectMethod() {
    return MethodSpec.methodBuilder("customInject")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addStatement("hiltInternalInject()")
        .build();
  }

  //   // This is a known unsafe cast but is safe in the only correct use case:
  //   // $APP extends Hilt_$APP
  //   generatedComponent().inject$APP(($APP) this);
  private CodeBlock injectCodeBlock() {
    return CodeBlock.builder()
        .add("// This is a known unsafe cast, but is safe in the only correct use case:\n")
        .add("// $T extends $T\n", metadata.elementClassName(), metadata.generatedClassName())
        .addStatement(
            "(($T) generatedComponent()).$L($L)",
            metadata.injectorClassName(),
            metadata.injectMethodName(),
            Generators.unsafeCastThisTo(metadata.elementClassName()))
        .build();
  }
}

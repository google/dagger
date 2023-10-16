/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.android.processor;

import static androidx.room.compiler.processing.JavaPoetExtKt.addOriginatingElement;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static com.squareup.javapoet.TypeSpec.interfaceBuilder;
import static dagger.android.processor.DelegateAndroidProcessor.FLAG_EXPERIMENTAL_USE_STRING_KEYS;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import dagger.internal.codegen.xprocessing.XElements;

/** Generates the implementation specified in {@code ContributesAndroidInjector}. */
final class ContributesAndroidInjectorProcessingStep extends BaseProcessingStep {
  private final AndroidInjectorDescriptor.Validator validator;
  private final XProcessingEnv processingEnv;

  ContributesAndroidInjectorProcessingStep(XProcessingEnv processingEnv) {
    this.processingEnv = processingEnv;
    this.validator = new AndroidInjectorDescriptor.Validator(processingEnv.getMessager());
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.CONTRIBUTES_ANDROID_INJECTOR);
  }

  @Override
  public void process(XElement element, ImmutableSet<ClassName> annotationNames) {
    validator.createIfValid(XElements.asMethod(element)).ifPresent(this::generate);
  }

  private void generate(AndroidInjectorDescriptor descriptor) {
    ClassName moduleName =
        descriptor
            .enclosingModule()
            .topLevelClassName()
            .peerClass(
                Joiner.on('_').join(descriptor.enclosingModule().simpleNames())
                    + "_"
                    + LOWER_CAMEL.to(UPPER_CAMEL, XElements.getSimpleName(descriptor.method())));

    String baseName = descriptor.injectedType().simpleName();
    ClassName subcomponentName = moduleName.nestedClass(baseName + "Subcomponent");
    ClassName subcomponentFactoryName = subcomponentName.nestedClass("Factory");

    TypeSpec.Builder module =
        classBuilder(moduleName)
            .addAnnotation(
                AnnotationSpec.builder(TypeNames.MODULE)
                    .addMember("subcomponents", "$T.class", subcomponentName)
                    .build())
            .addModifiers(PUBLIC, ABSTRACT)
            .addMethod(bindAndroidInjectorFactory(descriptor, subcomponentFactoryName))
            .addType(subcomponent(descriptor, subcomponentName, subcomponentFactoryName))
            .addMethod(constructorBuilder().addModifiers(PRIVATE).build());

    addOriginatingElement(module, descriptor.method());

    XTypeElement generatedAnnotation = processingEnv.findGeneratedAnnotation();
    if (generatedAnnotation != null) {
      module.addAnnotation(
          AnnotationSpec.builder(generatedAnnotation.getClassName())
              .addMember(
                  "value", "$S", ClassName.get("dagger.android.processor", "AndroidProcessor"))
              .build());
    }

    processingEnv
        .getFiler()
        .write(
            JavaFile.builder(moduleName.packageName(), module.build())
                .skipJavaLangImports(true)
                .build(),
            XFiler.Mode.Isolating);
  }

  private static boolean useStringKeys(XProcessingEnv processingEnv) {
    if (!processingEnv.getOptions().containsKey(FLAG_EXPERIMENTAL_USE_STRING_KEYS)) {
      return false;
    }
    String flagValue = processingEnv.getOptions().get(FLAG_EXPERIMENTAL_USE_STRING_KEYS);
    if (flagValue == null || Ascii.equalsIgnoreCase(flagValue, "true")) {
      return true;
    } else if (Ascii.equalsIgnoreCase(flagValue, "false")) {
      return false;
    } else {
      processingEnv
          .getMessager()
          .printMessage(
              ERROR,
              String.format(
                  "Unknown flag value: %s. %s must be set to either 'true' or 'false'.",
                  flagValue, FLAG_EXPERIMENTAL_USE_STRING_KEYS));
      return false;
    }
  }

  private MethodSpec bindAndroidInjectorFactory(
      AndroidInjectorDescriptor descriptor, ClassName subcomponentBuilderName) {
    return methodBuilder("bindAndroidInjectorFactory")
        .addAnnotation(TypeNames.BINDS)
        .addAnnotation(TypeNames.INTO_MAP)
        .addAnnotation(androidInjectorMapKey(descriptor))
        .addModifiers(ABSTRACT)
        .returns(
            ParameterizedTypeName.get(
                TypeNames.ANDROID_INJECTOR_FACTORY, WildcardTypeName.subtypeOf(TypeName.OBJECT)))
        .addParameter(subcomponentBuilderName, "builder")
        .build();
  }

  private AnnotationSpec androidInjectorMapKey(AndroidInjectorDescriptor descriptor) {
    if (useStringKeys(processingEnv)) {
      return AnnotationSpec.builder(TypeNames.ANDROID_INJECTION_KEY)
          .addMember("value", "$S", descriptor.injectedType().toString())
          .build();
    }
    return AnnotationSpec.builder(TypeNames.CLASS_KEY)
        .addMember("value", "$T.class", descriptor.injectedType())
        .build();
  }

  private TypeSpec subcomponent(
      AndroidInjectorDescriptor descriptor,
      ClassName subcomponentName,
      ClassName subcomponentFactoryName) {
    AnnotationSpec.Builder subcomponentAnnotation = AnnotationSpec.builder(TypeNames.SUBCOMPONENT);
    for (ClassName module : descriptor.modules()) {
      subcomponentAnnotation.addMember("modules", "$T.class", module);
    }

    return interfaceBuilder(subcomponentName)
        .addModifiers(PUBLIC)
        .addAnnotation(subcomponentAnnotation.build())
        .addAnnotations(descriptor.scopes())
        .addSuperinterface(
            ParameterizedTypeName.get(TypeNames.ANDROID_INJECTOR, descriptor.injectedType()))
        .addType(subcomponentFactory(descriptor, subcomponentFactoryName))
        .build();
  }

  private TypeSpec subcomponentFactory(
      AndroidInjectorDescriptor descriptor, ClassName subcomponentFactoryName) {
    return interfaceBuilder(subcomponentFactoryName)
        .addAnnotation(TypeNames.SUBCOMPONENT_FACTORY)
        .addModifiers(PUBLIC, STATIC)
        .addSuperinterface(
            ParameterizedTypeName.get(
                TypeNames.ANDROID_INJECTOR_FACTORY, descriptor.injectedType()))
        .build();
  }
}

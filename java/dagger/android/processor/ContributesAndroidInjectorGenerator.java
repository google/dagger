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

import static com.google.auto.common.GeneratedAnnotationSpecs.generatedAnnotationSpec;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static com.squareup.javapoet.TypeSpec.interfaceBuilder;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import dagger.Binds;
import dagger.Module;
import dagger.Subcomponent;
import dagger.android.AndroidInjectionKey;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.android.processor.AndroidInjectorDescriptor.Validator;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;

/** Generates the implementation specified in {@link ContributesAndroidInjector}. */
final class ContributesAndroidInjectorGenerator implements ProcessingStep {

  private final AndroidInjectorDescriptor.Validator validator;
  private final Filer filer;
  private final Elements elements;
  private final boolean useStringKeys;
  private final SourceVersion sourceVersion;

  ContributesAndroidInjectorGenerator(
      Validator validator,
      boolean useStringKeys,
      Filer filer,
      Elements elements,
      SourceVersion sourceVersion) {
    this.validator = validator;
    this.useStringKeys = useStringKeys;
    this.filer = filer;
    this.elements = elements;
    this.sourceVersion = sourceVersion;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(ContributesAndroidInjector.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> deferredElements = ImmutableSet.builder();
    for (ExecutableElement method : methodsIn(elementsByAnnotation.values())) {
      try {
        validator.createIfValid(method).ifPresent(this::generate);
      } catch (TypeNotPresentException e) {
        deferredElements.add(method);
      }
    }
    return deferredElements.build();
  }

  private void generate(AndroidInjectorDescriptor descriptor) {
    ClassName moduleName =
        descriptor
            .enclosingModule()
            .topLevelClassName()
            .peerClass(
                Joiner.on('_').join(descriptor.enclosingModule().simpleNames())
                    + "_"
                    + LOWER_CAMEL.to(UPPER_CAMEL, descriptor.method().getSimpleName().toString()));

    String baseName = descriptor.injectedType().simpleName();
    ClassName subcomponentName = moduleName.nestedClass(baseName + "Subcomponent");
    ClassName subcomponentFactoryName = subcomponentName.nestedClass("Factory");

    TypeSpec.Builder module =
        classBuilder(moduleName)
            .addOriginatingElement(descriptor.method())
            .addAnnotation(
                AnnotationSpec.builder(Module.class)
                    .addMember("subcomponents", "$T.class", subcomponentName)
                    .build())
            .addModifiers(PUBLIC, ABSTRACT)
            .addMethod(bindAndroidInjectorFactory(descriptor, subcomponentFactoryName))
            .addType(subcomponent(descriptor, subcomponentName, subcomponentFactoryName))
            .addMethod(constructorBuilder().addModifiers(PRIVATE).build());
    generatedAnnotationSpec(elements, sourceVersion, AndroidProcessor.class)
        .ifPresent(module::addAnnotation);

    try {
      JavaFile.builder(moduleName.packageName(), module.build())
          .skipJavaLangImports(true)
          .build()
          .writeTo(filer);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private MethodSpec bindAndroidInjectorFactory(
      AndroidInjectorDescriptor descriptor, ClassName subcomponentBuilderName) {
    return methodBuilder("bindAndroidInjectorFactory")
        .addAnnotation(Binds.class)
        .addAnnotation(IntoMap.class)
        .addAnnotation(androidInjectorMapKey(descriptor))
        .addModifiers(ABSTRACT)
        .returns(
            parameterizedTypeName(
                AndroidInjector.Factory.class,
                WildcardTypeName.subtypeOf(TypeName.OBJECT)))
        .addParameter(subcomponentBuilderName, "builder")
        .build();
  }

  private AnnotationSpec androidInjectorMapKey(AndroidInjectorDescriptor descriptor) {
    if (useStringKeys) {
      return AnnotationSpec.builder(AndroidInjectionKey.class)
          .addMember("value", "$S", descriptor.injectedType().toString())
          .build();
    }
    return AnnotationSpec.builder(ClassKey.class)
        .addMember("value", "$T.class", descriptor.injectedType())
        .build();
  }

  private TypeSpec subcomponent(
      AndroidInjectorDescriptor descriptor,
      ClassName subcomponentName,
      ClassName subcomponentFactoryName) {
    AnnotationSpec.Builder subcomponentAnnotation = AnnotationSpec.builder(Subcomponent.class);
    for (ClassName module : descriptor.modules()) {
      subcomponentAnnotation.addMember("modules", "$T.class", module);
    }

    return interfaceBuilder(subcomponentName)
        .addModifiers(PUBLIC)
        .addAnnotation(subcomponentAnnotation.build())
        .addAnnotations(descriptor.scopes())
        .addSuperinterface(parameterizedTypeName(AndroidInjector.class, descriptor.injectedType()))
        .addType(subcomponentFactory(descriptor, subcomponentFactoryName))
        .build();
  }

  private TypeSpec subcomponentFactory(
      AndroidInjectorDescriptor descriptor, ClassName subcomponentFactoryName) {
    return interfaceBuilder(subcomponentFactoryName)
        .addAnnotation(Subcomponent.Factory.class)
        .addModifiers(PUBLIC, STATIC)
        .addSuperinterface(
            parameterizedTypeName(AndroidInjector.Factory.class, descriptor.injectedType()))
        .build();
  }

  private static ParameterizedTypeName parameterizedTypeName(
      Class<?> clazz, TypeName... typeArguments) {
    return ParameterizedTypeName.get(ClassName.get(clazz), typeArguments);
  }
}

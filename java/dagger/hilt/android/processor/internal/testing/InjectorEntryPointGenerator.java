/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.testing;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/** Generates an entry point that allows injection into the given components. */
public final class InjectorEntryPointGenerator {
  /**
   * Returns an {@link InjectorEntryPointGenerator} that generates an {@link InstallIn} entry point.
   *
   * @throws if {@code installInComponents} is empty.
   */
  public static InjectorEntryPointGenerator create(
      ProcessingEnvironment env,
      TypeElement originatingElement,
      ClassName injectedName,
      ClassName injectorName,
      ClassName... installInComponents) {
    checkState(installInComponents.length > 0);
    return new InjectorEntryPointGenerator(
        env, originatingElement, injectedName, injectorName, installInComponents);
  }

  /**
   * Returns an {@link InjectorEntryPointGenerator} that generates an entry point without {@link
   * InstallIn}.
   */
  public static InjectorEntryPointGenerator createWithoutInstallIn(
      ProcessingEnvironment env,
      TypeElement originatingElement,
      ClassName injectedName,
      ClassName injectorName) {
    return new InjectorEntryPointGenerator(env, originatingElement, injectedName, injectorName);
  }

  private final ProcessingEnvironment env;
  private final TypeElement originatingElement;
  private final ClassName injectedName;
  private final ClassName injectorName;
  private final ImmutableList<ClassName> installInComponents;

  private InjectorEntryPointGenerator(
      ProcessingEnvironment env,
      TypeElement originatingElement,
      ClassName injectedName,
      ClassName injectorName,
      ClassName... installInComponents) {
    this.env = env;
    this.originatingElement = originatingElement;
    this.injectedName = injectedName;
    this.injectorName = injectorName;
    this.installInComponents = ImmutableList.copyOf(installInComponents);
  }

  // @GeneratedEntryPoint
  // @InstallIn(ApplicationComponent.class)
  // public interface Foo_Injector {
  //   void inject(Foo app);
  // }
  public void generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(injectorName)
            .addOriginatingElement(originatingElement)
            .addAnnotation(Processors.getOriginatingElementAnnotation(originatingElement))
            .addModifiers(Modifier.PUBLIC)
            .addMethod(
                MethodSpec.methodBuilder("inject")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(
                        injectedName, Processors.upperToLowerCamel(injectedName.simpleName()))
                    .build());

    if (!installInComponents.isEmpty()) {
      AnnotationSpec.Builder annotationBuilder = AnnotationSpec.builder(ClassNames.INSTALL_IN);
      installInComponents.forEach(
          component -> annotationBuilder.addMember("value", "$T.class", component));
      builder
          .addAnnotation(ClassNames.GENERATED_ENTRY_POINT)
          .addAnnotation(annotationBuilder.build());
    }

    Processors.addGeneratedAnnotation(builder, env, getClass());

    JavaFile.builder(injectorName.packageName(), builder.build()).build().writeTo(env.getFiler());
  }
}

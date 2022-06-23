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
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

/** Generates an entry point that allows for injection of the given activity */
public final class InjectorEntryPointGenerator {

  private final ProcessingEnvironment env;
  private final AndroidEntryPointMetadata metadata;

  public InjectorEntryPointGenerator(
      ProcessingEnvironment env, AndroidEntryPointMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
  }

  // @Generated("InjectorEntryPointGenerator")
  // @InstallIn({$SCOPES})
  // public interface FooActivity_GeneratedInjector {
  //   void injectFoo(FooActivity foo);
  // }
  public void generate() throws IOException {
    ClassName name = metadata.injectorClassName();
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(name.simpleName())
            .addOriginatingElement(metadata.element())
            .addAnnotation(Processors.getOriginatingElementAnnotation(metadata.element()))
            .addAnnotation(ClassNames.GENERATED_ENTRY_POINT)
            .addAnnotation(metadata.injectorInstallInAnnotation())
            .addModifiers(Modifier.PUBLIC)
            .addMethod(
                MethodSpec.methodBuilder(metadata.injectMethodName())
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(
                        metadata.elementClassName(),
                        Processors.upperToLowerCamel(metadata.elementClassName().simpleName()))
                    .build());

    Processors.addGeneratedAnnotation(builder, env, getClass());
    Generators.copyLintAnnotations(metadata.element(), builder);
    Generators.copySuppressAnnotations(metadata.element(), builder);
    JavaFile.builder(name.packageName(), builder.build()).build().writeTo(env.getFiler());
  }
}

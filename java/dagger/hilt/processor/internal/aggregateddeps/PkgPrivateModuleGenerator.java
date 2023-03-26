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

package dagger.hilt.processor.internal.aggregateddeps;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.Processors;
import dagger.internal.codegen.xprocessing.XAnnotations;
import java.io.IOException;
import javax.lang.model.element.Modifier;

/**
 * Generates a public Dagger module that includes a user's pkg-private module. This allows a user's
 * module to use pkg-private visibility to hide from external packages, but still allows Hilt to
 * install the module when the component is created in another package.
 */
final class PkgPrivateModuleGenerator {
  private final XProcessingEnv env;
  private final PkgPrivateMetadata metadata;

  PkgPrivateModuleGenerator(XProcessingEnv env, PkgPrivateMetadata metadata) {
    this.env = env;
    this.metadata = metadata;
  }

  // This method creates the following generated code for a pkg-private module, pkg.MyModule:
  //
  // package pkg; //same as module
  //
  // import dagger.Module;
  // import dagger.hilt.InstallIn;
  // import javax.annotation.Generated;
  //
  // @Generated("dagger.hilt.processor.internal.aggregateddeps.PkgPrivateModuleGenerator")
  // @InstallIn(ActivityComponent.class)
  // @Module(includes = MyModule.class)
  // public final class HiltModuleWrapper_MyModule {}
  void generate() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(metadata.generatedClassName().simpleName())
            .addAnnotation(Processors.getOriginatingElementAnnotation(metadata.getTypeElement()))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            // generated @InstallIn is exactly the same as the module being processed
            .addAnnotation(
                XAnnotations.getAnnotationSpec(metadata.getOptionalInstallInAnnotation().get()))
            .addAnnotation(
                AnnotationSpec.builder(metadata.getAnnotation())
                    .addMember("includes", "$T.class", metadata.getTypeElement().getClassName())
                    .build());
    JavaPoetExtKt.addOriginatingElement(builder, metadata.getTypeElement());

    Processors.addGeneratedAnnotation(builder, env, getClass());

    env.getFiler()
        .write(
            JavaFile.builder(metadata.generatedClassName().packageName(), builder.build()).build(),
            Mode.Isolating);
  }
}

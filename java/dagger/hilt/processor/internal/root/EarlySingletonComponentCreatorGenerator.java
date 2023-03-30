/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.processor.internal.root;

import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;

/** Generator for the {@code EarlySingletonComponentCreator}. */
final class EarlySingletonComponentCreatorGenerator {
  private static final ClassName EARLY_SINGLETON_COMPONENT_CREATOR =
      ClassName.get("dagger.hilt.android.internal.testing", "EarlySingletonComponentCreator");
  private static final ClassName EARLY_SINGLETON_COMPONENT_CREATOR_IMPL =
      ClassName.get(
          "dagger.hilt.android.internal.testing", "EarlySingletonComponentCreatorImpl");
  private static final ClassName DEFAULT_COMPONENT_IMPL =
      ClassName.get(
          "dagger.hilt.android.internal.testing.root", "DaggerDefault_HiltComponents_SingletonC");

  static void generate(XProcessingEnv env) {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(EARLY_SINGLETON_COMPONENT_CREATOR_IMPL)
            .superclass(EARLY_SINGLETON_COMPONENT_CREATOR)
            .addMethod(
                MethodSpec.methodBuilder("create")
                    .addParameter(ClassNames.APPLICATION, "application")
                    .returns(ClassName.OBJECT)
                    .addStatement(
                        "return $T.builder()\n"
                            + ".applicationContextModule(new $T(application))\n"
                            + ".build()",
                        DEFAULT_COMPONENT_IMPL,
                        ClassNames.APPLICATION_CONTEXT_MODULE)
                    .build());

    Processors.addGeneratedAnnotation(builder, env, ClassNames.ROOT_PROCESSOR.toString());

    env.getFiler()
        .write(
            JavaFile.builder(EARLY_SINGLETON_COMPONENT_CREATOR_IMPL.packageName(), builder.build())
                .build(),
            Mode.Isolating);
  }

  private EarlySingletonComponentCreatorGenerator() {}
}

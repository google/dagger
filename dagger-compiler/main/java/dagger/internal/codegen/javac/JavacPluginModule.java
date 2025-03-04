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

package dagger.internal.codegen.javac;

import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XProcessingEnv;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.util.Context;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.compileroption.CompilerOptions;
import javax.lang.model.util.Elements; // ALLOW_TYPES_ELEMENTS
import javax.lang.model.util.Types; // ALLOW_TYPES_ELEMENTS

/**
 * A module that provides a {@link XMessager}, {@link XProcessingEnv}, and {@link CompilerOptions}
 * for use in {@code javac} plugins. Requires a binding for the {@code javac} {@link Context}.
 */
@Module(includes = JavacPluginModule.BindsModule.class)
public final class JavacPluginModule {
  @Module
  interface BindsModule {
    @Binds
    CompilerOptions compilerOptions(JavacPluginCompilerOptions compilerOptions);
  }

  private final XProcessingEnv processingEnv;

  public JavacPluginModule(Context context) {
    this(JavacElements.instance(context), JavacTypes.instance(context));
  }

  public JavacPluginModule(Elements elements, Types types) {
    this.processingEnv =
        XProcessingEnv.create(new JavacPluginProcessingEnvironment(elements, types));
  }

  @Provides
  XMessager messager() {
    return processingEnv.getMessager();
  }

  @Provides
  XProcessingEnv xProcessingEnv() {
    return processingEnv;
  }
}

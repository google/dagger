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

package dagger.hilt.android.processor;

import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compiler;
import dagger.hilt.android.processor.internal.androidentrypoint.AndroidEntryPointProcessor;
import dagger.hilt.android.processor.internal.customtestapplication.CustomTestApplicationProcessor;
import dagger.hilt.android.processor.internal.uninstallmodules.UninstallModulesProcessor;
import dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsProcessor;
import dagger.hilt.processor.internal.definecomponent.DefineComponentProcessor;
import dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputProcessor;
import dagger.hilt.processor.internal.originatingelement.OriginatingElementProcessor;
import dagger.hilt.processor.internal.root.RootProcessor;
import dagger.internal.codegen.ComponentProcessor;
import dagger.testing.compile.CompilerTests;
import java.util.Arrays;
import java.util.Map;
import javax.annotation.processing.Processor;
import com.tschuchort.compiletesting.KotlinCompilation;

/** {@link Compiler} instances for testing Android Hilt. */
public final class AndroidCompilers {

  public static Compiler compiler(Processor... extraProcessors) {
    Map<Class<?>, Processor> processors =
        defaultProcessors().stream()
            .collect(toMap((Processor e) -> e.getClass(), (Processor e) -> e));

    // Adds extra processors, and allows overriding any processors of the same class.
    Arrays.stream(extraProcessors)
        .forEach(processor -> processors.put(processor.getClass(), processor));

    return CompilerTests.compiler().withProcessors(processors.values());
  }

  public static KotlinCompilation kotlinCompiler() {
    KotlinCompilation compilation = new KotlinCompilation();
    compilation.setAnnotationProcessors(defaultProcessors());
    compilation.setClasspaths(
        ImmutableList.<java.io.File>builder()
            .addAll(compilation.getClasspaths())
            .add(CompilerTests.compilerDepsJar())
            .build()
    );
    return compilation;
  }

  private static ImmutableList<Processor> defaultProcessors() {
    return ImmutableList.of(
        new AggregatedDepsProcessor(),
        new AndroidEntryPointProcessor(),
        new ComponentProcessor(),
        new DefineComponentProcessor(),
        new GeneratesRootInputProcessor(),
        new OriginatingElementProcessor(),
        new CustomTestApplicationProcessor(),
        new UninstallModulesProcessor(),
        new RootProcessor());
  }

  private AndroidCompilers() {}
}

/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal.codegen;

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;

import androidx.room.compiler.codegen.CodeLanguage;
import androidx.room.compiler.codegen.XFileSpec;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
import java.util.Map;
import java.util.Set;

/** A simple {@link XProcessingStep} that generates one source file. */
final class GeneratingProcessingStep implements XProcessingStep {
  private static final CodeLanguage CODE_LANGUAGE = CodeLanguage.JAVA;
  private final String pkgName;
  private final XTypeSpec typeSpec;

  // TODO(bcorso): Ideally we'd be able to pass in a Source rather than a XTypeSpec for tests, but
  // that would require XFiler supporting more generic writing of source files
  GeneratingProcessingStep(String pkgName, XTypeSpec typeSpec) {
    this.pkgName = pkgName;
    this.typeSpec = typeSpec;
  }

  @Override
  public final ImmutableSet<String> annotations() {
    // TODO(b/249322175): Replace this with "*" after this bug is fixed.
    // For now, we just trigger off of annotations in the other sources in the test, but ideally
    // this should support "*" similar to javac's Processor.
    return ImmutableSet.of("dagger.Component");
  }

  @Override
  public ImmutableSet<XElement> process(
      XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {
    String generatedClassName = String.format("%s.%s", pkgName, toJavaPoet(typeSpec.getName()));
    if (env.findTypeElement(generatedClassName) == null) {
      // Add an arbitrary orginating element, otherwise XProcessing will output a warning in KSP.
      XTypeSpec generatedTypeSpec =
          XTypeSpecs.toBuilder(typeSpec)
              .addOriginatingElement(env.requireTypeElement(XTypeName.ANY_OBJECT))
              .build();
      XFileSpec.builder(pkgName, generatedTypeSpec)
          .build()
          .writeTo(CODE_LANGUAGE, env.getFiler(), XFiler.Mode.Isolating);
    }
    return ImmutableSet.of();
  }

  @Override
  public void processOver(
      XProcessingEnv env, Map<String, ? extends Set<? extends XElement>> elementsByAnnotation) {}
}

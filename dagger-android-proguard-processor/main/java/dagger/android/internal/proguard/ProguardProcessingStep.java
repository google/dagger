/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.android.internal.proguard;

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.android.processor.BaseProcessingStep;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Path;

/**
 * A annotation processing step to generate dagger-android's specific proguard needs. This is only
 * intended to run over the dagger-android project itself, as the alternative is to create an
 * intermediary java_library for proguard rules to be consumed by the project.
 *
 * <p>Basic structure looks like this:
 *
 * <pre><code>
 *   resources/META-INF/com.android.tools/proguard/dagger-android.pro
 *   resources/META-INF/com.android.tools/r8/dagger-android.pro
 *   resources/META-INF/proguard/dagger-android.pro
 * </code></pre>
 */
public final class ProguardProcessingStep extends BaseProcessingStep {
  private final XProcessingEnv processingEnv;

  ProguardProcessingStep(XProcessingEnv processingEnv) {
    this.processingEnv = processingEnv;
  }

  static final XClassName GENERATE_RULES_ANNOTATION_NAME =
      XClassName.get("dagger.android.internal", "GenerateAndroidInjectionProguardRules");

  @Override
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.of(GENERATE_RULES_ANNOTATION_NAME);
  }

  @Override
  public void process(XElement element, ImmutableSet<XClassName> annotationNames) {
    XFiler filer = processingEnv.getFiler();

    String errorProneRule = "-dontwarn com.google.errorprone.annotations.**\n";
    String androidInjectionKeysRule =
        "-identifiernamestring class dagger.android.internal.AndroidInjectionKeys {\n"
            + "  java.lang.String of(java.lang.String);\n"
            + "}\n";

    writeFile(filer, "com.android.tools/proguard", errorProneRule);
    writeFile(filer, "com.android.tools/r8", errorProneRule + androidInjectionKeysRule);
    writeFile(filer, "proguard", errorProneRule);
  }

  private void writeFile(XFiler filer, String intermediatePath, String contents) {
    try (OutputStream outputStream =
            filer.writeResource(
                Path.of("META-INF/" + intermediatePath + "/dagger-android.pro"),
                ImmutableList.<XElement>of(),
                XFiler.Mode.Isolating);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
      writer.write(contents);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}

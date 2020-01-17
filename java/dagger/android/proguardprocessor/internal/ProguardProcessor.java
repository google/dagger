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

package dagger.android.proguardprocessor.internal;

import com.google.auto.service.AutoService;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import static javax.tools.StandardLocation.CLASS_OUTPUT;

/**
 * An {@linkplain Processor annotation processor} to generate dagger-android's specific proguard
 * needs. This is only intended to run over the dagger-android project itself, as the alternative
 * is to create an intermediary java_library for proguard rules to be consumed by the project.
 *
 * <p>Basic structure looks like this:
 * <pre><code>
 *   resources/META-INF/com.android.tools/proguard/dagger-android.pro
 *   resources/META-INF/com.android.tools/r8/dagger-android.pro
 *   resources/META-INF/proguard/dagger-android.pro
 * </code></pre>
 */
@SupportedAnnotationTypes("*")
@AutoService(Processor.class)
public final class ProguardProcessor extends AbstractProcessor {

  private boolean hasGenerated = false;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.errorRaised()) {
      return false;
    } else if (hasGenerated) {
      return false;
    }

    generate();
    hasGenerated = true;

    return false;
  }

  private void generate() {
    Filer filer = processingEnv.getFiler();

    StringBuilder rulesBuilder = new StringBuilder();
    rulesBuilder.append("-dontwarn com.google.errorprone.annotations.**")
        .append("\n");

    String proguardRules = rulesBuilder.toString();

    String r8Rules = rulesBuilder.append(
        "-identifiernamestring class dagger.android.internal.AndroidInjectionKeys {")
        .append("\n")
        .append("  java.lang.String of(java.lang.String);")
        .append("\n")
        .append("}")
        .append("\n")
        .toString();

    try {
      // Write META-INF/com.android.tools/proguard/dagger-android.pro
      try (Writer writer = filer.createResource(CLASS_OUTPUT,
          "",
          "META-INF/com.android.tools/proguard/dagger-android.pro")
          .openWriter()) {
        writer.write(proguardRules);
      }
      // Write META-INF/com.android.tools/r8/dagger-android.pro
      try (Writer writer = filer.createResource(CLASS_OUTPUT,
          "",
          "META-INF/com.android.tools/r8/dagger-android.pro")
          .openWriter()) {
        writer.write(r8Rules);
      }
      // Write META-INF/proguard/dagger-android.pro
      try (Writer writer = filer.createResource(CLASS_OUTPUT, "", "META-INF/dagger-android.pro")
          .openWriter()) {
        writer.write(proguardRules);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}

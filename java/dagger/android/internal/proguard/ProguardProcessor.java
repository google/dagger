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

package dagger.android.internal.proguard;

import static javax.tools.StandardLocation.CLASS_OUTPUT;

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

/**
 * An {@linkplain Processor annotation processor} to generate dagger-android's specific proguard
 * needs. This is only intended to run over the dagger-android project itself, as the alternative is
 * to create an intermediary java_library for proguard rules to be consumed by the project.
 *
 * <p>Basic structure looks like this:
 *
 * <pre><code>
 *   resources/META-INF/com.android.tools/proguard/dagger-android.pro
 *   resources/META-INF/com.android.tools/r8/dagger-android.pro
 *   resources/META-INF/proguard/dagger-android.pro
 * </code></pre>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes(ProguardProcessor.GENERATE_RULES_ANNOTATION_NAME)
public final class ProguardProcessor extends AbstractProcessor {

  static final String GENERATE_RULES_ANNOTATION_NAME =
      "dagger.android.internal.GenerateAndroidInjectionProguardRules";

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    roundEnv
        .getElementsAnnotatedWith(
            processingEnv.getElementUtils().getTypeElement(GENERATE_RULES_ANNOTATION_NAME))
        .forEach(element -> generate());

    return false;
  }

  private void generate() {
    Filer filer = processingEnv.getFiler();

    String errorProneRule = "-dontwarn com.google.errorprone.annotations.**\n";
    String androidInjectionKeysRule =
        "-identifiernamestring class dagger.android.internal.AndroidInjectionKeys {\n"
            + "  java.lang.String of(java.lang.String);\n"
            + "}\n";

    writeFile(filer, "com.android.tools/proguard", errorProneRule);
    writeFile(filer, "com.android.tools/r8", errorProneRule + androidInjectionKeysRule);
    writeFile(filer, "proguard", errorProneRule);
  }

  private static void writeFile(Filer filer, String intermediatePath, String contents) {
    try (Writer writer =
        filer
            .createResource(
                CLASS_OUTPUT, "", "META-INF/" + intermediatePath + "/dagger-android.pro")
            .openWriter()) {
      writer.write(contents);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}

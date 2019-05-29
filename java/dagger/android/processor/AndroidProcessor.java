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

package dagger.android.processor;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.googlejavaformat.java.filer.FormattingFiler;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/**
 * An {@linkplain javax.annotation.processing.Processor annotation processor} to verify usage of
 * {@code dagger.android} code.
 *
 * <p>Additionally, if {@code -Adagger.android.experimentalUseStringKeys} is passed to the
 * compilation, a file will be generated to support obfuscated injected Android types used with
 * {@code @AndroidInjectionKey}. The fact that this is generated is deliberate: not all versions of
 * ProGuard/R8 support {@code -identifiernamestring}, so we can't include a ProGuard file in the
 * dagger-android artifact Instead, we generate the file in {@code META-INF/proguard} only when
 * users enable the flag. They should only be enabling it if their shrinker supports those files,
 * and any version that does so will also support {@code -identifiernamestring}. This was added to
 * R8 in <a href="https://r8.googlesource.com/r8/+/389123dfcc11e6dda0eec31ab62e1b7eb0da80d2">May
 * 2018</a>.
 */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class AndroidProcessor extends BasicAnnotationProcessor {
  private static final String FLAG_EXPERIMENTAL_USE_STRING_KEYS =
      "dagger.android.experimentalUseStringKeys";

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    Filer filer = new FormattingFiler(processingEnv.getFiler());
    Messager messager = processingEnv.getMessager();
    Elements elements = processingEnv.getElementUtils();
    Types types = processingEnv.getTypeUtils();

    return ImmutableList.of(
        new AndroidMapKeyValidator(elements, types, messager),
        new ContributesAndroidInjectorGenerator(
            new AndroidInjectorDescriptor.Validator(messager),
            useStringKeys(),
            filer,
            elements,
            processingEnv.getSourceVersion()));
  }

  private boolean useStringKeys() {
    if (!processingEnv.getOptions().containsKey(FLAG_EXPERIMENTAL_USE_STRING_KEYS)) {
      return false;
    }
    String flagValue = processingEnv.getOptions().get(FLAG_EXPERIMENTAL_USE_STRING_KEYS);
    if (flagValue == null || Ascii.equalsIgnoreCase(flagValue, "true")) {
      return true;
    } else if (Ascii.equalsIgnoreCase(flagValue, "false")) {
      return false;
    } else {
      processingEnv
          .getMessager()
          .printMessage(
              ERROR,
              String.format(
                  "Unknown flag value: %s. %s must be set to either 'true' or 'false'.",
                  flagValue, FLAG_EXPERIMENTAL_USE_STRING_KEYS));
      return false;
    }
  }

  @Override
  protected void postRound(RoundEnvironment roundEnv) {
    if (roundEnv.processingOver() && useStringKeys()) {
      try (Writer writer = createProguardFile()){
        writer.write(
            Joiner.on("\n")
                .join(
                    "-identifiernamestring class dagger.android.internal.AndroidInjectionKeys {",
                    "  java.lang.String of(java.lang.String);",
                    "}"));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private Writer createProguardFile() throws IOException {
    return processingEnv
        .getFiler()
        .createResource(CLASS_OUTPUT, "", "META-INF/proguard/dagger.android.AndroidInjectionKeys")
        .openWriter();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return ImmutableSet.of(FLAG_EXPERIMENTAL_USE_STRING_KEYS);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}

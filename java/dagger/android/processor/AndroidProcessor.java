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

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.javac.JavacBasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
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
public final class AndroidProcessor extends JavacBasicAnnotationProcessor {
  private final DelegateAndroidProcessor delegate = new DelegateAndroidProcessor();

  @Override
  public void initialize(XProcessingEnv env) {
    delegate.initialize(env);
  }

  @Override
  public Iterable<XProcessingStep> processingSteps() {
    return delegate.processingSteps();
  }

  @Override
  public final ImmutableSet<String> getSupportedOptions() {
    return ImmutableSet.of(DelegateAndroidProcessor.FLAG_EXPERIMENTAL_USE_STRING_KEYS);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}

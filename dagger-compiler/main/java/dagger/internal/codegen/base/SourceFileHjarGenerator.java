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

package dagger.internal.codegen.base;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;

import androidx.room3.compiler.codegen.XTypeSpec;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.xprocessing.HjarGenerator;

/**
 * A source file generator that only writes the relevant code necessary for Bazel to create a
 * correct header (ABI) jar.
 */
// TODO(b/414394222): Handle KotlinPoet implementation (once header-compilation is supported).
public final class SourceFileHjarGenerator<T> extends SourceFileGenerator<T> {
  public static <T> SourceFileGenerator<T> wrap(
      SourceFileGenerator<T> delegate, XProcessingEnv processingEnv) {
    return new SourceFileHjarGenerator<>(delegate, processingEnv);
  }

  private final SourceFileGenerator<T> delegate;
  private final XProcessingEnv processingEnv;

  private SourceFileHjarGenerator(SourceFileGenerator<T> delegate, XProcessingEnv processingEnv) {
    super(delegate);
    this.delegate = delegate;
    this.processingEnv = processingEnv;
  }

  @Override
  public XElement originatingElement(T input) {
    return delegate.originatingElement(input);
  }

  @Override
  public ImmutableList<XTypeSpec> topLevelTypes(T input) {
    String packageName = closestEnclosingTypeElement(originatingElement(input)).getPackageName();
    return delegate.topLevelTypes(input).stream()
        .map(completeType -> HjarGenerator.strip(packageName, completeType, processingEnv))
        .collect(toImmutableList());
  }
}

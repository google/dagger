/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.internal.codegen.validation;

import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.processing.XTypeElement;
import dagger.internal.codegen.binding.AssistedElementsRegistry;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A registry that tracks {@code @AssistedInject} classes and {@code @AssistedFactory} interfaces
 * that have been processed in the current compilation unit.
 */
@Singleton
public final class AssistedElementsRegistryImpl implements AssistedElementsRegistry {
  private final Set<XClassName> processedAssistedFactories = new HashSet<>();
  private final Set<XClassName> processedAssistedInjectClasses = new HashSet<>();

  @Inject
  AssistedElementsRegistryImpl() {}

  /** Registers an {@code @AssistedFactory} interface as processed. */
  @Override
  public void registerAssistedFactory(XTypeElement factory) {
    processedAssistedFactories.add(factory.asClassName());
  }

  /**
   * Returns {@code true} if the {@code @AssistedFactory} interface was processed in this
   * compilation.
   */
  @Override
  public boolean isAssistedFactoryProcessed(XTypeElement factory) {
    return processedAssistedFactories.contains(factory.asClassName());
  }

  /** Registers an {@code @AssistedInject} class as processed. */
  @Override
  public void registerAssistedInjectClass(XTypeElement assistedClass) {
    processedAssistedInjectClasses.add(assistedClass.asClassName());
  }

  /**
   * Returns {@code true} if the {@code @AssistedInject} class was processed in this compilation.
   */
  @Override
  public boolean isAssistedInjectClassProcessed(XTypeElement assistedClass) {
    return processedAssistedInjectClasses.contains(assistedClass.asClassName());
  }
}

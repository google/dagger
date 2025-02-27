/*
 * Copyright (C) 2021 The Dagger Authors.
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

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import dagger.internal.codegen.xprocessing.XTypeNames;

/**
 * Helper methods for getting types of producer annotations.
 *
 * <p>Note:These should only be used for cases where the annotations don't exist in the user's code.
 * For example, all producer components implicitly have {@code @ProductionScope}, but it doesn't
 * appear in the user's code. We need to get a reference to the scope annotation though to reuse
 * classes from regular Dagger like the {@code ComponentDescriptor}.
 */
public final class ProducerAnnotations {
  private static final XClassName ANNOTATION_USAGES =
      XClassName.get("dagger.producers.internal", "AnnotationUsages");
  private static final XClassName PRODUCTION_USAGE =
      ANNOTATION_USAGES.nestedClass("ProductionUsage");
  private static final XClassName PRODUCTION_IMPLEMENTATION_USAGE =
      ANNOTATION_USAGES.nestedClass("ProductionImplementationUsage");
  private static final XClassName PRODUCTION_SCOPE_USAGE =
      ANNOTATION_USAGES.nestedClass("ProductionScopeUsage");

  /** Returns a {@link dagger.producers.internal.ProductionImplementation} qualifier. */
  // TODO(bcorso): We could probably remove the need for this if we define a new type,
  //  "ProductionImplementationExecutor", rather than binding "@ProductionImplementation Executor".
  public static XAnnotation productionImplementationQualifier(XProcessingEnv processingEnv) {
    return processingEnv.findTypeElement(PRODUCTION_IMPLEMENTATION_USAGE)
        .getAnnotation(XTypeNames.PRODUCTION_IMPLEMENTATION);
  }

  /** Returns a {@link dagger.producers.Production} qualifier. */
  // TODO(bcorso): We could probably remove the need for this. It's currently only used in
  //  "DependsOnProductionExecutorValidator", but we could implement that without this.
  public static XAnnotation productionQualifier(XProcessingEnv processingEnv) {
    return processingEnv.findTypeElement(PRODUCTION_USAGE).getAnnotation(XTypeNames.PRODUCTION);
  }

  /** Returns a {@link dagger.producers.ProductionScope} scope. */
  // TODO(bcorso): We could probably remove the need for this, but it would require changing
  //  Dagger SPI's public API. In particular, Scope should probably only require a XClassName rather
  //  than an actual annotation type.
  public static XAnnotation productionScope(XProcessingEnv processingEnv) {
    return processingEnv.findTypeElement(PRODUCTION_SCOPE_USAGE)
        .getAnnotation(XTypeNames.PRODUCTION_SCOPE);
  }

  private ProducerAnnotations() {}
}

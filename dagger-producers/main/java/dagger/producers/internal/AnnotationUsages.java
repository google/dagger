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

package dagger.producers.internal;

import dagger.producers.Production;
import dagger.producers.ProductionScope;

/**
 * This class should never be referenced directly!
 *
 * This class should only be used by Dagger's annotation processor to get access to the annotations
 * types.
 */
final class AnnotationUsages {
  @Production
  static final class ProductionUsage {}

  @ProductionImplementation
  static final class ProductionImplementationUsage {}

  @ProductionScope
  static final class ProductionScopeUsage {}

  private AnnotationUsages() {}
}

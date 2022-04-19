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

package dagger.hilt.internal.aggregatedroot;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation used to aggregate {@link dagger.hilt.android.HiltAndroidApp} and {@link
 * dagger.hilt.android.testing.HiltAndroidTest} roots.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface AggregatedRoot {
  /** Canonical name of the root class. Only used if the below package/simple names aren't set. */
  String root();

  /**
   * Package of the root class, separated because this isn't guaranteed to be distinguishable from
   * the canonical name.
   */
  String rootPackage();

  /**
   * The root class's simple names, in order of outer to inner.
   */
  String[] rootSimpleNames();

  /**
   * Deprecated. Canonical name of the originating root class. Only used if the below package/simple
   * names aren't set.
   */
  String originatingRoot();

  /**
   * Package of the originating root class, separated because this isn't guaranteed to be
   * distinguishable from the canonical name.
   */
  String originatingRootPackage();

  /**
   * The originating root class's simple names, in order of outer to inner.
   */
  String[] originatingRootSimpleNames();

  Class<?> rootAnnotation();
}

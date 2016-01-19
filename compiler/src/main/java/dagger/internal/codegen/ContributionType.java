/*
 * Copyright (C) 2015 Google, Inc.
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
package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

/**
 * Whether a binding or declaration is for a unique contribution or a map or set multibinding.
 */
enum ContributionType {
  /** Represents map bindings. */
  MAP,
  /** Represents set bindings. */
  SET,
  /** Represents a valid non-collection binding. */
  UNIQUE,
  ;

  /**
   * An object that is associated with a {@link ContributionType}.
   */
  interface HasContributionType {

    /** The contribution type of this object. */
    ContributionType contributionType();
  }

  /**
   * {@code true} if this is for a multibinding.
   */
  boolean isMultibinding() {
    return !this.equals(UNIQUE);
  }

  /** The contribution type for a given provision type. */
  static ContributionType forProvisionType(dagger.Provides.Type provisionType) {
    switch (provisionType) {
      case SET:
      case SET_VALUES:
        return SET;
      case MAP:
        return MAP;
      case UNIQUE:
        return UNIQUE;
      default:
        throw new AssertionError("Unknown provision type: " + provisionType);
    }
  }

  /** Indexes objects by their contribution type. */
  static <T extends HasContributionType>
      ImmutableListMultimap<ContributionType, T> indexByContributionType(
          Iterable<T> haveContributionTypes) {
    return Multimaps.index(
        haveContributionTypes,
        new Function<HasContributionType, ContributionType>() {
          @Override
          public ContributionType apply(HasContributionType hasContributionType) {
            return hasContributionType.contributionType();
          }
        });
  }
}

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
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.producers.Produces;
import javax.lang.model.element.ExecutableElement;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Whether a binding or declaration is for a unique contribution or a map or set multibinding.
 */
enum ContributionType {
  /** Represents map bindings. */
  MAP,
  /** Represents set bindings. */
  SET,
  /** Represents set values bindings. */
  SET_VALUES,
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
  private static ContributionType forProvisionType(Provides.Type provisionType) {
    switch (provisionType) {
      case SET:
        return SET;
      case SET_VALUES:
        return SET_VALUES;
      case MAP:
        return MAP;
      case UNIQUE:
        return UNIQUE;
      default:
        throw new AssertionError("Unknown provision type: " + provisionType);
    }
  }

  private static ContributionType forProductionType(Produces.Type productionType) {
    switch (productionType) {
      case SET:
        return SET;
      case SET_VALUES:
        return SET_VALUES;
      case MAP:
        return MAP;
      case UNIQUE:
        return UNIQUE;
      default:
        throw new AssertionError("Unknown production type: " + productionType);
    }
  }

  /**
   * The contribution type from a binding method annotations. Presumes a well-formed binding method
   * (only one of @IntoSet, @IntoMap, @ElementsIntoSet, @Provides.type or @Produces.type. {@link
   * ProvidesMethodValidator} and {@link ProducesMethodValidator} validate correctness on their own.
   */
  static ContributionType fromBindingMethod(ExecutableElement method) {
    checkArgument(
        isAnnotationPresent(method, Provides.class)
            || isAnnotationPresent(method, Produces.class));
    if (isAnnotationPresent(method, IntoMap.class)) {
      return ContributionType.MAP;
    } else if (isAnnotationPresent(method, IntoSet.class)) {
      return ContributionType.SET;
    } else if (isAnnotationPresent(method, ElementsIntoSet.class)) {
      return ContributionType.SET_VALUES;
    }

    if (isAnnotationPresent(method, Provides.class)) {
      return forProvisionType(method.getAnnotation(Provides.class).type());
    } else if (isAnnotationPresent(method, Produces.class)) {
      return forProductionType(method.getAnnotation(Produces.class).type());
    } else {
      throw new AssertionError();
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

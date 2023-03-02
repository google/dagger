/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.internal.codegen.model;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Joiner;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XElements;
import java.util.Optional;

/**
 * A {@linkplain DaggerType type} and an optional {@linkplain javax.inject.Qualifier qualifier} that
 * is the lookup key for a binding.
 */
@AutoValue
public abstract class Key {
  /**
   * A {@link javax.inject.Qualifier} annotation that provides a unique namespace prefix for the
   * type of this key.
   */
  public abstract Optional<DaggerAnnotation> qualifier();

  /** The type represented by this key. */
  public abstract DaggerType type();

  /**
   * Distinguishes keys for multibinding contributions that share a {@link #type()} and {@link
   * #qualifier()}.
   *
   * <p>Each multibound map and set has a synthetic multibinding that depends on the specific
   * contributions to that map or set using keys that identify those multibinding contributions.
   *
   * <p>Absent except for multibinding contributions.
   */
  public abstract Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier();

  /** Returns a {@link Builder} that inherits the properties of this key. */
  abstract Builder toBuilder();

  /** Returns a copy of this key with the type replaced with the given type. */
  public Key withType(DaggerType newType) {
    return toBuilder().type(newType).build();
  }

  /**
   * Returns a copy of this key with the multibinding contribution identifier replaced with the
   * given multibinding contribution identifier.
   */
  public Key withMultibindingContributionIdentifier(
      DaggerTypeElement contributingModule, DaggerExecutableElement bindingMethod) {
    return toBuilder()
        .multibindingContributionIdentifier(contributingModule, bindingMethod)
        .build();
  }

  /** Returns a copy of this key with the multibinding contribution identifier, if any, removed. */
  public Key withoutMultibindingContributionIdentifier() {
    return toBuilder().multibindingContributionIdentifier(Optional.empty()).build();
  }

  // The main hashCode/equality bottleneck is in MoreTypes.equivalence(). It's possible that we can
  // avoid this by tuning that method. Perhaps we can also avoid the issue entirely by interning all
  // Keys
  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object o);

  @Override
  public final String toString() {
    return Joiner.on(' ')
        .skipNulls()
        .join(
            qualifier()
                .map(DaggerAnnotation::xprocessing)
                .map(XAnnotations::toStableString)
                .orElse(null),
            type(),
            multibindingContributionIdentifier().orElse(null));
  }

  /** Returns a builder for {@link Key}s. */
  public static Builder builder(DaggerType type) {
    return new AutoValue_Key.Builder().type(type);
  }

  /** A builder for {@link Key}s. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder type(DaggerType type);

    public abstract Builder qualifier(Optional<DaggerAnnotation> qualifier);

    public abstract Builder qualifier(DaggerAnnotation qualifier);

    public final Builder multibindingContributionIdentifier(
        DaggerTypeElement contributingModule, DaggerExecutableElement bindingMethod) {
      return multibindingContributionIdentifier(
          Optional.of(
              MultibindingContributionIdentifier.create(contributingModule, bindingMethod)));
    }

    abstract Builder multibindingContributionIdentifier(
        Optional<MultibindingContributionIdentifier> identifier);

    public abstract Key build();
  }

  /**
   * An object that identifies a multibinding contribution method and the module class that
   * contributes it to the graph.
   *
   * @see #multibindingContributionIdentifier()
   */
  @AutoValue
  public abstract static class MultibindingContributionIdentifier {
    private static MultibindingContributionIdentifier create(
        DaggerTypeElement contributingModule, DaggerExecutableElement bindingMethod) {
      return new AutoValue_Key_MultibindingContributionIdentifier(
          contributingModule, bindingMethod);
    }

    /** Returns the module containing the multibinding method. */
    public abstract DaggerTypeElement contributingModule();

    /** Returns the multibinding method that defines teh multibinding contribution. */
    public abstract DaggerExecutableElement bindingMethod();

    /**
     * {@inheritDoc}
     *
     * <p>The returned string is human-readable and distinguishes the keys in the same way as the
     * whole object.
     */
    @Override
    public String toString() {
      return String.format(
          "%s#%s",
          contributingModule().xprocessing().getQualifiedName(),
          XElements.getSimpleName(bindingMethod().xprocessing()));
    }
  }
}

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

package dagger.spi.model;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Joiner;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

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
  public abstract Builder toBuilder();

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
            qualifier().map(MoreAnnotationMirrors::toStableString).orElse(null),
            type(),
            multibindingContributionIdentifier().orElse(null));
  }

  /** Returns a builder for {@link Key}s. */
  public static Builder builder(DaggerType type) {
    return new AutoValue_Key.Builder().type(type);
  }

  /** A builder for {@link Key}s. */
  @CanIgnoreReturnValue
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder type(DaggerType type);

    public abstract Builder qualifier(Optional<DaggerAnnotation> qualifier);

    public abstract Builder qualifier(DaggerAnnotation qualifier);

    public abstract Builder multibindingContributionIdentifier(
        Optional<MultibindingContributionIdentifier> identifier);

    public abstract Builder multibindingContributionIdentifier(
        MultibindingContributionIdentifier identifier);

    @CheckReturnValue
    public abstract Key build();
  }

  /**
   * An object that identifies a multibinding contribution method and the module class that
   * contributes it to the graph.
   *
   * @see #multibindingContributionIdentifier()
   */
  public static final class MultibindingContributionIdentifier {
    private final String module;
    private final String bindingElement;

    /**
     * @deprecated This is only meant to be called from code in {@code dagger.internal.codegen}.
     * It is not part of a specified API and may change at any point.
     */
    @Deprecated
    public MultibindingContributionIdentifier(
        // TODO(ronshapiro): reverse the order of these parameters
        ExecutableElement bindingMethod, TypeElement contributingModule) {
      this(
          bindingMethod.getSimpleName().toString(),
          contributingModule.getQualifiedName().toString());
    }

    // TODO(ronshapiro,dpb): create KeyProxies so that these constructors don't need to be public.
    @Deprecated
    public MultibindingContributionIdentifier(String bindingElement, String module) {
      this.module = module;
      this.bindingElement = bindingElement;
    }

    /**
     * @deprecated This is only meant to be called from code in {@code dagger.internal.codegen}.
     * It is not part of a specified API and may change at any point.
     */
    @Deprecated
    public String module() {
      return module;
    }

    /**
     * @deprecated This is only meant to be called from code in {@code dagger.internal.codegen}.
     * It is not part of a specified API and may change at any point.
     */
    @Deprecated
    public String bindingElement() {
      return bindingElement;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned string is human-readable and distinguishes the keys in the same way as the
     * whole object.
     */
    @Override
    public String toString() {
      return String.format("%s#%s", module, bindingElement);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof MultibindingContributionIdentifier) {
        MultibindingContributionIdentifier other = (MultibindingContributionIdentifier) obj;
        return module.equals(other.module) && bindingElement.equals(other.bindingElement);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(module, bindingElement);
    }
  }
}

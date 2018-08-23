/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Joiner;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * A {@linkplain TypeMirror type} and an optional {@linkplain javax.inject.Qualifier qualifier} that
 * is the lookup key for a binding.
 */
@AutoValue
public abstract class Key {
  /**
   * A {@link javax.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   */
  public final Optional<AnnotationMirror> qualifier() {
    return wrappedQualifier().map(Wrapper::get);
  }

  /**
   * The type represented by this key.
   */
  public final TypeMirror type() {
    return wrappedType().get();
  }

  /**
   * A {@link javax.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   *
   * Despite documentation in {@link AnnotationMirror}, equals and hashCode aren't implemented
   * to represent logical equality, so {@link AnnotationMirrors#equivalence()}
   * provides this facility.
   */
  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedQualifier();

  /**
   * The type represented by this key.
   *
   * As documented in {@link TypeMirror}, equals and hashCode aren't implemented to represent
   * logical equality, so {@link MoreTypes#equivalence()} wraps this type.
   */
  abstract Equivalence.Wrapper<TypeMirror> wrappedType();

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

  @Memoized
  @Override
  public abstract int hashCode();

  // We are modifying what would be the AutoValue-generated equals() method to stick in a check for
  // hashCode() equality before other properties. Profiling indicated that Key equality is called in
  // numerous places and it cannot be @Memoized, but hashCode() can be. Because so many other value
  // types use Key, their equality is dependant on Key's. Inserting the check removed Key.equals()
  // from the profile.
  // The main equality bottleneck in calculating the equality is in MoreTypes.equivalence()'s
  // equality checker. It's possible that we can avoid this by tuning that method. Perhaps we can
  // also avoid the issue entirely by interning all Keys
  // TODO(ronshapiro): consider creating an AutoValue extension that can generate this code on its
  // own
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Key) {
      Key that = (Key) o;
      return (this.hashCode() == that.hashCode())
          && (this.wrappedQualifier().equals(that.wrappedQualifier()))
          && (this.wrappedType().equals(that.wrappedType()))
          && (this.multibindingContributionIdentifier()
              .equals(that.multibindingContributionIdentifier()));
    }
    return false;
  }

  @Override
  public final String toString() {
    return Joiner.on(' ')
        .skipNulls()
        .join(qualifier().orElse(null), type(), multibindingContributionIdentifier().orElse(null));
  }

  /** Returns a builder for {@link Key}s. */
  public static Builder builder(TypeMirror type) {
    return new AutoValue_Key.Builder().type(type);
  }

  /** A builder for {@link Key}s. */
  @AutoValue.Builder
  public abstract static class Builder {
    abstract Builder wrappedType(Equivalence.Wrapper<TypeMirror> wrappedType);

    public final Builder type(TypeMirror type) {
      return wrappedType(MoreTypes.equivalence().wrap(checkNotNull(type)));
    }

    abstract Builder wrappedQualifier(
        Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedQualifier);

    abstract Builder wrappedQualifier(Equivalence.Wrapper<AnnotationMirror> wrappedQualifier);

    public final Builder qualifier(AnnotationMirror qualifier) {
      return wrappedQualifier(AnnotationMirrors.equivalence().wrap(checkNotNull(qualifier)));
    }

    public final Builder qualifier(Optional<AnnotationMirror> qualifier) {
      return wrappedQualifier(checkNotNull(qualifier).map(AnnotationMirrors.equivalence()::wrap));
    }

    public abstract Builder multibindingContributionIdentifier(
        Optional<MultibindingContributionIdentifier> identifier);

    public abstract Builder multibindingContributionIdentifier(
        MultibindingContributionIdentifier identifier);

    public abstract Key build();
  }

  /**
   * An object that identifies a multibinding contribution method and the module class that
   * contributes it to the graph.
   *
   * @see #multibindingContributionIdentifier()
   */
  public static final class MultibindingContributionIdentifier {
    private final String identifierString;

    /**
     * @deprecated This is only meant to be called from code in {@code dagger.internal.codegen}.
     * It is not part of a specified API and may change at any point.
     */
    @Deprecated
    public MultibindingContributionIdentifier(
        ExecutableElement bindingMethod, TypeElement contributingModule) {
      this.identifierString =
          String.format(
              "%s#%s", contributingModule.getQualifiedName(), bindingMethod.getSimpleName());
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned string is human-readable and distinguishes the keys in the same way as the
     * whole object.
     */
    @Override
    public String toString() {
      return identifierString;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MultibindingContributionIdentifier
          && ((MultibindingContributionIdentifier) obj)
              .identifierString.equals(this.identifierString);
    }

    @Override
    public int hashCode() {
      return identifierString.hashCode();
    }
  }
}

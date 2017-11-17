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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.MoreAnnotationMirrors.unwrapOptionalEquivalence;
import static dagger.internal.codegen.MoreAnnotationMirrors.wrapOptionalInEquivalence;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Equivalence;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import java.util.Optional;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

/**
 * Represents a unique combination of {@linkplain TypeMirror type} and
 * {@linkplain Qualifier qualifier} to which binding can occur.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class Key {

  /** An object that is associated with a {@link Key}. */
  interface HasKey {
    /** The key associated with this object. */
    Key key();
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
   * <p>Each multibound map and set has a {@linkplain
   * ProvisionBinding.Factory#syntheticMultibinding(Key, Iterable) synthetic multibinding} that
   * depends on the specific contributions to that map or set using keys that identify those
   * multibinding contributions.
   *
   * <p>Absent except for multibinding contributions.
   */
  abstract Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier();
  
  abstract Builder toBuilder();

  @Memoized
  @Override
  public abstract int hashCode();

  static Builder builder(TypeMirror type) {
    return new AutoValue_Key.Builder().type(type);
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder wrappedType(Equivalence.Wrapper<TypeMirror> wrappedType);

    Builder type(TypeMirror type) {
      return wrappedType(MoreTypes.equivalence().wrap(checkNotNull(type)));
    }

    abstract Builder wrappedQualifier(
        Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedQualifier);

    abstract Builder wrappedQualifier(Equivalence.Wrapper<AnnotationMirror> wrappedQualifier);

    Builder qualifier(AnnotationMirror qualifier) {
      return wrappedQualifier(AnnotationMirrors.equivalence().wrap(checkNotNull(qualifier)));
    }

    Builder qualifier(Optional<AnnotationMirror> qualifier) {
      return wrappedQualifier(wrapOptionalInEquivalence(checkNotNull(qualifier)));
    }

    Builder qualifier(TypeElement annotationType) {
      return qualifier(SimpleAnnotationMirror.of(annotationType));
    }

    abstract Builder multibindingContributionIdentifier(
        Optional<MultibindingContributionIdentifier> identifier);

    abstract Builder multibindingContributionIdentifier(
        MultibindingContributionIdentifier identifier);

    abstract Key build();
  }
  
  /**
   * An object that identifies a multibinding contribution method and the module class that
   * contributes it to the graph.
   *
   * @see Key#multibindingContributionIdentifier()
   */
  static final class MultibindingContributionIdentifier {
    private final String identifierString;

    MultibindingContributionIdentifier(
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

  /**
   * A {@link javax.inject.Qualifier} annotation that provides a unique namespace prefix
   * for the type of this key.
   */
  Optional<AnnotationMirror> qualifier() {
    return unwrapOptionalEquivalence(wrappedQualifier());
  }

  /**
   * The type represented by this key.
   */
  TypeMirror type() {
    return wrappedType().get();
  }

  /**
   * A key whose {@link #qualifier()} and {@link #type()} are equivalent to this one's, but without
   * a {@link #multibindingContributionIdentifier()}.
   */
  Key withoutMultibindingContributionIdentifier() {
    return toBuilder().multibindingContributionIdentifier(Optional.empty()).build();
  }

  boolean isValidMembersInjectionKey() {
    return !qualifier().isPresent() && type().getKind().equals(TypeKind.DECLARED);
  }

  /**
   * Returns {@code true} if this is valid as an implicit key (that is, if it's valid for a
   * just-in-time binding by discovering an {@code @Inject} constructor).
   */
  boolean isValidImplicitProvisionKey(Types types) {
    return isValidImplicitProvisionKey(qualifier(), type(), types);
  }

  /**
   * Returns {@code true} if a key with {@code qualifier} and {@code type} is valid as an implicit
   * key (that is, if it's valid for a just-in-time binding by discovering an {@code @Inject}
   * constructor).
   */
  static boolean isValidImplicitProvisionKey(
      Optional<? extends AnnotationMirror> qualifier, TypeMirror type, final Types types) {
    // Qualifiers disqualify implicit provisioning.
    if (qualifier.isPresent()) {
      return false;
    }

    return type.accept(
        new SimpleTypeVisitor6<Boolean, Void>(false) {
          @Override
          public Boolean visitDeclared(DeclaredType type, Void ignored) {
            // Non-classes or abstract classes aren't allowed.
            TypeElement element = MoreElements.asType(type.asElement());
            if (!element.getKind().equals(ElementKind.CLASS)
                || element.getModifiers().contains(Modifier.ABSTRACT)) {
              return false;
            }

            // If the key has type arguments, validate that each type argument is declared.
            // Otherwise the type argument may be a wildcard (or other type), and we can't
            // resolve that to actual types.
            for (TypeMirror arg : type.getTypeArguments()) {
              if (arg.getKind() != TypeKind.DECLARED) {
                return false;
              }
            }

            // Also validate that the key is not the erasure of a generic type.
            // If it is, that means the user referred to Foo<T> as just 'Foo',
            // which we don't allow.  (This is a judgement call -- we *could*
            // allow it and instantiate the type bounds... but we don't.)
            return MoreTypes.asDeclared(element.asType()).getTypeArguments().isEmpty()
                || !types.isSameType(types.erasure(element.asType()), type);
          }
        },
        null);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The returned string is equal to another key's if and only if this key is {@link
   * #equals(Object)} to it.
   */
  @Override
  public String toString() {
    return Joiner.on(' ')
        .skipNulls()
        .join(qualifier().orElse(null), type(), multibindingContributionIdentifier().orElse(null));
  }

  /**
   * Indexes {@code haveKeys} by {@link HasKey#key()}.
   */
  static <T extends HasKey> ImmutableSetMultimap<Key, T> indexByKey(Iterable<T> haveKeys) {
    return ImmutableSetMultimap.copyOf(Multimaps.index(haveKeys, HasKey::key));
  }

}

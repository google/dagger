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
import static java.util.stream.Collectors.joining;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.squareup.javapoet.CodeBlock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

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

  // The main hashCode/equality bottleneck is in MoreTypes.equivalence(). It's possible that we can
  // avoid this by tuning that method. Perhaps we can also avoid the issue entirely by interning all
  // Keys
  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object o);

  /**
   * Returns a String rendering of an {@link AnnotationMirror} that includes attributes in the order
   * defined in the annotation type. This will produce the same output for {@linkplain
   * AnnotationMirrors#equivalence() equal} {@link AnnotationMirror}s even if default values are
   * omitted or their attributes were written in different orders, e.g. {@code @A(b = "b", c = "c")}
   * and {@code @A(c = "c", b = "b", attributeWithDefaultValue = "default value")}.
   */
  // TODO(ronshapiro): move this to auto-common
  private static String stableAnnotationMirrorToString(AnnotationMirror qualifier) {
    StringBuilder builder = new StringBuilder("@").append(qualifier.getAnnotationType());
    ImmutableMap<ExecutableElement, AnnotationValue> elementValues =
        AnnotationMirrors.getAnnotationValuesWithDefaults(qualifier);
    if (!elementValues.isEmpty()) {
      ImmutableMap.Builder<String, String> namedValuesBuilder = ImmutableMap.builder();
      elementValues.forEach(
          (key, value) ->
              namedValuesBuilder.put(
                  key.getSimpleName().toString(), stableAnnotationValueToString(value)));
      ImmutableMap<String, String> namedValues = namedValuesBuilder.build();
      builder.append('(');
      if (namedValues.size() == 1 && namedValues.containsKey("value")) {
        // Omit "value ="
        builder.append(namedValues.get("value"));
      } else {
        builder.append(Joiner.on(", ").withKeyValueSeparator("=").join(namedValues));
      }
      builder.append(')');
    }
    return builder.toString();
  }

  private static String stableAnnotationValueToString(AnnotationValue annotationValue) {
    return annotationValue.accept(
        new SimpleAnnotationValueVisitor8<String, Void>() {
          @Override
          protected String defaultAction(Object value, Void ignore) {
            return value.toString();
          }

          @Override
          public String visitString(String value, Void ignore) {
            return CodeBlock.of("$S", value).toString();
          }

          @Override
          public String visitAnnotation(AnnotationMirror value, Void ignore) {
            return stableAnnotationMirrorToString(value);
          }

          @Override
          public String visitArray(List<? extends AnnotationValue> value, Void ignore) {
            return value.stream()
                .map(Key::stableAnnotationValueToString)
                .collect(joining(", ", "{", "}"));
          }
        },
        null);
  }

  @Override
  public final String toString() {
    return Joiner.on(' ')
        .skipNulls()
        .join(
            qualifier().map(Key::stableAnnotationMirrorToString).orElse(null),
            type(),
            multibindingContributionIdentifier().orElse(null));
  }

  /** Returns a builder for {@link Key}s. */
  public static Builder builder(TypeMirror type) {
    return new AutoValue_Key.Builder().type(type);
  }

  /** A builder for {@link Key}s. */
  @CanIgnoreReturnValue
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

/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal.codegen.xprocessing;

import static androidx.room.compiler.processing.compat.XConverters.getProcessingEnv;
import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static androidx.room.compiler.processing.compat.XConverters.toXProcessing;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.squareup.javapoet.TypeName;
import java.util.Optional;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.TypeKind;

/** A utility class for {@link XProcessingEnvs} helper methods. */
// TODO(bcorso): Consider moving these methods into XProcessing library.
public final class XProcessingEnvs {
  /** Returns {@code true} if the sources are being compiled on a javac and the version is <= 8. */
  public static boolean isPreJava8SourceVersion(XProcessingEnv processingEnv) {
    Optional<SourceVersion> javaSourceVersion = javaSourceVersion(processingEnv);
    return javaSourceVersion.isPresent()
        && javaSourceVersion.get().compareTo(SourceVersion.RELEASE_8) < 0;
  }

  /**
   * Returns an optional containing the java source version if the current sources are being
   * compiled with javac, or else returns an empty optional.
   */
  private static Optional<SourceVersion> javaSourceVersion(XProcessingEnv processingEnv) {
    switch (processingEnv.getBackend()) {
      case JAVAC:
        return Optional.of(toJavac(processingEnv).getSourceVersion());
      case KSP:
        return Optional.empty();
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  /**
   * Returns {@code true} if {@code overrider} overrides {@code overridden} from within {@code type}
   */
  public static boolean javacOverrides(
      XMethodElement overrider,
      XMethodElement overridden,
      XTypeElement type) {
    XProcessingEnv processingEnv = getProcessingEnv(type);
    switch (processingEnv.getBackend()) {
      case JAVAC:
        // TODO(bcorso): Investigate why XMethodElement#overrides() throws exception in some cases.
        return toJavac(processingEnv)
            .getElementUtils() // ALLOW_TYPES_ELEMENTS
            .overrides(toJavac(overrider), toJavac(overridden), toJavac(type));
      case KSP:
        // For KSP, just use the standard overrides since the issues above are specific to javac.
        return overrider.overrides(overridden, type);
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  /** Returns a new unbounded wildcard type argument, i.e. {@code <?>}. */
  public static XType getUnboundedWildcardType(XProcessingEnv processingEnv) {
    switch (processingEnv.getBackend()) {
      case JAVAC:
        return toXProcessing(
            toJavac(processingEnv)
                .getTypeUtils() // ALLOW_TYPES_ELEMENTS
                .getWildcardType(null, null),
            processingEnv);
      case KSP:
        // In KSP, the equivalent of an unbounded wildcard type is the star projection. However,
        // there's currently no way to create a star projection type directly. Instead, we create a
        // List<T> type, get its star projection, and then grab the type argument from that.
        return XConverters.toXProcessing(
                XConverters.toKS(processingEnv.requireType("java.util.List")).starProjection(),
                processingEnv)
            .getTypeArguments()
            .get(0);
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  /**
   * Returns {@code type}'s single type argument.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has zero or more
   *     than one type arguments.
   */
  public static XType unwrapType(XType type) {
    XType unwrapped = unwrapTypeOrDefault(type, null);
    checkArgument(unwrapped != null, "%s is a raw type", type);
    return unwrapped;
  }

  /**
   * Returns {@code type}'s single type argument, if one exists, or {@link Object} if not.
   *
   * <p>For example, if {@code type} is {@code List<Number>} this will return {@code Number}.
   *
   * @throws IllegalArgumentException if {@code type} is not a declared type or has more than one
   *     type argument.
   */
  public static XType unwrapTypeOrObject(XType type, XProcessingEnv processingEnv) {
    return unwrapTypeOrDefault(type, processingEnv.requireType(TypeName.OBJECT));
  }

  private static XType unwrapTypeOrDefault(XType type, XType defaultType) {
    XTypeElement typeElement = type.getTypeElement();
    checkArgument(
        !typeElement.getType().getTypeArguments().isEmpty(),
        "%s does not have a type parameter",
        typeElement.getQualifiedName());
    return getOnlyElement(type.getTypeArguments(), defaultType);
  }

  /** Returns a primitive int {@link XType}. */
  public static XType getPrimitiveIntType(XProcessingEnv processingEnv) {
    return toXProcessing(
        toJavac(processingEnv)
            .getTypeUtils() // ALLOW_TYPES_ELEMENTS
            .getPrimitiveType(TypeKind.INT),
        processingEnv);
  }

  /** Returns the type this method is enclosed in. */
  public static XType wrapType(XClassName wrapper, XType type, XProcessingEnv processingEnv) {
    return processingEnv.getDeclaredType(processingEnv.requireTypeElement(wrapper), type);
  }

  private XProcessingEnvs() {}
}

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

package dagger.internal.codegen.xprocessing;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;

import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import javax.lang.model.type.TypeKind;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link XType} helper methods. */
public final class XTypes {

  /** Returns {@code true} if the raw type of {@code type} is equal to {@code className}. */
  public static boolean isTypeOf(XType type, ClassName className) {
    return isDeclared(type) && type.getTypeElement().getClassName().equals(className);
  }

  /** Returns {@code true} if the given type is a declared type. */
  public static boolean isWildcard(XType type) {
    return toJavac(type).getKind().equals(TypeKind.WILDCARD);
  }

  /** Returns {@code true} if the given type is a declared type. */
  public static boolean isDeclared(XType type) {
    return type.getTypeElement() != null;
  }

  /** Returns {@code true} if the given type is a type variable. */
  public static boolean isTypeVariable(XType type) {
    return XConverters.toJavac(type).getKind() == TypeKind.TYPEVAR;
  }

  /**
   * Returns {@code true} if {@code type1} is equivalent to {@code type2}.
   *
   * <p>See {@link MoreTypes#equivalence()}.
   */
  public static boolean areEquivalentTypes(XType type1, XType type2) {
    return MoreTypes.equivalence().equivalent(toJavac(type1), toJavac(type2));
  }

  /** Returns {@code true} if the given type is a primitive type. */
  public static boolean isPrimitive(XType type) {
    return XConverters.toJavac(type).getKind().isPrimitive();
  }

  /** Returns {@code true} if the given type has type parameters. */
  public static boolean hasTypeParameters(XType type) {
    return !type.getTypeArguments().isEmpty();
  }

  private XTypes() {}
}

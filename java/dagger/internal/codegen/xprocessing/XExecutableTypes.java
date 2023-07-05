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
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.codegen.XTypeNameKt;
import androidx.room.compiler.processing.XConstructorType;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableType;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.TypeName;

/** A utility class for {@link XExecutableType} helper methods. */
// TODO(bcorso): Consider moving these methods into XProcessing library.
public final class XExecutableTypes {

  // TODO(b/271177465): Remove this method once XProcessing supports this feature.
  public static boolean isSubsignature(XExecutableElement method1, XExecutableElement method2) {
    XProcessingEnv processingEnv = getProcessingEnv(method1);
    switch (processingEnv.getBackend()) {
      case JAVAC:
        return isSubsignatureJavac(method1, method2, processingEnv);
      case KSP:
        return isSubsignatureKsp(method1, method2);
    }
    throw new AssertionError("Unexpected backend: " + processingEnv.getBackend());
  }

  private static boolean isSubsignatureKsp(XExecutableElement method1, XExecutableElement method2) {
    if (method1.getParameters().size() != method2.getParameters().size()) {
      return false;
    }
    ImmutableList<TypeName> method1Parameters = getParameters(method1);
    ImmutableList<TypeName> method1TypeParameters = getTypeParameters(method1);
    ImmutableList<TypeName> method2TypeParameters = getTypeParameters(method2);
    return (method1TypeParameters.equals(method2TypeParameters)
            && method1Parameters.equals(getParameters(method2)))
        || (method1TypeParameters
                .isEmpty() // "The erasure of the signature of a generic method has no type
            // parameters."
            && method1Parameters.equals(
                method2.getExecutableType().getParameterTypes().stream()
                    .map(XTypes::erasedTypeName)
                    .collect(toImmutableList())));
  }

  private static ImmutableList<TypeName> getParameters(XExecutableElement method) {
    return method.getExecutableType().getParameterTypes().stream()
        .map(XType::asTypeName)
        .map(XTypeNameKt::toJavaPoet)
        .collect(toImmutableList());
  }

  private static ImmutableList<TypeName> getTypeParameters(XExecutableElement method) {
    return method.getTypeParameters().stream()
        .map(it -> it.getBounds().get(0))
        .map(XType::asTypeName)
        .map(XTypeNameKt::toJavaPoet)
        .collect(toImmutableList());
  }

  private static boolean isSubsignatureJavac(
      XExecutableElement method1, XExecutableElement method2, XProcessingEnv env) {
    return toJavac(env)
        .getTypeUtils() // ALLOW_TYPES_ELEMENTS
        .isSubsignature(toJavac(method1.getExecutableType()), toJavac(method2.getExecutableType()));
  }

  public static boolean isConstructorType(XExecutableType executableType) {
    return executableType instanceof XConstructorType;
  }

  public static boolean isMethodType(XExecutableType executableType) {
    return executableType instanceof XMethodType;
  }

  public static XMethodType asMethodType(XExecutableType executableType) {
    return (XMethodType) executableType;
  }

  public static String getKindName(XExecutableType executableType) {
    if (isMethodType(executableType)) {
      return "METHOD";
    } else if (isConstructorType(executableType)) {
      return "CONSTRUCTOR";
    }
    return "UNKNOWN";
  }

  /**
   * Returns a string representation of {@link XExecutableType} that is independent of the backend
   * (javac/ksp).
   */
  public static String toStableString(XExecutableType executableType) {
    try {
      return String.format(
          "(%s)%s",
          executableType.getParameterTypes().stream()
              .map(XTypes::toStableString)
              .collect(joining(",")),
          isMethodType(executableType)
              ? XTypes.toStableString(asMethodType(executableType).getReturnType())
              : TypeName.VOID);
    } catch (TypeNotPresentException e) {
      return e.typeName();
    }
  }

  private XExecutableTypes() {}
}

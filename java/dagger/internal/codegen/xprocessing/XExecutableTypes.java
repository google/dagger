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

import androidx.room.compiler.processing.XConstructorType;
import androidx.room.compiler.processing.XExecutableType;
import androidx.room.compiler.processing.XMethodType;

/** A utility class for {@link XExecutableType} helper methods. */
// TODO(bcorso): Consider moving these methods into XProcessing library.
public final class XExecutableTypes {

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

  private XExecutableTypes() {}
}

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

package dagger.internal.codegen.writing;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XFunSpec;
import androidx.room.compiler.codegen.XPropertySpec;
import androidx.room.compiler.codegen.XTypeSpec;
import com.google.common.base.Supplier;
import dagger.internal.codegen.writing.ComponentImplementation.FieldSpecKind;
import dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind;
import dagger.internal.codegen.writing.ComponentImplementation.TypeSpecKind;

/** Represents the implementation of a generated class. */
public interface GeneratedImplementation {
  /** Returns the name of the component. */
  XClassName name();

  /** Returns a new, unique method name for the component based on the given name. */
  String getUniqueClassName(String name);

  /** Adds the given field to the generated implementation. */
  void addField(FieldSpecKind fieldKind, XPropertySpec fieldSpec);

  /** Adds the given method to the generated implementation. */
  void addMethod(MethodSpecKind methodKind, XFunSpec methodSpec);

  /** Adds the given type to the generated implementation. */
  void addType(TypeSpecKind typeKind, XTypeSpec typeSpec);

  /** Adds a {@link Supplier} for a {@link XTypeSpec} to the generated implementation. */
  void addTypeSupplier(Supplier<XTypeSpec> typeSupplier);

  /** Returns the {@link XTypeSpec} for this generated implementation. */
  public XTypeSpec generate();
}

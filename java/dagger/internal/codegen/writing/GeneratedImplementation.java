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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.writing.ComponentImplementation.FieldSpecKind;
import dagger.internal.codegen.writing.ComponentImplementation.MethodSpecKind;
import dagger.internal.codegen.writing.ComponentImplementation.TypeSpecKind;

/** Represents the implementation of a generated class. */
public interface GeneratedImplementation {
  /** Returns the name of the component. */
  ClassName name();

  /** Returns a new, unique method name for the component based on the given name. */
  String getUniqueClassName(String name);

  /** Adds the given field to the generated implementation. */
  void addField(FieldSpecKind fieldKind, FieldSpec fieldSpec);

  /** Adds the given method to the generated implementation. */
  void addMethod(MethodSpecKind methodKind, MethodSpec methodSpec);

  /** Adds the given type to the generated implementation. */
  void addType(TypeSpecKind typeKind, TypeSpec typeSpec);

  /** Returns the {@link TypeSpec} for this generated implementation. */
  public TypeSpec generate();
}

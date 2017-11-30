/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;

/** The model of the component being generated. */
final class GeneratedComponentModel {
  private final ClassName name;
  // TODO(user): This is only non-private to ease migration with AbstractComponentWriter!
  final TypeSpec.Builder component;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final UniqueNameSet componentMethodNames = new UniqueNameSet();
  private final List<CodeBlock> initializations = new ArrayList<>();

  private GeneratedComponentModel(ClassName name, Modifier... modifiers) {
    this.name = name;
    this.component = classBuilder(name).addModifiers(modifiers);
  }

  static GeneratedComponentModel forComponent(ClassName name) {
    return new GeneratedComponentModel(name, PUBLIC, FINAL);
  }

  static GeneratedComponentModel forSubcomponent(ClassName name) {
    return new GeneratedComponentModel(name, PRIVATE, FINAL);
  }

  /** Returns the name of the component. */
  ClassName name() {
    return name;
  }

  /** Adds the given field to the component. */
  // TODO(user): Add a enum for field order/type so that we can control the order of fields.
  void addField(FieldSpec fieldSpec) {
    component.addField(fieldSpec);
  }

  /** Adds the given method to the component. */
  // TODO(user): Add a enum for method order/type so that we can control the order of methods.
  void addMethod(MethodSpec methodSpec) {
    component.addMethod(methodSpec);
  }

  /** Adds the given methods to the component. */
  void addMethods(Iterable<MethodSpec> methodSpecs) {
    component.addMethods(methodSpecs);
  }

  /** Adds the given code block to the initialize methods of the component. */
  void addInitialization(CodeBlock codeBlock) {
    initializations.add(codeBlock);
  }

  /** Adds the given type to the component. */
  void addType(TypeSpec typeSpec) {
    component.addType(typeSpec);
  }

  /** Returns a new, unique field name for the component based on the given name. */
  String getUniqueFieldName(String name) {
    return componentFieldNames.getUniqueName(name);
  }

  /** Returns a new, unique method name for the component based on the given name. */
  String getUniqueMethodName(String name) {
    return componentMethodNames.getUniqueName(name);
  }

  /** Claims a new method name for the component. Does nothing if method name already exists. */
  void claimMethodName(Name name) {
    componentMethodNames.claim(name);
  }

  /** Returns the list of {@link CodeBlock}s that need to go in the initialize method. */
  ImmutableList<CodeBlock> getInitializations() {
    return ImmutableList.copyOf(initializations);
  }
}

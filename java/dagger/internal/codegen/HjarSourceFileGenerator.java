/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import java.util.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

/**
 * A source file generator that only writes the relevant code necessary for Bazel to create a
 * correct header (ABI) jar.
 */
final class HjarSourceFileGenerator<T> extends SourceFileGenerator<T> {
  private final SourceFileGenerator<T> delegate;

  private HjarSourceFileGenerator(SourceFileGenerator<T> delegate) {
    super(delegate);
    this.delegate = delegate;
  }

  static <T> SourceFileGenerator<T> wrap(SourceFileGenerator<T> delegate) {
    return new HjarSourceFileGenerator<>(delegate);
  }

  @Override
  ClassName nameGeneratedType(T input) {
    return delegate.nameGeneratedType(input);
  }

  @Override
  Element originatingElement(T input) {
    return delegate.originatingElement(input);
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, T input) {
    return delegate
        .write(generatedTypeName, input)
        .map(completeType -> skeletonType(completeType.build()));
  }

  private TypeSpec.Builder skeletonType(TypeSpec completeType) {
    TypeSpec.Builder skeleton =
        classBuilder(completeType.name)
            .addSuperinterfaces(completeType.superinterfaces)
            .addTypeVariables(completeType.typeVariables)
            .addModifiers(completeType.modifiers.toArray(new Modifier[0]))
            .addAnnotations(completeType.annotations);

    if (!completeType.superclass.equals(ClassName.OBJECT)) {
      skeleton.superclass(completeType.superclass);
    }

    completeType.methodSpecs.stream()
        .filter(method -> !method.modifiers.contains(PRIVATE) || method.isConstructor())
        .map(this::skeletonMethod)
        .forEach(skeleton::addMethod);

    completeType.fieldSpecs.stream()
        .filter(field -> !field.modifiers.contains(PRIVATE))
        .map(this::skeletonField)
        .forEach(skeleton::addField);

    completeType.typeSpecs.stream()
        .map(type -> skeletonType(type).build())
        .forEach(skeleton::addType);

    return skeleton;
  }

  private MethodSpec skeletonMethod(MethodSpec completeMethod) {
    MethodSpec.Builder skeleton =
        completeMethod.isConstructor()
            ? constructorBuilder()
            : methodBuilder(completeMethod.name).returns(completeMethod.returnType);

    if (completeMethod.isConstructor()) {
      // Code in Turbine must (for technical reasons in javac) have a valid super() call for
      // constructors, otherwise javac will bark, and Turbine has no way to avoid this. So we retain
      // constructor method bodies if they do exist
      skeleton.addCode(completeMethod.code);
    }

    return skeleton
        .addModifiers(completeMethod.modifiers)
        .addTypeVariables(completeMethod.typeVariables)
        .addParameters(completeMethod.parameters)
        .addExceptions(completeMethod.exceptions)
        .varargs(completeMethod.varargs)
        .addAnnotations(completeMethod.annotations)
        .build();
  }

  private FieldSpec skeletonField(FieldSpec completeField) {
    return FieldSpec.builder(
            completeField.type,
            completeField.name,
            completeField.modifiers.toArray(new Modifier[0]))
        .addAnnotations(completeField.annotations)
        .build();
  }
}

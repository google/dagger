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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.JavaPoetExt.toParameterSpec;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

import androidx.room.compiler.codegen.compat.XConverters;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

// TODO(bcorso): Consider moving these methods into XProcessing library.
/** A utility class for {@link MethodSpec} helper methods. */
public final class MethodSpecs {

  /** Returns a {@link MethodSpec} that overrides the given method. */
  public static MethodSpec.Builder overriding(XMethodElement method, XType owner) {
    XMethodType methodType = method.asMemberOf(owner);
    Nullability nullability = Nullability.of(method);
    TypeName returnType =
        Nullability.getTypeNameWithNullableAnnotations(methodType.getReturnType());

    MethodSpec.Builder builder =
        // We're overriding the method so we have to use the jvm name here.
        MethodSpec.methodBuilder(method.getJvmName())
            .addAnnotation(Override.class)
            .addAnnotations(
                nullability.nonTypeUseNullableAnnotations().stream()
                    .map(XConverters::toJavaPoet)
                    .map(AnnotationSpec::builder)
                    .map(AnnotationSpec.Builder::build)
                    .collect(toImmutableList()))
            .addTypeVariables(methodType.getTypeVariableNames())
            .varargs(method.isVarArgs())
            .returns(returnType);
    if (method.isPublic()) {
      builder.addModifiers(PUBLIC);
    } else if (method.isProtected()) {
      builder.addModifiers(PROTECTED);
    }
    for (int i = 0; i < methodType.getParameterTypes().size(); i++) {
      XExecutableParameterElement parameter = method.getParameters().get(i);
      XType parameterType = methodType.getParameterTypes().get(i);
      builder.addParameter(toParameterSpec(parameter, parameterType));
    }
    method.getThrownTypes().stream().map(XType::getTypeName).forEach(builder::addException);
    return builder;
  }

  private MethodSpecs() {}
}

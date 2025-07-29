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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.xprocessing.XTypes;

/** A class that defines proper {@code equals} and {@code hashcode} for a method signature. */
@AutoValue
public abstract class MethodSignature {

  abstract String name();

  abstract ImmutableList<Equivalence.Wrapper<XType>> parameterTypes();

  abstract ImmutableList<Equivalence.Wrapper<XType>> thrownTypes();

  public static MethodSignature forComponentMethod(
      ComponentMethodDescriptor componentMethod,
      XType componentType,
      XProcessingEnv processingEnv) {
    XMethodType methodType = componentMethod.methodElement().asMemberOf(componentType);
    return new AutoValue_MethodSignature(
        getSimpleName(componentMethod.methodElement()),
        methodType.getParameterTypes().stream()
            .map(XTypes.equivalence()::wrap)
            .collect(toImmutableList()),
        // Using the thrown types of the method element, which should be the same as the method type
        // since thrown types can't use type variables.
        // TODO(bcorso): Support getting thrown types from XExecutableType in XProcessing.
        componentMethod.methodElement().getThrownTypes().stream()
            .map(XTypes.equivalence()::wrap)
            .collect(toImmutableList()));
  }
}

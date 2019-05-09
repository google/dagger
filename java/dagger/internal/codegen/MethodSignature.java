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

package dagger.internal.codegen;

import static dagger.internal.codegen.DaggerStreams.toImmutableList;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.List;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

@AutoValue
abstract class MethodSignature {

  abstract String name();

  abstract ImmutableList<? extends Equivalence.Wrapper<? extends TypeMirror>> parameterTypes();

  abstract ImmutableList<? extends Equivalence.Wrapper<? extends TypeMirror>> thrownTypes();

  static MethodSignature forComponentMethod(
      ComponentMethodDescriptor componentMethod, DeclaredType componentType, DaggerTypes types) {
    ExecutableType methodType =
        MoreTypes.asExecutable(types.asMemberOf(componentType, componentMethod.methodElement()));
    return new AutoValue_MethodSignature(
        componentMethod.methodElement().getSimpleName().toString(),
        wrapInEquivalence(methodType.getParameterTypes()),
        wrapInEquivalence(methodType.getThrownTypes()));
  }

  private static ImmutableList<? extends Equivalence.Wrapper<? extends TypeMirror>>
      wrapInEquivalence(List<? extends TypeMirror> types) {
    return types.stream().map(MoreTypes.equivalence()::wrap).collect(toImmutableList());
  }
}

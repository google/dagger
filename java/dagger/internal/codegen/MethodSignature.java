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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

@AutoValue
abstract class MethodSignature {
  abstract String name();
  abstract ImmutableList<Equivalence.Wrapper<TypeMirror>> parameterTypes();
  abstract ImmutableList<Equivalence.Wrapper<TypeMirror>> thrownTypes();

  static MethodSignature fromExecutableType(String methodName, ExecutableType methodType) {
    checkNotNull(methodType);
    ImmutableList.Builder<Equivalence.Wrapper<TypeMirror>> parameters = ImmutableList.builder();
    ImmutableList.Builder<Equivalence.Wrapper<TypeMirror>> thrownTypes = ImmutableList.builder();
    for (TypeMirror parameter : methodType.getParameterTypes()) {
      parameters.add(MoreTypes.equivalence().wrap(parameter));
    }
    for (TypeMirror thrownType : methodType.getThrownTypes()) {
      thrownTypes.add(MoreTypes.equivalence().wrap(thrownType));
    }
    return new AutoValue_MethodSignature(
        methodName,
        parameters.build(),
        thrownTypes.build());
  }
}

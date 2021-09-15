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

package dagger.internal.codegen.writing;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.spi.model.BindingKind;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

/** Utility class for generating unique assisted parameter names for a component shard. */
final class AssistedInjectionParameters {
  /**
   * Returns the list of assisted parameters as {@link ParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<ParameterSpec> assistedParameterSpecs(
      Binding binding, DaggerTypes types, ShardImplementation shardImplementation) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    ExecutableElement constructor = asExecutable(binding.bindingElement().get());
    ExecutableType constructorType =
        asExecutable(types.asMemberOf(asDeclared(binding.key().type().java()), constructor));
    return assistedParameterSpecs(
        constructor.getParameters(), constructorType.getParameterTypes(), shardImplementation);
  }

  private static ImmutableList<ParameterSpec> assistedParameterSpecs(
      List<? extends VariableElement> paramElements,
      List<? extends TypeMirror> paramTypes,
      ShardImplementation shardImplementation) {
    ImmutableList.Builder<ParameterSpec> assistedParameterSpecs = ImmutableList.builder();
    for (int i = 0; i < paramElements.size(); i++) {
      VariableElement paramElement = paramElements.get(i);
      TypeMirror paramType = paramTypes.get(i);
      if (AssistedInjectionAnnotations.isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            ParameterSpec.builder(
                    TypeName.get(paramType),
                    shardImplementation.getUniqueFieldNameForAssistedParam(paramElement))
                .build());
      }
    }
    return assistedParameterSpecs.build();
  }

  private AssistedInjectionParameters() {}
}

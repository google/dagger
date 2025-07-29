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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;

import androidx.room.compiler.codegen.XParameterSpec;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XConstructorType;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodType;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedFactoryMetadata;
import dagger.internal.codegen.binding.Binding;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.writing.ComponentImplementation.ShardImplementation;
import dagger.internal.codegen.xprocessing.XParameterSpecs;
import java.util.List;

/** Utility class for generating unique assisted parameter names for a component shard. */
final class AssistedInjectionParameters {
  /**
   * Returns the list of assisted factory parameters as {@link XParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<XParameterSpec> assistedFactoryParameterSpecs(
      Binding binding, ShardImplementation shardImplementation) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_FACTORY);
    XTypeElement factory = asTypeElement(binding.bindingElement().get());
    AssistedFactoryMetadata metadata = AssistedFactoryMetadata.create(factory.getType());
    XMethodType factoryMethodType =
        metadata.factoryMethod().asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(
        // Use the order of the parameters from the @AssistedFactory method but use the parameter
        // names of the @AssistedInject constructor.
        metadata.assistedFactoryAssistedParameters().stream()
            .map(metadata.assistedInjectAssistedParametersMap()::get)
            .collect(toImmutableList()),
        factoryMethodType.getParameterTypes(),
        shardImplementation);
  }

  /**
   * Returns the list of assisted parameters as {@link XParameterSpec}s.
   *
   * <p>The type of each parameter will be the resolved type given by the binding key, and the name
   * of each parameter will be the name given in the {@link
   * dagger.assisted.AssistedInject}-annotated constructor.
   */
  public static ImmutableList<XParameterSpec> assistedParameterSpecs(
      Binding binding, ShardImplementation shardImplementation) {
    checkArgument(binding.kind() == BindingKind.ASSISTED_INJECTION);
    XConstructorElement constructor = asConstructor(binding.bindingElement().get());
    XConstructorType constructorType = constructor.asMemberOf(binding.key().type().xprocessing());
    return assistedParameterSpecs(
        constructor.getParameters(), constructorType.getParameterTypes(), shardImplementation);
  }

  private static ImmutableList<XParameterSpec> assistedParameterSpecs(
      List<XExecutableParameterElement> paramElements,
      List<XType> paramTypes,
      ShardImplementation shardImplementation) {
    ImmutableList.Builder<XParameterSpec> assistedParameterSpecs = ImmutableList.builder();
    for (int i = 0; i < paramElements.size(); i++) {
      XExecutableParameterElement paramElement = paramElements.get(i);
      XType paramType = paramTypes.get(i);
      if (AssistedInjectionAnnotations.isAssistedParameter(paramElement)) {
        assistedParameterSpecs.add(
            XParameterSpecs.of(
                shardImplementation.getUniqueFieldNameForAssistedParam(paramElement),
                paramType.asTypeName()));
      }
    }
    return assistedParameterSpecs.build();
  }

  private AssistedInjectionParameters() {}
}

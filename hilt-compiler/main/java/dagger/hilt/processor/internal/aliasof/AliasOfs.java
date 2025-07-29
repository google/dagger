/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal.aliasof;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.ComponentDescriptor;
import dagger.hilt.processor.internal.ProcessorErrors;

/**
 * Extracts a multimap of aliases annotated with {@link dagger.hilt.migration.AliasOf} mapping them
 * to scopes they are alias of.
 */
public final class AliasOfs {
  public static AliasOfs create(
      ImmutableSet<AliasOfPropagatedDataMetadata> metadatas,
      ImmutableSet<ComponentDescriptor> componentDescriptors) {
    ImmutableSet<ClassName> defineComponentScopes =
        componentDescriptors.stream()
            .flatMap(descriptor -> descriptor.scopes().stream())
            .collect(toImmutableSet());

    ImmutableSetMultimap.Builder<ClassName, ClassName> builder = ImmutableSetMultimap.builder();
    metadatas.forEach(
        metadata -> {
          ClassName aliasScopeName = metadata.aliasElement().getClassName();
          metadata
              .defineComponentScopeElements()
              .forEach(
                  defineComponentScope -> {
                    ClassName defineComponentScopeName = defineComponentScope.getClassName();
                    ProcessorErrors.checkState(
                        defineComponentScopes.contains(defineComponentScopeName),
                        metadata.aliasElement(),
                        "The scope %s cannot be an alias for %s. You can only have aliases of a"
                            + " scope defined directly on a @DefineComponent type.",
                        aliasScopeName,
                        defineComponentScopeName);
                    builder.put(defineComponentScopeName, aliasScopeName);
                  });
        });
    return new AliasOfs(builder.build());
  }

  private final ImmutableSetMultimap<ClassName, ClassName> defineComponentScopeToAliases;

  private AliasOfs(ImmutableSetMultimap<ClassName, ClassName> defineComponentScopeToAliases) {
    this.defineComponentScopeToAliases = defineComponentScopeToAliases;
  }

  public ImmutableSet<ClassName> getAliasesFor(ClassName defineComponentScope) {
    return defineComponentScopeToAliases.get(defineComponentScope);
  }
}

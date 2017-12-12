/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.BindingKey.Kind.MEMBERS_INJECTION;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;

import com.squareup.javapoet.CodeBlock;

/** An initializer for {@link dagger.MembersInjector} fields. */
final class MembersInjectorFieldInitializer extends FrameworkFieldInitializer {

  // TODO(ronshapiro): add Binding.bindingKey() and use that instead of taking a ResolvedBindings
  private final ResolvedBindings resolvedBindings;

  MembersInjectorFieldInitializer(
      ResolvedBindings resolvedBindings,
      GeneratedComponentModel generatedComponentModel,
      ComponentBindingExpressions componentBindingExpressions) {
    super(generatedComponentModel, componentBindingExpressions, resolvedBindings);
    checkArgument(resolvedBindings.bindingKey().kind().equals(MEMBERS_INJECTION));
    this.resolvedBindings = checkNotNull(resolvedBindings);
  }

  @Override
  protected CodeBlock getFieldInitialization() {
    MembersInjectionBinding membersInjectionBinding =
        resolvedBindings.membersInjectionBinding().get();
    return CodeBlock.of(
        "$T.create($L)",
        membersInjectorNameForType(membersInjectionBinding.membersInjectedType()),
        makeParametersCodeBlock(getBindingDependencyExpressions(membersInjectionBinding)));
  }
}

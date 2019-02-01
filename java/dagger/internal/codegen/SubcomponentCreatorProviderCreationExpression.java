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

import static dagger.internal.codegen.CodeBlocks.anonymousProvider;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import javax.lang.model.type.TypeMirror;

/** A {@link javax.inject.Provider} creation expression for a subcomponent creator. */
final class SubcomponentCreatorProviderCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final TypeMirror creatorType;
  private final String creatorImplementationName;

  SubcomponentCreatorProviderCreationExpression(
      TypeMirror creatorType, String creatorImplementationName) {
    this.creatorType = creatorType;
    this.creatorImplementationName = creatorImplementationName;
  }

  @Override
  public CodeBlock creationExpression() {
    return anonymousProvider(
        TypeName.get(creatorType), CodeBlock.of("return new $L();", creatorImplementationName));
  }
}

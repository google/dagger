/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static com.squareup.javapoet.ClassName.OBJECT;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.TypeName.INT;
import static dagger.internal.codegen.BindingRequest.bindingRequest;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.CodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;
import dagger.model.Key;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.type.TypeMirror;

/**
 * Generates {@linkplain BindingExpression binding expressions} for a binding that is represented by
 * a static {@code SwitchingProvider} class.
 *
 * <p>Currently, the generated {@code SwitchingProvider} class is generated as a static nested class
 * in the root component. Ideally, each component would get its own {@code SwitchingProvider}, but
 * since the subcomponents are inner classes they cannot contain static classes.
 */
@PerGeneratedFile
final class StaticSwitchingProviders extends SwitchingProviders {
  private final DaggerTypes types;
  private final ClassName owningComponent;

  @Inject
  StaticSwitchingProviders(
      @TopLevel ComponentImplementation componentImplementation, DaggerTypes types) {
    super(componentImplementation, types);
    this.types = types;
    this.owningComponent = componentImplementation.name();
  }

  /**
   * Returns the {@link FrameworkInstanceCreationExpression} for a binding that satisfies a {@link
   * Provider} requests with a static {@code SwitchingProvider} class.
   */
  FrameworkInstanceCreationExpression newCreationExpression(
      ContributionBinding binding, ComponentBindingExpressions componentBindingExpressions) {
    return new FrameworkInstanceCreationExpression() {
      @Override
      public CodeBlock creationExpression() {
        return getProviderExpression(new SwitchCase(binding, componentBindingExpressions))
            .codeBlock();
      }
    };
  }

  @Override
  protected TypeSpec createSwitchingProviderType(TypeSpec.Builder builder) {
    return builder
        .addModifiers(PRIVATE, FINAL, STATIC)
        .addField(INT, "id", PRIVATE, FINAL)
        .addField(ArrayTypeName.of(OBJECT), "dependencies", PRIVATE, FINAL)
        .addMethod(
            constructorBuilder()
                .addParameter(INT, "id")
                .addParameter(ArrayTypeName.of(OBJECT), "dependencies")
                .varargs()
                .addStatement("this.id = id")
                .addStatement("this.dependencies = dependencies")
                .build())
        .build();
  }

  private final class SwitchCase implements SwitchingProviders.SwitchCase {
    private final ComponentBindingExpressions componentBindingExpressions;
    private final ContributionBinding binding;

    SwitchCase(
        ContributionBinding binding, ComponentBindingExpressions componentBindingExpressions) {
      this.binding = binding;
      this.componentBindingExpressions = componentBindingExpressions;
    }

    @Override
    public Key key() {
      return binding.key();
    }

    @Override
    public Expression getProviderExpression(ClassName switchingProviderClass, int switchId) {
      TypeMirror accessibleType = types.accessibleType(binding.contributedType(), owningComponent);
      // Java 7 type inference can't figure out that instance in
      // DoubleCheck.provider(new SwitchingProvider<>()) is Provider<T> and not Provider<Object>
      CodeBlock typeParameter = CodeBlock.of("$T", accessibleType);

      CodeBlock arguments =
          Stream.of(
                  CodeBlock.of("$L", switchId),
                  componentBindingExpressions.getCreateMethodArgumentsCodeBlock(binding))
              .filter(codeBlock -> !codeBlock.isEmpty())
              .collect(toParametersCodeBlock());

      return Expression.create(
          types.wrapType(accessibleType, Provider.class),
          CodeBlock.of("new $T<$L>($L)", switchingProviderClass, typeParameter, arguments));
    }

    @Override
    public Expression getReturnExpression(ClassName switchingProviderClass) {
      return Expression.create(
          binding.contributedType(),
          CodeBlock.of(
              "$T.provideInstance($L)",
              generatedClassNameForBinding(binding),
              getMethodArguments(switchingProviderClass)));
    }

    private CodeBlock getMethodArguments(ClassName switchingProviderClass) {
      int i = 0;
      ImmutableList.Builder<CodeBlock> arguments = ImmutableList.builder();
      if (binding.requiresModuleInstance()) {
        arguments.add(argument(binding.contributingModule().get().asType(), i++));
      }

      for (FrameworkDependency dependency : binding.frameworkDependencies()) {
        TypeMirror type =
            componentBindingExpressions
                .getDependencyExpression(bindingRequest(dependency), switchingProviderClass)
                .type();
        arguments.add(argument(type, i++));
      }
      return makeParametersCodeBlock(arguments.build());
    }

    private CodeBlock argument(TypeMirror type, int index) {
      CodeBlock.Builder builder = CodeBlock.builder();
      TypeName accessibleType = TypeName.get(types.accessibleType(type, owningComponent));
      if (!accessibleType.equals(ClassName.OBJECT)) {
        builder.add("($T) ", accessibleType);
      }
      return builder.add("dependencies[$L]", index).build();
    }
  }
}

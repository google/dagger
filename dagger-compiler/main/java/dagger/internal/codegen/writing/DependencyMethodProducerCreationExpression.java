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

package dagger.internal.codegen.writing;

import static androidx.room.compiler.codegen.compat.XConverters.toJavaPoet;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.anonymousClassBuilder;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XTypeNames.dependencyMethodProducerOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.listenableFutureOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import androidx.room.compiler.codegen.XTypeName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentDependencyProductionBinding;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.writing.FrameworkFieldInitializer.FrameworkInstanceCreationExpression;

/**
 * A {@link dagger.producers.Producer} creation expression for a production method on a production
 * component's {@linkplain dagger.producers.ProductionComponent#dependencies()} dependency} that
 * returns a {@link com.google.common.util.concurrent.ListenableFuture}.
 */
// TODO(dpb): Resolve with DependencyMethodProviderCreationExpression.
final class DependencyMethodProducerCreationExpression
    implements FrameworkInstanceCreationExpression {
  private final ComponentDependencyProductionBinding binding;
  private final ComponentImplementation componentImplementation;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final BindingGraph graph;

  @AssistedInject
  DependencyMethodProducerCreationExpression(
      @Assisted ComponentDependencyProductionBinding binding,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      BindingGraph graph) {
    this.binding = checkNotNull(binding);
    this.componentImplementation = componentImplementation;
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.graph = graph;
  }

  @Override
  public CodeBlock creationExpression() {
    ComponentRequirement dependency =
        graph.componentDescriptor().getDependencyThatDefinesMethod(binding.bindingElement().get());
    FieldSpec dependencyField =
        FieldSpec.builder(
                dependency.typeElement().getClassName(), dependency.variableName(), PRIVATE, FINAL)
            .initializer(
                componentRequirementExpressions.getExpressionDuringInitialization(
                    dependency,
                    // This isn't a real class name, but we want the requesting class for the
                    // expression to *not* be the same class as the component implementation,
                    // because it isn't... it's an anonymous inner class.
                    // TODO(cgdecker): If we didn't use an anonymous inner class here but instead
                    // generated a named nested class as with
                    // DependencyMethodProviderCreationExpression, we wouldn't need to deal with
                    // this and might be able to avoid potentially creating an extra field in the
                    // component?
                    componentImplementation.name().nestedClass("Anonymous")))
            .build();
    // TODO(b/70395982): Explore using a private static type instead of an anonymous class.
    XTypeName keyType = binding.key().type().xprocessing().asTypeName();
    return CodeBlock.of(
        "$L",
        anonymousClassBuilder("")
            .superclass(toJavaPoet(dependencyMethodProducerOf(keyType)))
            .addField(dependencyField)
            .addMethod(
                methodBuilder("callDependencyMethod")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(toJavaPoet(listenableFutureOf(keyType)))
                    .addStatement(
                        "return $N.$L()",
                        dependencyField,
                        asMethod(binding.bindingElement().get()).getJvmName())
                    .build())
            .build());
  }

  @AssistedFactory
  static interface Factory {
    DependencyMethodProducerCreationExpression create(ComponentDependencyProductionBinding binding);
  }
}

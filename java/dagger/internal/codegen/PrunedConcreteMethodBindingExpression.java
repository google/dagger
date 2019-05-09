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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.MissingBindingFactory;
import dagger.internal.codegen.ModifiableBindingMethods.ModifiableBindingMethod;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.producers.internal.MissingBindingProducer;
import java.util.Optional;

/**
 * A {@link BindingExpression} that implements a method that encapsulates a binding that is not part
 * of the binding graph when generating a final concrete implementation of a subcomponent. The
 * implementation throws an exception. It is assumed that a binding may remain missing in a valid
 * binding graph, because it's possible for there to be dependencies that are passively pruned when
 * a non-leaf binding is re-defined (such as when {@code @Provides} bindings override
 * {@code @Inject} bindings).
 *
 * <p>This method should never be invoked. If it is the exception indicates an issue within Dagger
 * itself.
 */
final class PrunedConcreteMethodBindingExpression extends BindingExpression {
  private static final CodeBlock METHOD_IMPLEMENTATION =
      CodeBlock.of(
          "throw new $T($S);",
          UnsupportedOperationException.class,
          "This binding is not part of the final binding graph. The key was requested by a binding "
              + "that was believed to possibly be part of the graph, but is no longer requested. "
              + "If this exception is thrown, it is the result of a Dagger bug.");

  PrunedConcreteMethodBindingExpression() {}

  @Override
  CodeBlock getModifiableBindingMethodImplementation(
      ModifiableBindingMethod modifiableBindingMethod,
      ComponentImplementation component,
      DaggerTypes types) {
    Optional<FrameworkType> frameworkType = modifiableBindingMethod.request().frameworkType();
    if (frameworkType.isPresent()) {
      // If we make initializations replaceable, we can do away with these classes and this logic
      // since the pruned framework instances will no longer be initialized
      switch (frameworkType.get()) {
        case PROVIDER:
          return missingFrameworkInstance(MissingBindingFactory.class);
        case PRODUCER_NODE:
          return missingFrameworkInstance(MissingBindingProducer.class);
      }
      throw new AssertionError(frameworkType);
    }
    return METHOD_IMPLEMENTATION;
  }

  private static CodeBlock missingFrameworkInstance(Class<?> factoryClass) {
    return CodeBlock.builder().addStatement("return $T.create()", factoryClass).build();
  }

  @Override
  final Expression getDependencyExpression(ClassName requestingClass) {
    throw new UnsupportedOperationException(
        "Requesting a dependency expression for a pruned binding.");
  }
}

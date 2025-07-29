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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.binding.BindingRequest.bindingRequest;
import static dagger.internal.codegen.xprocessing.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.xprocessing.XCodeBlocks.toParametersCodeBlock;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.base.ContributionType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.MultiboundSetBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.XCodeBlocks;
import dagger.internal.codegen.xprocessing.XExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;

/** A binding expression for multibound sets. */
final class SetRequestRepresentation extends RequestRepresentation {
  private final MultiboundSetBinding binding;
  private final BindingGraph graph;
  private final ComponentRequestRepresentations componentRequestRepresentations;
  private final CompilerOptions compilerOptions;
  private final XProcessingEnv processingEnv;

  @AssistedInject
  SetRequestRepresentation(
      @Assisted MultiboundSetBinding binding,
      BindingGraph graph,
      ComponentImplementation componentImplementation,
      ComponentRequestRepresentations componentRequestRepresentations,
      CompilerOptions compilerOptions,
      XProcessingEnv processingEnv) {
    this.binding = binding;
    this.graph = graph;
    this.componentRequestRepresentations = componentRequestRepresentations;
    this.compilerOptions = compilerOptions;
    this.processingEnv = processingEnv;
  }

  @Override
  XExpression getDependencyExpression(XClassName requestingClass) {
    // TODO(ronshapiro): We should also make an ImmutableSet version of SetFactory
    boolean isImmutableSetAvailable = isImmutableSetAvailable();
    // TODO(ronshapiro, gak): Use Sets.immutableEnumSet() if it's available?
    if (isImmutableSetAvailable && binding.dependencies().stream().allMatch(this::isSingleValue)) {
      return XExpression.create(
          immutableSetType(),
          XCodeBlock.builder()
              .add("%T.", XTypeNames.IMMUTABLE_SET)
              .add(maybeTypeParameter(requestingClass))
              .add(
                  "of(%L)",
                  binding.dependencies().stream()
                      .map(dependency -> getContributionExpression(dependency, requestingClass))
                      .collect(toParametersCodeBlock()))
              .build());
    }
    switch (binding.dependencies().size()) {
      case 0:
        return collectionsStaticFactoryInvocation(requestingClass, XCodeBlock.of("emptySet()"));
      case 1:
        {
          DependencyRequest dependency = getOnlyElement(binding.dependencies());
          XCodeBlock contributionExpression =
              getContributionExpression(dependency, requestingClass);
          if (isSingleValue(dependency)) {
            return collectionsStaticFactoryInvocation(
                requestingClass, XCodeBlock.of("singleton(%L)", contributionExpression));
          } else if (isImmutableSetAvailable) {
            return XExpression.create(
                immutableSetType(),
                XCodeBlock.builder()
                    .add("%T.", XTypeNames.IMMUTABLE_SET)
                    .add(maybeTypeParameter(requestingClass))
                    .add("copyOf(%L)", contributionExpression)
                    .build());
          }
        }
        // fall through
      default:
        XCodeBlock.Builder instantiation = XCodeBlock.builder();
        instantiation
            .add("%T.", isImmutableSetAvailable ? XTypeNames.IMMUTABLE_SET : XTypeNames.SET_BUILDER)
            .add(maybeTypeParameter(requestingClass));
        if (isImmutableSetBuilderWithExpectedSizeAvailable()) {
          instantiation.add("builderWithExpectedSize(%L)", binding.dependencies().size());
        } else if (isImmutableSetAvailable) {
          instantiation.add("builder()");
        } else {
          instantiation.add("newSetBuilder(%L)", binding.dependencies().size());
        }
        // TODO(b/430348351): We should avoid arbitrarily long chaining of methods like this
        // because it can cause StackOverflow in javac when building the AST for this generated
        // code. To fix this, we would need to ban direct inlining of the Set expression and wrap
        // the builder creation in a method that splits the chain into separate statements.
        for (DependencyRequest dependency : binding.dependencies()) {
          String builderMethod = isSingleValue(dependency) ? "add" : "addAll";
          instantiation.add(
              ".%L(%L)", builderMethod, getContributionExpression(dependency, requestingClass));
        }
        instantiation.add(".build()");
        return XExpression.create(
            isImmutableSetAvailable ? immutableSetType() : binding.key().type().xprocessing(),
            instantiation.build());
    }
  }

  private XType immutableSetType() {
    return processingEnv.getDeclaredType(
        processingEnv.requireTypeElement(XTypeNames.IMMUTABLE_SET),
        SetType.from(binding.key()).elementType());
  }

  private XCodeBlock getContributionExpression(
      DependencyRequest dependency, XClassName requestingClass) {
    RequestRepresentation bindingExpression =
        componentRequestRepresentations.getRequestRepresentation(bindingRequest(dependency));
    XCodeBlock expression = bindingExpression.getDependencyExpression(requestingClass).codeBlock();
    if (isSingleValue(dependency)) {
      return expression;
    }
    // Multivalue expressions call Set#addAll(Collection) and may require a cast to Collection.
    // TODO(b/211774331): Type casting should be Set after contributions to Set multibinding are
    // limited to be Set.
    // Add a cast to "(Collection)" when the contribution is a raw "Provider" type because the
    // "addAll()" method expects a collection. For example, ".addAll((Collection)
    // provideInaccessibleSetOfFoo.get())"
    return (!isTypeAccessibleFrom(
                binding.key().type().xprocessing(), requestingClass.getPackageName())
            // TODO(bcorso): Replace instanceof checks with validation on the binding.
            && (bindingExpression instanceof DerivedFromFrameworkInstanceRequestRepresentation
                || bindingExpression instanceof DelegateRequestRepresentation))
        ? XCodeBlocks.cast(expression, XTypeName.COLLECTION)
        : expression;
  }

  private XExpression collectionsStaticFactoryInvocation(
      XClassName requestingClass, XCodeBlock methodInvocation) {
    return XExpression.create(
        binding.key().type().xprocessing(),
        XCodeBlock.builder()
            .add("%T.", XTypeNames.JAVA_UTIL_COLLECTIONS)
            .add(maybeTypeParameter(requestingClass))
            .add(methodInvocation)
            .build());
  }

  private XCodeBlock maybeTypeParameter(XClassName requestingClass) {
    XType elementType = SetType.from(binding.key()).elementType();
    return isTypeAccessibleFrom(elementType, requestingClass.getPackageName())
        ? XCodeBlock.of("<%T>", elementType.asTypeName())
        : XCodeBlock.of("");
  }

  private boolean isSingleValue(DependencyRequest dependency) {
    return graph.contributionBinding(dependency.key())
        .contributionType()
        .equals(ContributionType.SET);
  }

  private boolean isImmutableSetBuilderWithExpectedSizeAvailable() {
    return isImmutableSetAvailable()
        && processingEnv.requireTypeElement(XTypeNames.IMMUTABLE_SET).getDeclaredMethods().stream()
            .anyMatch(method -> getSimpleName(method).contentEquals("builderWithExpectedSize"));
  }

  private boolean isImmutableSetAvailable() {
    return processingEnv.findTypeElement(XTypeNames.IMMUTABLE_SET) != null;
  }

  @AssistedFactory
  static interface Factory {
    SetRequestRepresentation create(MultiboundSetBinding binding);
  }
}

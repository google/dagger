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

package dagger.internal.codegen.bindinggraphvalidation;

import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.base.ElementFormatter.elementToString;
import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.base.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.base.Keys.isValidMembersInjectionKey;
import static dagger.internal.codegen.base.RequestKinds.dependencyCanBeProduction;
import static dagger.internal.codegen.binding.DependencyRequestFormatter.DOUBLE_INDENT;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isWildcard;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.binding.ComponentNodeImpl;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.MissingBinding;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.validation.DiagnosticMessageGenerator;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;

/** Reports errors for missing bindings. */
final class MissingBindingValidator extends ValidationBindingGraphPlugin {

  private final InjectBindingRegistry injectBindingRegistry;
  private final DiagnosticMessageGenerator.Factory diagnosticMessageGeneratorFactory;

  @Inject
  MissingBindingValidator(
      InjectBindingRegistry injectBindingRegistry,
      DiagnosticMessageGenerator.Factory diagnosticMessageGeneratorFactory) {
    this.injectBindingRegistry = injectBindingRegistry;
    this.diagnosticMessageGeneratorFactory = diagnosticMessageGeneratorFactory;
  }

  @Override
  public String pluginName() {
    return "Dagger/MissingBinding";
  }

  @Override
  public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    // Don't report missing bindings when validating a full binding graph or a graph built from a
    // subcomponent.
    if (graph.isFullBindingGraph() || graph.rootComponentNode().isSubcomponent()) {
      return;
    }
    // A missing binding might exist in a different component as unused binding, thus getting
    // stripped. Therefore, full graph needs to be traversed to capture the stripped bindings.
    if (!graph.missingBindings().isEmpty()) {
      requestVisitFullGraph(graph);
    }
  }

  @Override
  public void revisitFullGraph(
      BindingGraph prunedGraph, BindingGraph fullGraph, DiagnosticReporter diagnosticReporter) {
    prunedGraph
        .missingBindings()
        .forEach(
            missingBinding -> reportMissingBinding(missingBinding, fullGraph, diagnosticReporter));
  }

  private void reportMissingBinding(
      MissingBinding missingBinding,
      BindingGraph graph,
      DiagnosticReporter diagnosticReporter) {
    diagnosticReporter.reportComponent(
        ERROR,
        graph.componentNode(missingBinding.componentPath()).get(),
        missingBindingErrorMessage(missingBinding, graph)
            + diagnosticMessageGeneratorFactory.create(graph).getMessage(missingBinding)
            + alternativeBindingsMessage(missingBinding, graph)
            + similarBindingsMessage(missingBinding, graph));
  }

  private static ImmutableSet<Binding> getSimilarTypeBindings(
      BindingGraph graph, Key missingBindingKey) {
    return graph.bindings().stream()
        // Filter out multibinding contributions (users can't request these directly).
        .filter(binding -> binding.key().multibindingContributionIdentifier().isEmpty())
        // Filter out keys that are identical to the missing key (i.e. the binding exists in another
        // component, but we don't need to include those here because they're reported elsewhere).
        .filter(binding -> !binding.key().equals(missingBindingKey))
        .filter(binding -> isSimilar(binding.key(), missingBindingKey))
        .collect(toImmutableSet());
  }

  /**
   * Returns {@code true} if the two keys are similar.
   *
   * <p>Two keys are considered similar if they are equal except for the following differences:
   *
   * <ul>
   *   <li> qualifiers: (e.g. {@code @Qualified Foo} and {@code Foo} are similar)
   *   <li> variances: (e.g. {@code List<Foo>} and {@code List<? extends Foo>} are similar)
   *   <li> raw types: (e.g. {@code Set} and {@code Set<Foo>} are similar)
   * </ul>
   */
  private static boolean isSimilar(Key key, Key otherKey) {
    TypeDfsIterator typeIterator = new TypeDfsIterator(key.type().xprocessing());
    TypeDfsIterator otherTypeIterator = new TypeDfsIterator(otherKey.type().xprocessing());
    while (typeIterator.hasNext() || otherTypeIterator.hasNext()) {
      if (typeIterator.stack.size() != otherTypeIterator.stack.size()) {
        // Exit early if the stacks don't align. This implies the types have a different number
        // of type arguments, so we know the types must be dissimilar without checking further.
        return false;
      }
      // If next type is a raw type, don't add the type arguments of either type to the stack.
      boolean skipTypeArguments = typeIterator.isNextTypeRaw() || otherTypeIterator.isNextTypeRaw();
      TypeName typeName = typeIterator.next(skipTypeArguments);
      TypeName otherTypeName = otherTypeIterator.next(skipTypeArguments);
      if (!typeName.equals(otherTypeName)) {
        return false;
      }
    }
    return true;
  }

  private String missingBindingErrorMessage(MissingBinding missingBinding, BindingGraph graph) {
    Key key = missingBinding.key();
    StringBuilder errorMessage = new StringBuilder();
    // Wildcards should have already been checked by DependencyRequestValidator.
    verify(!isWildcard(key.type().xprocessing()), "unexpected wildcard request: %s", key);
    // TODO(ronshapiro): replace "provided" with "satisfied"?
    errorMessage.append(key).append(" cannot be provided without ");
    if (isValidImplicitProvisionKey(key)) {
      errorMessage.append("an @Inject constructor or ");
    }
    errorMessage.append("an @Provides-"); // TODO(dpb): s/an/a
    if (allIncomingDependenciesCanUseProduction(missingBinding, graph)) {
      errorMessage.append(" or @Produces-");
    }
    errorMessage.append("annotated method.");
    if (isValidMembersInjectionKey(key) && typeHasInjectionSites(key)) {
      errorMessage.append(
          " This type supports members injection but cannot be implicitly provided.");
    }
    return errorMessage.append("\n").toString();
  }

  private String alternativeBindingsMessage(
      MissingBinding missingBinding, BindingGraph graph) {
    ImmutableSet<Binding> alternativeBindings = graph.bindings(missingBinding.key());
    if (alternativeBindings.isEmpty()) {
      return "";
    }
    StringBuilder message = new StringBuilder();
    message.append("\n\nNote: ")
        .append(missingBinding.key())
        .append(" is provided in the following other components:");
    for (Binding alternativeBinding : alternativeBindings) {
      // Some alternative bindings appear multiple times because they were re-resolved in multiple
      // components (e.g. due to multibinding contributions). To avoid the noise, we only report
      // the binding where the module is contributed.
      if (alternativeBinding.contributingModule().isPresent()
          && !((ComponentNodeImpl) graph.componentNode(alternativeBinding.componentPath()).get())
              .componentDescriptor()
              .moduleTypes()
              .contains(alternativeBinding.contributingModule().get().xprocessing())) {
        continue;
      }
      message.append("\n").append(INDENT).append(asString(alternativeBinding));
    }
    return message.toString();
  }

  private String similarBindingsMessage(
      MissingBinding missingBinding, BindingGraph graph) {
    ImmutableSet<Binding> similarBindings =
        getSimilarTypeBindings(graph, missingBinding.key());
    if (similarBindings.isEmpty()) {
      return "";
    }
    StringBuilder message =
        new StringBuilder(
            "\n\nNote: A similar binding is provided in the following other components:");
    for (Binding similarBinding : similarBindings) {
      message
          .append("\n")
          .append(INDENT)
          .append(similarBinding.key())
          .append(" is provided at:")
          .append("\n")
          .append(DOUBLE_INDENT)
          .append(asString(similarBinding));
    }
    message.append("\n")
        .append(
            "(For Kotlin sources, you may need to use '@JvmSuppressWildcards' or '@JvmWildcard' if "
                + "you need to explicitly control the wildcards at a particular usage site.)");
    return message.toString();
  }

  private String asString(Binding binding) {
    return String.format(
        "[%s] %s",
        binding.componentPath().currentComponent().xprocessing().getQualifiedName(),
        binding.bindingElement().isPresent()
            ? elementToString(
                binding.bindingElement().get().xprocessing(),
                /* elideMethodParameterTypes= */ true)
            // For synthetic bindings just print the Binding#toString()
            : binding);
  }

  private boolean allIncomingDependenciesCanUseProduction(
      MissingBinding missingBinding, BindingGraph graph) {
    return graph.network().inEdges(missingBinding).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .allMatch(edge -> dependencyCanBeProduction(edge, graph));
  }

  private boolean typeHasInjectionSites(Key key) {
    return injectBindingRegistry
        .getOrFindMembersInjectionBinding(key)
        .map(binding -> !binding.injectionSites().isEmpty())
        .orElse(false);
  }

  /**
   * An iterator over a list of {@link TypeName}s produced by flattening a parameterized type. e.g.
   * {@code Map<Foo, List<Bar>>} to {@code [Map, Foo, List, Bar]}.
   *
   * <p>The iterator returns the bound when encounters a wildcard type.
   */
  private static class TypeDfsIterator {
    final Deque<XType> stack = new ArrayDeque<>();

    TypeDfsIterator(XType type) {
      stack.push(type);
    }

    public boolean hasNext() {
      return !stack.isEmpty();
    }

    public boolean isNextTypeRaw() {
      return XTypes.isRawParameterizedType(stack.peek());
    }

    public TypeName next(boolean skipTypeArguments) {
      XType next = stack.pop();
      if (isDeclared(next)) {
        if (!skipTypeArguments) {
          for (XType typeArgument : next.getTypeArguments()) {
            stack.push(typeArgument.extendsBoundOrSelf());
          }
        }
        return next.getTypeElement().getClassName();
      }
      // TODO(bcorso): consider handling other types like arrays.
      return next.getTypeName();
    }
  }
}

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
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSetMultimap;
import static dagger.spi.model.BindingKind.INJECTION;
import static dagger.spi.model.BindingKind.MEMBERS_INJECTION;
import static java.util.Comparator.comparing;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.binding.BindingDeclaration;
import dagger.internal.codegen.binding.BindingDeclarationFormatter;
import dagger.internal.codegen.binding.BindingNode;
import dagger.internal.codegen.binding.MultibindingDeclaration;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraph.ComponentNode;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.BindingKind;
import dagger.spi.model.ComponentPath;
import dagger.spi.model.DaggerElement;
import dagger.spi.model.DaggerTypeElement;
import dagger.spi.model.DiagnosticReporter;
import dagger.spi.model.Key;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.tools.Diagnostic.Kind;

/** Reports errors for conflicting bindings with the same key. */
final class DuplicateBindingsValidator implements BindingGraphPlugin {

  private static final Comparator<Binding> BY_LENGTH_OF_COMPONENT_PATH =
      comparing(binding -> binding.componentPath().components().size());

  private final BindingDeclarationFormatter bindingDeclarationFormatter;
  private final CompilerOptions compilerOptions;

  @Inject
  DuplicateBindingsValidator(
      BindingDeclarationFormatter bindingDeclarationFormatter, CompilerOptions compilerOptions) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
    this.compilerOptions = compilerOptions;
  }

  @Override
  public String pluginName() {
    return "Dagger/DuplicateBindings";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    // If two unrelated subcomponents have the same duplicate bindings only because they install the
    // same two modules, then fixing the error in one subcomponent will uncover the second
    // subcomponent to fix.
    // TODO(ronshapiro): Explore ways to address such underreporting without overreporting.
    Set<ImmutableSet<BindingElement>> reportedDuplicateBindingSets = new HashSet<>();
    duplicateBindingSets(bindingGraph)
        .forEach(
            duplicateBindings -> {
              // Only report each set of duplicate bindings once, ignoring the installed component.
              if (reportedDuplicateBindingSets.add(duplicateBindings.keySet())) {
                reportDuplicateBindings(duplicateBindings, bindingGraph, diagnosticReporter);
              }
            });
  }

  /**
   * Returns sets of duplicate bindings. Bindings are duplicates if they bind the same key and are
   * visible from the same component. Two bindings that differ only in the component that owns them
   * are not considered to be duplicates, because that means the same binding was "copied" down to a
   * descendant component because it depends on local multibindings or optional bindings. Hence each
   * "set" is represented as a multimap from binding element (ignoring component path) to binding.
   */
  private ImmutableSet<ImmutableSetMultimap<BindingElement, Binding>> duplicateBindingSets(
      BindingGraph bindingGraph) {
    return groupBindingsByKey(bindingGraph).stream()
        .flatMap(bindings -> mutuallyVisibleSubsets(bindings).stream())
        .map(BindingElement::index)
        .filter(duplicates -> duplicates.keySet().size() > 1)
        .collect(toImmutableSet());
  }

  private static ImmutableSet<ImmutableSet<Binding>> groupBindingsByKey(BindingGraph bindingGraph) {
    return valueSetsForEachKey(
        bindingGraph.bindings().stream()
            .filter(binding -> !binding.kind().equals(MEMBERS_INJECTION))
            .collect(toImmutableSetMultimap(Binding::key, binding -> binding)));
  }

  /**
   * Returns the subsets of the input set that contain bindings that are all visible from the same
   * component. A binding is visible from its component and all its descendants.
   */
  private static ImmutableSet<ImmutableSet<Binding>> mutuallyVisibleSubsets(
      Set<Binding> duplicateBindings) {
    ImmutableListMultimap<ComponentPath, Binding> bindingsByComponentPath =
        Multimaps.index(duplicateBindings, Binding::componentPath);
    ImmutableSetMultimap.Builder<ComponentPath, Binding> mutuallyVisibleBindings =
        ImmutableSetMultimap.builder();
    bindingsByComponentPath
        .asMap()
        .forEach(
            (componentPath, bindings) -> {
              mutuallyVisibleBindings.putAll(componentPath, bindings);
              for (ComponentPath ancestor = componentPath; !ancestor.atRoot(); ) {
                ancestor = ancestor.parent();
                ImmutableList<Binding> bindingsInAncestor = bindingsByComponentPath.get(ancestor);
                mutuallyVisibleBindings.putAll(componentPath, bindingsInAncestor);
              }
            });
    return valueSetsForEachKey(mutuallyVisibleBindings.build());
  }

  private void reportDuplicateBindings(
      ImmutableSetMultimap<BindingElement, Binding> duplicateBindings,
      BindingGraph bindingGraph,
      DiagnosticReporter diagnosticReporter) {
    if (explicitBindingConfictsWithInject(duplicateBindings.keySet())) {
      compilerOptions
          .explicitBindingConflictsWithInjectValidationType()
          .diagnosticKind()
          .ifPresent(
              diagnosticKind ->
                  reportExplicitBindingConflictsWithInject(
                      duplicateBindings,
                      diagnosticReporter,
                      diagnosticKind,
                      bindingGraph.rootComponentNode()));
      return;
    }
    ImmutableSet<Binding> bindings = ImmutableSet.copyOf(duplicateBindings.values());
    Binding oneBinding = bindings.asList().get(0);
    String message = bindings.stream().anyMatch(binding -> binding.kind().isMultibinding())
        ? incompatibleBindingsMessage(oneBinding, bindings, bindingGraph)
        : duplicateBindingMessage(oneBinding, bindings, bindingGraph);
    if (compilerOptions.experimentalDaggerErrorMessages()) {
      diagnosticReporter.reportComponent(
          ERROR,
          bindingGraph.rootComponentNode(),
          message);
    } else {
      diagnosticReporter.reportBinding(
          ERROR,
          oneBinding,
          message);
    }
  }

  /**
   * Returns {@code true} if the bindings contain one {@code @Inject} binding and one that isn't.
   */
  private static boolean explicitBindingConfictsWithInject(
      ImmutableSet<BindingElement> duplicateBindings) {
    ImmutableMultiset<BindingKind> bindingKinds =
        Multimaps.index(duplicateBindings, BindingElement::bindingKind).keys();
    return bindingKinds.count(INJECTION) == 1 && bindingKinds.size() == 2;
  }

  private void reportExplicitBindingConflictsWithInject(
      ImmutableSetMultimap<BindingElement, Binding> duplicateBindings,
      DiagnosticReporter diagnosticReporter,
      Kind diagnosticKind,
      ComponentNode rootComponent) {
    Binding injectBinding =
        rootmostBindingWithKind(k -> k.equals(INJECTION), duplicateBindings.values());
    Binding explicitBinding =
        rootmostBindingWithKind(k -> !k.equals(INJECTION), duplicateBindings.values());
    StringBuilder message =
        new StringBuilder()
            .append(explicitBinding.key())
            .append(" is bound multiple times:")
            .append(formatWithComponentPath(injectBinding))
            .append(formatWithComponentPath(explicitBinding))
            .append(
                "\nThis condition was never validated before, and will soon be an error. "
                    + "See https://dagger.dev/conflicting-inject.");

    if (compilerOptions.experimentalDaggerErrorMessages()) {
      diagnosticReporter.reportComponent(diagnosticKind, rootComponent, message.toString());
    } else {
      diagnosticReporter.reportBinding(diagnosticKind, explicitBinding, message.toString());
    }
  }

  private String formatWithComponentPath(Binding binding) {
    return String.format(
        "\n%s%s [%s]",
        Formatter.INDENT,
        bindingDeclarationFormatter.format(((BindingNode) binding).delegate()),
        binding.componentPath());
  }

  private String duplicateBindingMessage(
      Binding oneBinding, ImmutableSet<Binding> duplicateBindings, BindingGraph graph) {
    StringBuilder message =
        new StringBuilder().append(oneBinding.key()).append(" is bound multiple times:");
    formatDeclarations(message, 1, declarations(graph, duplicateBindings));
    if (compilerOptions.experimentalDaggerErrorMessages()) {
      message.append(String.format("\n%sin component: [%s]", INDENT, oneBinding.componentPath()));
    }
    return message.toString();
  }

  private String incompatibleBindingsMessage(
      Binding oneBinding, ImmutableSet<Binding> duplicateBindings, BindingGraph graph) {
    Key key = oneBinding.key();
    ImmutableSet<dagger.spi.model.Binding> multibindings =
        duplicateBindings.stream()
            .filter(binding -> binding.kind().isMultibinding())
            .collect(toImmutableSet());
    verify(
        multibindings.size() == 1, "expected only one multibinding for %s: %s", key, multibindings);
    StringBuilder message = new StringBuilder();
    java.util.Formatter messageFormatter = new java.util.Formatter(message);
    messageFormatter.format("%s has incompatible bindings or declarations:\n", key);
    message.append(INDENT);
    dagger.spi.model.Binding multibinding = getOnlyElement(multibindings);
    messageFormatter.format("%s bindings and declarations:", multibindingTypeString(multibinding));
    formatDeclarations(message, 2, declarations(graph, multibindings));

    Set<dagger.spi.model.Binding> uniqueBindings =
        Sets.filter(duplicateBindings, binding -> !binding.equals(multibinding));
    message.append('\n').append(INDENT).append("Unique bindings and declarations:");
    formatDeclarations(
        message,
        2,
        Sets.filter(
            declarations(graph, uniqueBindings),
            declaration -> !(declaration instanceof MultibindingDeclaration)));
    if (compilerOptions.experimentalDaggerErrorMessages()) {
      message.append(String.format("\n%sin component: [%s]", INDENT, oneBinding.componentPath()));
    }
    return message.toString();
  }

  private void formatDeclarations(
      StringBuilder builder,
      int indentLevel,
      Iterable<? extends BindingDeclaration> bindingDeclarations) {
    bindingDeclarationFormatter.formatIndentedList(
        builder, ImmutableList.copyOf(bindingDeclarations), indentLevel);
  }

  private ImmutableSet<BindingDeclaration> declarations(
      BindingGraph graph, Set<dagger.spi.model.Binding> bindings) {
    return bindings.stream()
        .flatMap(binding -> declarations(graph, binding).stream())
        .distinct()
        .sorted(BindingDeclaration.COMPARATOR)
        .collect(toImmutableSet());
  }

  private ImmutableSet<BindingDeclaration> declarations(
      BindingGraph graph, dagger.spi.model.Binding binding) {
    ImmutableSet.Builder<BindingDeclaration> declarations = ImmutableSet.builder();
    BindingNode bindingNode = (BindingNode) binding;
    bindingNode.associatedDeclarations().forEach(declarations::add);
    if (bindingDeclarationFormatter.canFormat(bindingNode.delegate())) {
      declarations.add(bindingNode.delegate());
    } else {
      graph.requestedBindings(binding).stream()
          .flatMap(requestedBinding -> declarations(graph, requestedBinding).stream())
          .forEach(declarations::add);
    }
    return declarations.build();
  }

  private String multibindingTypeString(dagger.spi.model.Binding multibinding) {
    switch (multibinding.kind()) {
      case MULTIBOUND_MAP:
        return "Map";
      case MULTIBOUND_SET:
        return "Set";
      default:
        throw new AssertionError(multibinding);
    }
  }

  private static <E> ImmutableSet<ImmutableSet<E>> valueSetsForEachKey(Multimap<?, E> multimap) {
    return multimap.asMap().values().stream().map(ImmutableSet::copyOf).collect(toImmutableSet());
  }

  /** Returns the binding of the given kind that is closest to the root component. */
  private static Binding rootmostBindingWithKind(
      Predicate<BindingKind> bindingKindPredicate, ImmutableCollection<Binding> bindings) {
    return bindings.stream()
        .filter(b -> bindingKindPredicate.test(b.kind()))
        .min(BY_LENGTH_OF_COMPONENT_PATH)
        .get();
  }

  /** The identifying information about a binding, excluding its {@link Binding#componentPath()}. */
  @AutoValue
  abstract static class BindingElement {

    abstract BindingKind bindingKind();

    abstract Optional<XElement> bindingElement();

    abstract Optional<XTypeElement> contributingModule();

    static ImmutableSetMultimap<BindingElement, Binding> index(Set<Binding> bindings) {
      return bindings.stream().collect(toImmutableSetMultimap(BindingElement::forBinding, b -> b));
    }

    private static BindingElement forBinding(Binding binding) {
      return new AutoValue_DuplicateBindingsValidator_BindingElement(
          binding.kind(),
          binding.bindingElement().map(DaggerElement::xprocessing),
          binding.contributingModule().map(DaggerTypeElement::xprocessing));
    }
  }
}

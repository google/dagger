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

import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSetMultimap;
import static dagger.internal.codegen.model.BindingKind.INJECTION;
import static dagger.internal.codegen.model.BindingKind.MEMBERS_INJECTION;
import static java.util.Comparator.comparing;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import dagger.internal.codegen.base.Formatter;
import dagger.internal.codegen.binding.BindingNode;
import dagger.internal.codegen.binding.Declaration;
import dagger.internal.codegen.binding.DeclarationFormatter;
import dagger.internal.codegen.binding.MultibindingDeclaration;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.BindingKind;
import dagger.internal.codegen.model.ComponentPath;
import dagger.internal.codegen.model.DaggerAnnotation;
import dagger.internal.codegen.model.DaggerElement;
import dagger.internal.codegen.model.DaggerTypeElement;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.model.Key.MultibindingContributionIdentifier;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import dagger.internal.codegen.xprocessing.XTypes;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.tools.Diagnostic;

/** Reports errors for conflicting bindings with the same key. */
final class DuplicateBindingsValidator extends ValidationBindingGraphPlugin {

  private static final Comparator<Binding> BY_LENGTH_OF_COMPONENT_PATH =
      comparing(binding -> binding.componentPath().components().size());

  private final DeclarationFormatter declarationFormatter;
  private final CompilerOptions compilerOptions;

  @Inject
  DuplicateBindingsValidator(
      DeclarationFormatter declarationFormatter, CompilerOptions compilerOptions) {
    this.declarationFormatter = declarationFormatter;
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
    Set<ImmutableSet<BindingWithoutComponent>> reportedDuplicateBindingSets = new HashSet<>();
    duplicateBindingSets(bindingGraph)
        .forEach(
            duplicateBindings -> {
              // Only report each set of duplicate bindings once, ignoring the installed component.
              if (reportedDuplicateBindingSets.add(duplicateBindings.keySet())) {
                reportErrors(duplicateBindings, bindingGraph, diagnosticReporter);
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
  private ImmutableSet<ImmutableSetMultimap<BindingWithoutComponent, Binding>> duplicateBindingSets(
      BindingGraph bindingGraph) {
    return groupBindingsByKey(bindingGraph).stream()
        .flatMap(bindings -> mutuallyVisibleSubsets(bindings).stream())
        .map(BindingWithoutComponent::index)
        .filter(duplicates -> duplicates.keySet().size() > 1)
        .collect(toImmutableSet());
  }

  private ImmutableSet<ImmutableSet<Binding>> groupBindingsByKey(BindingGraph bindingGraph) {
    return valueSetsForEachKey(
        bindingGraph.bindings().stream()
            .filter(binding -> !binding.kind().equals(MEMBERS_INJECTION))
            .collect(
                toImmutableSetMultimap(
                    binding ->
                        // If the "ignoreProvisionKeyWildcards" flag is enabled then ignore the
                        // variance in the key types here so that Foo<Bar> and Foo<? extends Bar>
                        // get grouped into the same set (i.e. as duplicates).
                        KeyWithTypeEquivalence.forKey(
                            binding.key(),
                            compilerOptions.ignoreProvisionKeyWildcards()
                                ? XTypes.equivalenceIgnoringVariance()
                                : XTypes.equivalence()),
                    binding -> binding)));
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

  private void reportErrors(
      ImmutableSetMultimap<BindingWithoutComponent, Binding> duplicateBindings,
      BindingGraph bindingGraph,
      DiagnosticReporter diagnosticReporter) {
    if (explicitBindingConfictsWithInject(duplicateBindings.keySet())) {
      compilerOptions
          .explicitBindingConflictsWithInjectValidationType()
          .diagnosticKind()
          .ifPresent(
              diagnosticKind ->
                  reportExplicitBindingConflictsWithInject(
                      duplicateBindings.values(),
                      diagnosticReporter,
                      diagnosticKind,
                      bindingGraph.rootComponentNode()));
      return;
    }

    reportDuplicateBindings(duplicateBindings.values(), bindingGraph, diagnosticReporter);
  }

  /**
   * Returns {@code true} if the bindings contain one {@code @Inject} binding and one that isn't.
   */
  private static boolean explicitBindingConfictsWithInject(
      ImmutableSet<BindingWithoutComponent> duplicateBindings) {
    ImmutableMultiset<BindingKind> bindingKinds =
        Multimaps.index(duplicateBindings, BindingWithoutComponent::bindingKind).keys();
    return bindingKinds.count(INJECTION) == 1 && bindingKinds.size() == 2;
  }

  private void reportExplicitBindingConflictsWithInject(
      ImmutableCollection<Binding> duplicateBindings,
      DiagnosticReporter diagnosticReporter,
      Diagnostic.Kind diagnosticKind,
      ComponentNode rootComponent) {
    Binding injectBinding = rootmostBindingWithKind(k -> k.equals(INJECTION), duplicateBindings);
    Binding explicitBinding = rootmostBindingWithKind(k -> !k.equals(INJECTION), duplicateBindings);
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
        declarationFormatter.format(((BindingNode) binding).delegate()),
        binding.componentPath());
  }

  private void reportDuplicateBindings(
      ImmutableCollection<Binding> duplicateBindings,
      BindingGraph graph,
      DiagnosticReporter diagnosticReporter) {
    StringBuilder message = new StringBuilder();
    Binding oneBinding = duplicateBindings.asList().get(0);
    ImmutableSet<Binding> multibindings =
        duplicateBindings.stream()
            .filter(binding -> binding.kind().isMultibinding())
            .collect(toImmutableSet());
    if (multibindings.isEmpty()) {
      message.append(oneBinding.key()).append(" is bound multiple times:");
      formatDeclarations(message, 2, declarations(graph, duplicateBindings));
    } else {
      Binding oneMultibinding = multibindings.asList().get(0);
      message.append(oneMultibinding.key()).append(" has incompatible bindings or declarations:\n");
      message
          .append(INDENT)
          .append(multibindingTypeString(oneMultibinding))
          .append(" bindings and declarations:");
      formatDeclarations(message, 2, declarations(graph, multibindings));
      ImmutableSet<Declaration> uniqueBindingDeclarations =
          duplicateBindings.stream()
              .filter(binding -> !binding.kind().isMultibinding())
              .flatMap(binding -> declarations(graph, binding).stream())
              .filter(declaration -> !(declaration instanceof MultibindingDeclaration))
              .collect(toImmutableSet());
      if (!uniqueBindingDeclarations.isEmpty()) {
        message.append('\n').append(INDENT).append("Unique bindings and declarations:");
        formatDeclarations(message, 2, uniqueBindingDeclarations);
      }
    }

    if (compilerOptions.experimentalDaggerErrorMessages()) {
      message.append(String.format("\n%sin component: [%s]", INDENT, oneBinding.componentPath()));
      diagnosticReporter.reportComponent(ERROR, graph.rootComponentNode(), message.toString());
    } else {
      diagnosticReporter.reportBinding(ERROR, oneBinding, message.toString());
    }
  }

  private void formatDeclarations(
      StringBuilder builder,
      int indentLevel,
      Iterable<? extends Declaration> bindingDeclarations) {
    declarationFormatter.formatIndentedList(
        builder, ImmutableList.copyOf(bindingDeclarations), indentLevel);
  }

  private ImmutableSet<Declaration> declarations(
      BindingGraph graph, ImmutableCollection<Binding> bindings) {
    return bindings.stream()
        .flatMap(binding -> declarations(graph, binding).stream())
        .distinct()
        .sorted(Declaration.COMPARATOR)
        .collect(toImmutableSet());
  }

  private ImmutableSet<Declaration> declarations(BindingGraph graph, Binding binding) {
    ImmutableSet.Builder<Declaration> declarations = ImmutableSet.builder();
    BindingNode bindingNode = (BindingNode) binding;
    bindingNode.associatedDeclarations().forEach(declarations::add);
    if (declarationFormatter.canFormat(bindingNode.delegate())) {
      declarations.add(bindingNode.delegate());
    } else {
      graph.requestedBindings(binding).stream()
          .flatMap(requestedBinding -> declarations(graph, requestedBinding).stream())
          .forEach(declarations::add);
    }
    return declarations.build();
  }

  private String multibindingTypeString(Binding multibinding) {
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
  abstract static class BindingWithoutComponent {

    abstract BindingKind bindingKind();

    abstract Key bindingKey();

    abstract Optional<XElement> bindingElement();

    abstract Optional<XTypeElement> contributingModule();

    static ImmutableSetMultimap<BindingWithoutComponent, Binding> index(Set<Binding> bindings) {
      return bindings.stream()
          .collect(toImmutableSetMultimap(BindingWithoutComponent::forBinding, b -> b));
    }

    private static BindingWithoutComponent forBinding(Binding binding) {
      return new AutoValue_DuplicateBindingsValidator_BindingWithoutComponent(
          binding.kind(),
          binding.key(),
          binding.bindingElement().map(DaggerElement::xprocessing),
          binding.contributingModule().map(DaggerTypeElement::xprocessing));
    }
  }


  /** The identifying information about a key with the given type equivalence. */
  @AutoValue
  abstract static class KeyWithTypeEquivalence {
    abstract Optional<DaggerAnnotation> qualifier();

    abstract Equivalence.Wrapper<XType> wrappedType();

    abstract Optional<MultibindingContributionIdentifier> multibindingContributionIdentifier();

    private static KeyWithTypeEquivalence forKey(Key key, Equivalence<XType> typeEquivalence) {
      return new AutoValue_DuplicateBindingsValidator_KeyWithTypeEquivalence(
          key.qualifier(),
          typeEquivalence.wrap(key.type().xprocessing()),
          key.multibindingContributionIdentifier());
    }
  }
}

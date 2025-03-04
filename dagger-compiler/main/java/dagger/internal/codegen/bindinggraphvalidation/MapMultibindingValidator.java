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

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.model.BindingKind.MULTIBOUND_MAP;
import static dagger.internal.codegen.xprocessing.XAnnotations.asClassName;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.codegen.XClassName;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.binding.BindingNode;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.Declaration;
import dagger.internal.codegen.binding.DeclarationFormatter;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.model.Key;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import dagger.internal.codegen.xprocessing.XAnnotations;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

/**
 * Reports an error for any map binding with either more than one contribution with the same map key
 * or contributions with inconsistent map key annotation types.
 */
final class MapMultibindingValidator extends ValidationBindingGraphPlugin {

  private final DeclarationFormatter declarationFormatter;
  private final KeyFactory keyFactory;

  @Inject
  MapMultibindingValidator(
      DeclarationFormatter declarationFormatter, KeyFactory keyFactory) {
    this.declarationFormatter = declarationFormatter;
    this.keyFactory = keyFactory;
  }

  @Override
  public String pluginName() {
    return "Dagger/MapKeys";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    mapMultibindings(bindingGraph)
        .forEach(
            binding -> {
              ImmutableSet<ContributionBinding> contributions =
                  mapBindingContributions(binding, bindingGraph);
              checkForDuplicateMapKeys(binding, contributions, diagnosticReporter);
              checkForInconsistentMapKeyAnnotationTypes(binding, contributions, diagnosticReporter);
            });
  }

  /**
   * Returns the map multibindings in the binding graph. If a graph contains bindings for more than
   * one of the following for the same {@code K} and {@code V}, then only the first one found will
   * be returned so we don't report the same map contribution problem more than once.
   *
   * <ol>
   *   <li>{@code Map<K, V>}
   *   <li>{@code Map<K, Provider<V>>}
   *   <li>{@code Map<K, Producer<V>>}
   * </ol>
   */
  private ImmutableSet<Binding> mapMultibindings(BindingGraph bindingGraph) {
    Set<Key> visitedKeys = new HashSet<>();
    return bindingGraph.bindings().stream()
        .filter(binding -> binding.kind().equals(MULTIBOUND_MAP))
        // Sort by the order of the value in the RequestKind:
        // (Map<K, V>, then Map<K, Provider<V>>, then Map<K, Producer<V>>).
        .sorted(Comparator.comparing(binding -> MapType.from(binding.key()).valueRequestKind()))
        // Only take the first binding (post sorting) per unwrapped key.
        .filter(binding -> visitedKeys.add(unwrappedKey(binding)))
        .collect(toImmutableSet());
  }

  private Key unwrappedKey(Binding binding) {
    return keyFactory.unwrapMapValueType(binding.key());
  }

  private ImmutableSet<ContributionBinding> mapBindingContributions(
      Binding binding, BindingGraph bindingGraph) {
    checkArgument(binding.kind().equals(MULTIBOUND_MAP));
    return bindingGraph.requestedBindings(binding).stream()
        .map(b -> (BindingNode) b)
        .map(b -> (ContributionBinding) b.delegate())
        .collect(toImmutableSet());
  }

  private void checkForDuplicateMapKeys(
      Binding multiboundMapBinding,
      ImmutableSet<ContributionBinding> contributions,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSetMultimap<?, ContributionBinding> contributionsByMapKey =
        ImmutableSetMultimap.copyOf(
            Multimaps.index(
                contributions,
                // Note: We're wrapping in XAnnotations.equivalence() to get proper equals/hashcode.
                binding -> binding.mapKey().map(XAnnotations.equivalence()::wrap)));

    for (Set<ContributionBinding> contributionsForOneMapKey :
        Multimaps.asMap(contributionsByMapKey).values()) {
      if (contributionsForOneMapKey.size() > 1) {
        diagnosticReporter.reportBinding(
            ERROR,
            multiboundMapBinding,
            duplicateMapKeyErrorMessage(contributionsForOneMapKey, multiboundMapBinding.key()));
      }
    }
  }

  private void checkForInconsistentMapKeyAnnotationTypes(
      Binding multiboundMapBinding,
      ImmutableSet<ContributionBinding> contributions,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSetMultimap<XClassName, ContributionBinding> contributionsByMapKeyAnnotationType =
        ImmutableSetMultimap.copyOf(
            Multimaps.index(contributions, mapBinding -> asClassName(mapBinding.mapKey().get())));

    if (contributionsByMapKeyAnnotationType.keySet().size() > 1) {
      diagnosticReporter.reportBinding(
          ERROR,
          multiboundMapBinding,
          inconsistentMapKeyAnnotationTypesErrorMessage(
              contributionsByMapKeyAnnotationType, multiboundMapBinding.key()));
    }
  }

  private String inconsistentMapKeyAnnotationTypesErrorMessage(
      ImmutableSetMultimap<XClassName, ContributionBinding> contributionsByMapKeyAnnotationType,
      Key mapBindingKey) {
    StringBuilder message =
        new StringBuilder(mapBindingKey.toString())
            .append(" uses more than one @MapKey annotation type");
    Multimaps.asMap(contributionsByMapKeyAnnotationType)
        .forEach(
            (annotationType, contributions) -> {
              message.append('\n').append(INDENT).append(annotationType).append(':');
              declarationFormatter.formatIndentedList(message, contributions, 2);
            });
    return message.toString();
  }

  private String duplicateMapKeyErrorMessage(
      Set<ContributionBinding> contributionsForOneMapKey, Key mapBindingKey) {
    StringBuilder message =
        new StringBuilder("The same map key is bound more than once for ").append(mapBindingKey);

    declarationFormatter.formatIndentedList(
        message,
        ImmutableList.sortedCopyOf(Declaration.COMPARATOR, contributionsForOneMapKey),
        1);
    return message.toString();
  }
}

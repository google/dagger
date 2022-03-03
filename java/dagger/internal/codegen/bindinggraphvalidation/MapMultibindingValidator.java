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
import static com.google.common.collect.Multimaps.filterKeys;
import static dagger.internal.codegen.base.Formatter.INDENT;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSetMultimap;
import static dagger.spi.model.BindingKind.MULTIBOUND_MAP;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.binding.BindingDeclaration;
import dagger.internal.codegen.binding.BindingDeclarationFormatter;
import dagger.internal.codegen.binding.BindingNode;
import dagger.internal.codegen.binding.ContributionBinding;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.DiagnosticReporter;
import dagger.spi.model.Key;
import java.util.Set;
import javax.inject.Inject;

/**
 * Reports an error for any map binding with either more than one contribution with the same map key
 * or contributions with inconsistent map key annotation types.
 */
final class MapMultibindingValidator implements BindingGraphPlugin {

  private final BindingDeclarationFormatter bindingDeclarationFormatter;
  private final KeyFactory keyFactory;

  @Inject
  MapMultibindingValidator(
      BindingDeclarationFormatter bindingDeclarationFormatter, KeyFactory keyFactory) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
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
    ImmutableSetMultimap<Key, Binding> mapMultibindings =
        bindingGraph.bindings().stream()
            .filter(node -> node.kind().equals(MULTIBOUND_MAP))
            .collect(toImmutableSetMultimap(Binding::key, node -> node));

    // Mutlbindings for Map<K, V>
    SetMultimap<Key, Binding> plainValueMapMultibindings =
        filterKeys(mapMultibindings, key -> !MapType.from(key).valuesAreFrameworkType());

    // Multibindings for Map<K, Provider<V>> where Map<K, V> isn't in plainValueMapMultibindings
    SetMultimap<Key, Binding> providerValueMapMultibindings =
        filterKeys(
            mapMultibindings,
            key ->
                MapType.from(key).valuesAreTypeOf(TypeNames.PROVIDER)
                    && !plainValueMapMultibindings.containsKey(keyFactory.unwrapMapValueType(key)));

    // Multibindings for Map<K, Producer<V>> where Map<K, V> isn't in plainValueMapMultibindings and
    // Map<K, Provider<V>> isn't in providerValueMapMultibindings
    SetMultimap<Key, Binding> producerValueMapMultibindings =
        filterKeys(
            mapMultibindings,
            key ->
                MapType.from(key).valuesAreTypeOf(TypeNames.PRODUCER)
                    && !plainValueMapMultibindings.containsKey(keyFactory.unwrapMapValueType(key))
                    && !providerValueMapMultibindings.containsKey(
                        keyFactory
                            .rewrapMapKey(key, TypeNames.PRODUCER, TypeNames.PROVIDER)
                            .get()));

    return new ImmutableSet.Builder<Binding>()
        .addAll(plainValueMapMultibindings.values())
        .addAll(providerValueMapMultibindings.values())
        .addAll(producerValueMapMultibindings.values())
        .build();
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
        ImmutableSetMultimap.copyOf(Multimaps.index(contributions, ContributionBinding::mapKey));

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
    ImmutableSetMultimap<ClassName, ContributionBinding> contributionsByMapKeyAnnotationType =
        ImmutableSetMultimap.copyOf(
            Multimaps.index(contributions, mapBinding -> mapBinding.mapKey().get().className()));

    if (contributionsByMapKeyAnnotationType.keySet().size() > 1) {
      diagnosticReporter.reportBinding(
          ERROR,
          multiboundMapBinding,
          inconsistentMapKeyAnnotationTypesErrorMessage(
              contributionsByMapKeyAnnotationType, multiboundMapBinding.key()));
    }
  }

  private String inconsistentMapKeyAnnotationTypesErrorMessage(
      ImmutableSetMultimap<ClassName, ContributionBinding> contributionsByMapKeyAnnotationType,
      Key mapBindingKey) {
    StringBuilder message =
        new StringBuilder(mapBindingKey.toString())
            .append(" uses more than one @MapKey annotation type");
    Multimaps.asMap(contributionsByMapKeyAnnotationType)
        .forEach(
            (annotationType, contributions) -> {
              message.append('\n').append(INDENT).append(annotationType).append(':');
              bindingDeclarationFormatter.formatIndentedList(message, contributions, 2);
            });
    return message.toString();
  }

  private String duplicateMapKeyErrorMessage(
      Set<ContributionBinding> contributionsForOneMapKey, Key mapBindingKey) {
    StringBuilder message =
        new StringBuilder("The same map key is bound more than once for ").append(mapBindingKey);

    bindingDeclarationFormatter.formatIndentedList(
        message,
        ImmutableList.sortedCopyOf(BindingDeclaration.COMPARATOR, contributionsForOneMapKey),
        1);
    return message.toString();
  }
}

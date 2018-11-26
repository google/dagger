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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Multimaps.filterKeys;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.DaggerStreams.toImmutableSetMultimap;
import static dagger.internal.codegen.Formatter.INDENT;
import static dagger.model.BindingKind.MULTIBOUND_MAP;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import dagger.model.BindingGraph;
import dagger.model.Key;
import dagger.producers.Producer;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.type.DeclaredType;

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
  private ImmutableSet<dagger.model.Binding> mapMultibindings(BindingGraph bindingGraph) {
    ImmutableSetMultimap<Key, dagger.model.Binding> mapMultibindings =
        bindingGraph.bindings().stream()
            .filter(node -> node.kind().equals(MULTIBOUND_MAP))
            .collect(toImmutableSetMultimap(dagger.model.Binding::key, node -> node));

    // Mutlbindings for Map<K, V>
    SetMultimap<Key, dagger.model.Binding> plainValueMapMultibindings =
        filterKeys(mapMultibindings, key -> !MapType.from(key).valuesAreFrameworkType());

    // Multibindings for Map<K, Provider<V>> where Map<K, V> isn't in plainValueMapMultibindings
    SetMultimap<Key, dagger.model.Binding> providerValueMapMultibindings =
        filterKeys(
            mapMultibindings,
            key ->
                MapType.from(key).valuesAreTypeOf(Provider.class)
                    && !plainValueMapMultibindings.containsKey(keyFactory.unwrapMapValueType(key)));

    // Multibindings for Map<K, Producer<V>> where Map<K, V> isn't in plainValueMapMultibindings and
    // Map<K, Provider<V>> isn't in providerValueMapMultibindings
    SetMultimap<Key, dagger.model.Binding> producerValueMapMultibindings =
        filterKeys(
            mapMultibindings,
            key ->
                MapType.from(key).valuesAreTypeOf(Producer.class)
                    && !plainValueMapMultibindings.containsKey(keyFactory.unwrapMapValueType(key))
                    && !providerValueMapMultibindings.containsKey(
                        keyFactory.rewrapMapKey(key, Producer.class, Provider.class).get()));

    return new ImmutableSet.Builder<dagger.model.Binding>()
        .addAll(plainValueMapMultibindings.values())
        .addAll(providerValueMapMultibindings.values())
        .addAll(producerValueMapMultibindings.values())
        .build();
  }

  private ImmutableSet<ContributionBinding> mapBindingContributions(
      dagger.model.Binding binding, BindingGraph bindingGraph) {
    checkArgument(binding.kind().equals(MULTIBOUND_MAP));
    return bindingGraph.requestedBindings(binding).stream()
        .map(b -> (BindingNode) b)
        .map(b -> (ContributionBinding) b.delegate())
        .collect(toImmutableSet());
  }

  private void checkForDuplicateMapKeys(
      dagger.model.Binding multiboundMapBinding,
      ImmutableSet<ContributionBinding> contributions,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSetMultimap<Object, ContributionBinding> contributionsByMapKey =
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
      dagger.model.Binding multiboundMapBinding,
      ImmutableSet<ContributionBinding> contributions,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSetMultimap<Equivalence.Wrapper<DeclaredType>, ContributionBinding>
        contributionsByMapKeyAnnotationType = indexByMapKeyAnnotationType(contributions);

    if (contributionsByMapKeyAnnotationType.keySet().size() > 1) {
      diagnosticReporter.reportBinding(
          ERROR,
          multiboundMapBinding,
          inconsistentMapKeyAnnotationTypesErrorMessage(
              contributionsByMapKeyAnnotationType, multiboundMapBinding.key()));
    }
  }

  private static ImmutableSetMultimap<Equivalence.Wrapper<DeclaredType>, ContributionBinding>
      indexByMapKeyAnnotationType(ImmutableSet<ContributionBinding> contributions) {
    return ImmutableSetMultimap.copyOf(
        Multimaps.index(
            contributions,
            mapBinding ->
                MoreTypes.equivalence()
                    .wrap(mapBinding.mapKeyAnnotation().get().getAnnotationType())));
  }

  private String inconsistentMapKeyAnnotationTypesErrorMessage(
      ImmutableSetMultimap<Equivalence.Wrapper<DeclaredType>, ContributionBinding>
          contributionsByMapKeyAnnotationType,
      Key mapBindingKey) {
    StringBuilder message =
        new StringBuilder(mapBindingKey.toString())
            .append(" uses more than one @MapKey annotation type");
    Multimaps.asMap(contributionsByMapKeyAnnotationType)
        .forEach(
            (annotationType, contributions) -> {
              message.append('\n').append(INDENT).append(annotationType.get()).append(':');
              bindingDeclarationFormatter.formatIndentedList(message, contributions, 2);
            });
    return message.toString();
  }

  private String duplicateMapKeyErrorMessage(
      Set<ContributionBinding> contributionsForOneMapKey, Key mapBindingKey) {
    StringBuilder message =
        new StringBuilder("The same map key is bound more than once for ").append(mapBindingKey);
    bindingDeclarationFormatter.formatIndentedList(message, contributionsForOneMapKey, 1);
    return message.toString();
  }
}

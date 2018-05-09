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
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.ErrorMessages.DUPLICATE_SIZE_LIMIT;
import static dagger.internal.codegen.Formatter.INDENT;
import static dagger.model.BindingKind.MULTIBOUND_MAP;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.Key;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.type.DeclaredType;

/**
 * Reports an error for any map binding with either more than one contribution with the same map key
 * or contributions with inconsistent map key annotation types.
 */
final class MapMultibindingValidation implements BindingGraphPlugin {

  private final BindingDeclarationFormatter bindingDeclarationFormatter;

  @Inject
  MapMultibindingValidation(BindingDeclarationFormatter bindingDeclarationFormatter) {
    this.bindingDeclarationFormatter = bindingDeclarationFormatter;
  }

  @Override
  public String pluginName() {
    return "Dagger/MapKeys";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    for (BindingNode node : bindingGraph.bindingNodes()) {
      if (node.binding().kind().equals(MULTIBOUND_MAP)) {
        ImmutableSet<ContributionBinding> contributions =
            mapBindingContributions(node, bindingGraph);
        checkForDuplicateMapKeys(node, contributions, diagnosticReporter);
        checkForInconsistentMapKeyAnnotationTypes(node, contributions, diagnosticReporter);
      }
    }
  }

  private ImmutableSet<ContributionBinding> mapBindingContributions(
      BindingNode bindingNode, BindingGraph bindingGraph) {
    checkArgument(bindingNode.binding().kind().equals(MULTIBOUND_MAP));
    return bindingGraph
        .successors(bindingNode)
        .stream()
        .flatMap(instancesOf(BindingNode.class))
        .map(node -> (ContributionBinding) node.binding())
        .collect(toImmutableSet());
  }

  private void checkForDuplicateMapKeys(
      BindingNode multiboundMapBindingNode,
      ImmutableSet<ContributionBinding> contributions,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSetMultimap<Object, ContributionBinding> contributionsByMapKey =
        ImmutableSetMultimap.copyOf(Multimaps.index(contributions, ContributionBinding::mapKey));

    for (Set<ContributionBinding> contributionsForOneMapKey :
        Multimaps.asMap(contributionsByMapKey).values()) {
      if (contributionsForOneMapKey.size() > 1) {
        diagnosticReporter.reportBinding(
            ERROR,
            multiboundMapBindingNode,
            duplicateMapKeyErrorMessage(
                contributionsForOneMapKey, multiboundMapBindingNode.binding().key()));
      }
    }
  }

  private void checkForInconsistentMapKeyAnnotationTypes(
      BindingNode multiboundMapBindingNode,
      ImmutableSet<ContributionBinding> contributions,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSetMultimap<Equivalence.Wrapper<DeclaredType>, ContributionBinding>
        contributionsByMapKeyAnnotationType = indexByMapKeyAnnotationType(contributions);

    if (contributionsByMapKeyAnnotationType.keySet().size() > 1) {
      diagnosticReporter.reportBinding(
          ERROR,
          multiboundMapBindingNode,
          inconsistentMapKeyAnnotationTypesErrorMessage(
              contributionsByMapKeyAnnotationType, multiboundMapBindingNode.binding().key()));
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
              bindingDeclarationFormatter.formatIndentedList(
                  message, contributions, 2, DUPLICATE_SIZE_LIMIT);
            });
    return message.toString();
  }

  private String duplicateMapKeyErrorMessage(
      Set<ContributionBinding> contributionsForOneMapKey, Key mapBindingKey) {
    StringBuilder message =
        new StringBuilder("The same map key is bound more than once for ").append(mapBindingKey);
    bindingDeclarationFormatter.formatIndentedList(
        message, contributionsForOneMapKey, 1, DUPLICATE_SIZE_LIMIT);
    return message.toString();
  }
}

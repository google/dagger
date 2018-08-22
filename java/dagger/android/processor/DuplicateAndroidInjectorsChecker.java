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

package dagger.android.processor;

import static com.google.auto.common.AnnotationMirrors.getAnnotatedAnnotations;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.android.processor.AndroidMapKeys.injectedTypeFromMapKey;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.common.MoreTypes;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import dagger.MapKey;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingKind;
import dagger.model.Key;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Validates that the two maps that {@link DispatchingAndroidInjector} injects have logically
 * different keys. If a contribution exists for the same {@code FooActivity} with
 * {@code @ActivityKey(FooActivity.class)} and
 * {@code @AndroidInjectionKey("com.example.FooActivity")}, report an error.
 */
@AutoService(BindingGraphPlugin.class)
public final class DuplicateAndroidInjectorsChecker implements BindingGraphPlugin {
  @Override
  public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    for (BindingNode node : graph.bindingNodes()) {
      if (isDispatchingAndroidInjector(node)) {
        validateMapKeyUniqueness(node, graph, diagnosticReporter);
      }
    }
  }

  private boolean isDispatchingAndroidInjector(BindingNode node) {
    Key key = node.binding().key();
    return MoreTypes.isTypeOf(DispatchingAndroidInjector.class, key.type())
        && !key.qualifier().isPresent();
  }

  private void validateMapKeyUniqueness(
      BindingNode dispatchingAndroidInjectorNode,
      BindingGraph graph,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSet<BindingNode> injectorFactories =
        injectorMapDependencies(dispatchingAndroidInjectorNode, graph)
            .flatMap(injectorFactoryMap -> dependencies(injectorFactoryMap, graph))
            .collect(collectingAndThen(toList(), ImmutableSet::copyOf));

    ImmutableListMultimap.Builder<String, BindingNode> mapKeyIndex =
        ImmutableListMultimap.builder();
    for (BindingNode injectorFactory : injectorFactories) {
      AnnotationMirror mapKey = mapKey(injectorFactory).get();
      Optional<String> injectedType = injectedTypeFromMapKey(mapKey);
      if (injectedType.isPresent()) {
        mapKeyIndex.put(injectedType.get(), injectorFactory);
      } else {
        diagnosticReporter.reportBinding(
            ERROR, injectorFactory, "Unrecognized class: %s", mapKey);
      }
    }

    Map<String, List<BindingNode>> duplicates =
        Maps.filterValues(
            Multimaps.asMap(mapKeyIndex.build()), bindingNodes -> bindingNodes.size() > 1);
    if (!duplicates.isEmpty()) {
      StringBuilder errorMessage =
          new StringBuilder("Multiple injector factories bound for the same type:\n");
      Formatter formatter = new Formatter(errorMessage);
      duplicates.forEach(
          (injectedType, duplicateFactories) -> {
            formatter.format("  %s:\n", injectedType);
            duplicateFactories.forEach(duplicate -> formatter.format("    %s\n", duplicate));
          });
      diagnosticReporter.reportBinding(
          ERROR, dispatchingAndroidInjectorNode, errorMessage.toString());
    }
  }

  private Stream<BindingNode> dependencies(BindingNode bindingNode, BindingGraph graph) {
    return graph
        .successors(bindingNode)
        .stream()
        // TODO(ronshapiro): reuse DaggerStreams.instancesOf()?
        .filter(BindingNode.class::isInstance)
        .map(BindingNode.class::cast);
  }

  /**
   * Returns a stream of the dependencies of {@code bindingNode} that have a key type of {@code
   * Map<K, Provider<AndroidInjector.Factory<?>>}.
   */
  private Stream<BindingNode> injectorMapDependencies(BindingNode bindingNode, BindingGraph graph) {
    return dependencies(bindingNode, graph)
        .filter(node -> node.binding().kind().equals(BindingKind.MULTIBOUND_MAP))
        .filter(
            node -> {
              TypeMirror valueType =
                  MoreTypes.asDeclared(node.binding().key().type()).getTypeArguments().get(1);
              if (!MoreTypes.isTypeOf(Provider.class, valueType)
                  || !valueType.getKind().equals(TypeKind.DECLARED)) {
                return false;
              }
              TypeMirror providedType = MoreTypes.asDeclared(valueType).getTypeArguments().get(0);
              return MoreTypes.isTypeOf(AndroidInjector.Factory.class, providedType);
            });
  }

  private Optional<AnnotationMirror> mapKey(BindingNode bindingNode) {
    return bindingNode
        .binding()
        .bindingElement()
        .map(bindingElement -> getAnnotatedAnnotations(bindingElement, MapKey.class))
        .flatMap(
            annotations ->
                annotations.isEmpty()
                    ? Optional.empty()
                    : Optional.of(getOnlyElement(annotations)));
  }

  @Override
  public String pluginName() {
    return "Dagger/Android/DuplicateAndroidInjectors";
  }
}

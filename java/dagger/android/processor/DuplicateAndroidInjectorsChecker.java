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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.android.processor.AndroidMapKeys.injectedTypeFromMapKey;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XType;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import dagger.internal.codegen.xprocessing.DaggerElements;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.internal.codegen.xprocessing.XTypes;
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DaggerProcessingEnv;
import dagger.spi.model.DiagnosticReporter;
import dagger.spi.model.Key;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Validates that the two maps that {@code DispatchingAndroidInjector} injects have logically
 * different keys. If a contribution exists for the same {@code FooActivity} with
 * {@code @ActivityKey(FooActivity.class)} and
 * {@code @AndroidInjectionKey("com.example.FooActivity")}, report an error.
 */
@AutoService(BindingGraphPlugin.class)
public final class DuplicateAndroidInjectorsChecker implements BindingGraphPlugin {
  private DaggerProcessingEnv processingEnv;

  @Override
  public void init(DaggerProcessingEnv processingEnv, Map<String, String> options) {
    this.processingEnv = processingEnv;
  }

  @Override
  public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    for (Binding binding : graph.bindings()) {
      if (isDispatchingAndroidInjector(binding)) {
        validateMapKeyUniqueness(binding, graph, diagnosticReporter);
      }
    }
  }

  private boolean isDispatchingAndroidInjector(Binding binding) {
    Key key = binding.key();

    return XTypes.isTypeOf(
            DaggerElements.toXProcessing(key.type(), processingEnv),
            TypeNames.DISPATCHING_ANDROID_INJECTOR)
        && !key.qualifier().isPresent();
  }

  private void validateMapKeyUniqueness(
      Binding dispatchingAndroidInjector,
      BindingGraph graph,
      DiagnosticReporter diagnosticReporter) {
    ImmutableSet<Binding> injectorFactories =
        injectorMapDependencies(dispatchingAndroidInjector, graph)
            .flatMap(injectorFactoryMap -> graph.requestedBindings(injectorFactoryMap).stream())
            .collect(collectingAndThen(toList(), ImmutableSet::copyOf));

    ImmutableListMultimap.Builder<String, Binding> mapKeyIndex = ImmutableListMultimap.builder();
    for (Binding injectorFactory : injectorFactories) {
      XAnnotation mapKey = mapKey(injectorFactory).get();
      Optional<String> injectedType = injectedTypeFromMapKey(mapKey);
      if (injectedType.isPresent()) {
        mapKeyIndex.put(injectedType.get(), injectorFactory);
      } else {
        diagnosticReporter.reportBinding(
            ERROR, injectorFactory, "Unrecognized class: %s", mapKey);
      }
    }

    Map<String, List<Binding>> duplicates =
        Maps.filterValues(Multimaps.asMap(mapKeyIndex.build()), bindings -> bindings.size() > 1);
    if (!duplicates.isEmpty()) {
      StringBuilder errorMessage =
          new StringBuilder("Multiple injector factories bound for the same type:\n");
      Formatter formatter = new Formatter(errorMessage);
      duplicates.forEach(
          (injectedType, duplicateFactories) -> {
            formatter.format("  %s:\n", injectedType);
            duplicateFactories.forEach(duplicate -> formatter.format("    %s\n", duplicate));
          });
      diagnosticReporter.reportBinding(ERROR, dispatchingAndroidInjector, errorMessage.toString());
    }
  }

  /**
   * Returns a stream of the dependencies of {@code binding} that have a key type of {@code Map<K,
   * Provider<AndroidInjector.Factory<?>>}.
   */
  private Stream<Binding> injectorMapDependencies(Binding binding, BindingGraph graph) {
    return graph.requestedBindings(binding).stream()
        .filter(requestedBinding -> requestedBinding.kind().equals(BindingKind.MULTIBOUND_MAP))
        .filter(
            requestedBinding -> {
              XType valueType =
                  DaggerElements.toXProcessing(requestedBinding.key().type(), processingEnv)
                      .getTypeArguments()
                      .get(1);
              if (!XTypes.isTypeOf(valueType, TypeNames.PROVIDER)
                  || !XTypes.isDeclared(valueType)) {
                return false;
              }
              XType providedType = valueType.getTypeArguments().get(0);
              return XTypes.isTypeOf(providedType, TypeNames.ANDROID_INJECTOR_FACTORY);
            });
  }

  private Optional<XAnnotation> mapKey(Binding binding) {
    return binding
        .bindingElement()
        .map(
            bindingElement ->
                XElements.getAnnotatedAnnotations(
                    DaggerElements.toXProcessing(bindingElement, processingEnv), TypeNames.MAP_KEY))
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

/*
 * Copyright (C) 2019 The Dagger Authors.
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

import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static dagger.internal.codegen.ComponentAnnotation.rootComponentAnnotation;

import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.ClassTree;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.util.Context;
import dagger.BindsInstance;
import dagger.Component;
import dagger.model.BindingGraph;
import javax.inject.Inject;
import javax.inject.Singleton;

/** A {@link BugChecker} that collects statistics derived from a {@link BindingGraph}. */
public abstract class BindingGraphStatisticsCollector extends BugChecker
    implements ClassTreeMatcher {
  private BindingGraphConverter bindingGraphConverter;
  private BindingGraphFactory bindingGraphFactory;
  private ComponentDescriptorFactory componentDescriptorFactory;
  private boolean isInjected;

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface Injector {
    void inject(BindingGraphStatisticsCollector collector);

    @Component.Factory
    interface Factory {
      Injector create(@BindsInstance Context context);
    }
  }

  // BugCheckers must have no-arg constructors, so we'll use method injection instead.
  @Inject
  void inject(
      BindingGraphConverter bindingGraphConverter,
      BindingGraphFactory bindingGraphFactory,
      ComponentDescriptorFactory componentDescriptorFactory) {
    this.bindingGraphConverter = bindingGraphConverter;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentDescriptorFactory = componentDescriptorFactory;
  }

  @Override
  public final Description matchClass(ClassTree tree, VisitorState state) {
    injectIfNecessary(state.context);

    ClassSymbol symbol = getSymbol(tree);
    rootComponentAnnotation(symbol)
        .map(annotation -> createBindingGraph(symbol))
        .ifPresent(graph -> visitBindingGraph(graph, state));

    return Description.NO_MATCH;
  }

  private BindingGraph createBindingGraph(ClassSymbol component) {
    return bindingGraphConverter.convert(
        bindingGraphFactory.create(
            componentDescriptorFactory.rootComponentDescriptor(component), false));
  }

  /** Visits a {@link BindingGraph} and emits stats to a {@link VisitorState}. */
  protected abstract void visitBindingGraph(BindingGraph graph, VisitorState state);

  private void injectIfNecessary(Context context) {
    if (isInjected) {
      return;
    }
    DaggerBindingGraphStatisticsCollector_Injector.factory().create(context).inject(this);
    isInjected = true;
  }
}

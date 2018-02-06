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

package dagger.spi;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.DoNotMock;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.Node;
import java.util.Optional;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;

/**
 * A single item that is reported by {@link BindingGraphPlugin}s. Dagger will use these to print
 * diagnostics to the {@link Messager} with any pertinent information, such as a dependency trace.
 */
@AutoValue
@DoNotMock("Use ValidationItem.create() instead")
public abstract class ValidationItem {
  /** Creates a validation item on {@code node}. */
  public static ValidationItem create(Diagnostic.Kind diagnosticKind, Node node, String message) {
    return new AutoValue_ValidationItem(
        diagnosticKind, Optional.of(node), Optional.empty(), message);
  }

  /** Creates a validation item on {@code edge}. */
  public static ValidationItem create(
      Diagnostic.Kind diagnosticKind, DependencyEdge edge, String message) {
    return new AutoValue_ValidationItem(
        diagnosticKind, Optional.empty(), Optional.of(edge), message);
  }

  public abstract Diagnostic.Kind diagnosticKind();

  /**
   * This method is only intended to be used by Dagger's implementation and may not remain part of
   * the official API.
   */
  // TODO(ronshapiro): consider having a mechanism by which users can report an entire subgraph as a
  // ValidationItem instead of a single node/edge.
  public abstract Optional<Node> node();

  /**
   * This method is only intended to be used by Dagger's implementation and may not remain part of
   * the official API.
   */
  public abstract Optional<Edge> edge();

  public abstract String message();
}

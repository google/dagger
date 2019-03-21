/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.example.spi;

import static java.util.UUID.randomUUID;
import static java.util.regex.Matcher.quoteReplacement;
import static java.util.stream.Collectors.groupingBy;

import com.google.auto.service.AutoService;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.graph.EndpointPair;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import dagger.model.Binding;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ChildFactoryMethodEdge;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.MaybeBinding;
import dagger.model.BindingGraph.MissingBinding;
import dagger.model.BindingGraph.Node;
import dagger.model.BindingGraph.SubcomponentCreatorBindingEdge;
import dagger.model.BindingKind;
import dagger.model.ComponentPath;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Experimental visualizer used as a proof-of-concept for {@link BindingGraphPlugin}.
 *
 * <p>For each component, writes a <a href=http://www.graphviz.org/content/dot-language>DOT file</a>
 * in the same package. The file name is the name of the component type (with enclosing type names,
 * joined by underscores, preceding it), with a {@code .dot} extension.
 *
 * <p>For example, for a nested component type {@code Foo.Bar} this will generate a file {@code
 * Foo_Bar.dot}.
 */
@AutoService(BindingGraphPlugin.class)
public final class BindingGraphVisualizer implements BindingGraphPlugin {
  private Filer filer;

  @Override
  public void initFiler(Filer filer) {
    this.filer = filer;
  }

  /** Graphviz color names to use for binding nodes within each component. */
  private static final ImmutableList<String> COMPONENT_COLORS =
      ImmutableList.of(
          "/set312/1",
          "/set312/2",
          "/set312/3",
          "/set312/4",
          "/set312/5",
          "/set312/6",
          "/set312/7",
          "/set312/8",
          "/set312/9",
          "/set312/10",
          "/set312/11",
          "/set312/12");

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    TypeElement componentElement =
        bindingGraph.rootComponentNode().componentPath().currentComponent();
    DotGraph graph = new NodesGraph(bindingGraph).graph();
    ClassName componentName = ClassName.get(componentElement);
    try {
      FileObject file =
          filer
              .createResource(
                  StandardLocation.CLASS_OUTPUT,
                  componentName.packageName(),
                  Joiner.on('_').join(componentName.simpleNames()) + ".dot",
                  componentElement);
      try (PrintWriter writer = new PrintWriter(file.openWriter())) {
        graph.write(0, writer);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private abstract static class Indented {

    abstract void write(int level, PrintWriter writer);

    @CanIgnoreReturnValue
    PrintWriter indent(int level, PrintWriter writer) {
      writer.print(Strings.repeat(" ", level * 2));
      return writer;
    }
  }

  static class DotGraph extends Indented {
    private final String header;
    private final List<Indented> elements = new ArrayList<>();

    DotGraph(String header) {
      this.header = header;
    }

    @CanIgnoreReturnValue
    DotGraph add(Indented element) {
      elements.add(element);
      return this;
    }

    @Override
    void write(int level, PrintWriter writer) {
      indent(level, writer);
      writer.println(header + " {");
      for (Indented element : elements) {
        element.write(level + 1, writer);
      }
      indent(level, writer);
      writer.println("}");
    }
  }

  static class DotStatement<S extends DotStatement<S>> extends Indented {
    private final String base;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    DotStatement(String base) {
      this.base = base;
    }

    @SuppressWarnings("unchecked")
    @CanIgnoreReturnValue
    S addAttribute(String name, Object value) {
      attributes.put(name, value);
      return (S) this;
    }

    @CanIgnoreReturnValue
    S addAttributeFormat(String name, String format, Object... args) {
      return addAttribute(name, String.format(format, args));
    }

    @Override
    void write(int level, PrintWriter writer) {
      indent(level, writer);
      writer.print(base);
      if (!attributes.isEmpty()) {
        writer.print(
            attributes
                .entrySet()
                .stream()
                .map(
                    entry ->
                        String.format("%s=%s", entry.getKey(), quote(entry.getValue().toString())))
                .collect(Collectors.joining(", ", " [", "]")));
      }
      writer.println();
    }
  }

  private static String quote(String string) {
    return '"' + string.replaceAll("\"", quoteReplacement("\\\"")) + '"';
  }

  static class DotNode extends DotStatement<DotNode> {
    DotNode(Object nodeName) {
      super(quote(nodeName.toString()));
    }
  }

  static class DotEdge extends DotStatement<DotEdge> {
    DotEdge(Object leftNode, Object rightNode) {
      super(quote(leftNode.toString()) + " -> " + quote(rightNode.toString()));
    }
  }

  static class NodesGraph {
    private final DotGraph graph =
        new DotGraph("digraph")
            .add(
                new DotStatement<>("graph")
                    .addAttribute("rankdir", "LR")
                    .addAttribute("labeljust", "l")
                    .addAttribute("compound", true));

    private final BindingGraph bindingGraph;
    private final Map<Node, UUID> nodeIds = new HashMap<>();

    NodesGraph(BindingGraph bindingGraph) {
      this.bindingGraph = bindingGraph;
    }

    DotGraph graph() {
      if (nodeIds.isEmpty()) {
        Iterator<String> colors = Iterators.cycle(COMPONENT_COLORS);
        bindingGraph.network().nodes().stream()
            .collect(groupingBy(Node::componentPath))
            .forEach(
                (component, networkNodes) -> {
                  DotGraph subgraph = subgraph(component);
                  subgraph.add(
                      new DotStatement<>("node")
                          .addAttribute("style", "filled")
                          .addAttribute("shape", "box")
                          .addAttribute("fillcolor", colors.next()));
                  subgraph.add(new DotStatement<>("graph").addAttribute("label", component));
                  for (Node node : networkNodes) {
                    subgraph.add(dotNode(node));
                  }
                });
        for (Edge edge : bindingGraph.network().edges()) {
          dotEdge(edge).ifPresent(graph::add);
        }
      }
      return graph;
    }

    DotGraph subgraph(ComponentPath component) {
      DotGraph subgraph = new DotGraph("subgraph " + quote(clusterName(component)));
      graph.add(subgraph);
      return subgraph;
    }

    UUID nodeId(Node node) {
      return nodeIds.computeIfAbsent(node, n -> randomUUID());
    }

    Optional<DotEdge> dotEdge(Edge edge) {
      EndpointPair<Node> incidentNodes = bindingGraph.network().incidentNodes(edge);
      DotEdge dotEdge = new DotEdge(nodeId(incidentNodes.source()), nodeId(incidentNodes.target()));
      if (edge instanceof DependencyEdge) {
        if (((DependencyEdge) edge).isEntryPoint()) {
          return Optional.empty();
        }
      } else if (edge instanceof ChildFactoryMethodEdge) {
        dotEdge.addAttribute("style", "dashed");
        dotEdge.addAttribute("lhead", clusterName(incidentNodes.target().componentPath()));
        dotEdge.addAttribute("ltail", clusterName(incidentNodes.source().componentPath()));
        dotEdge.addAttribute("taillabel", ((ChildFactoryMethodEdge) edge).factoryMethod());
      } else if (edge instanceof SubcomponentCreatorBindingEdge) {
        dotEdge.addAttribute("style", "dashed");
        dotEdge.addAttribute("lhead", clusterName(incidentNodes.target().componentPath()));
        dotEdge.addAttribute("taillabel", "subcomponent");
      }
      return Optional.of(dotEdge);
    }

    DotNode dotNode(Node node) {
      DotNode dotNode = new DotNode(nodeId(node));
      if (node instanceof MaybeBinding) {
        dotNode.addAttribute("tooltip", "");
        if (bindingGraph.entryPointBindings().contains(node)) {
          dotNode.addAttribute("penwidth", 3);
        }
        if (node instanceof Binding) {
          dotNode.addAttribute("label", label((Binding) node));
        }
        if (node instanceof MissingBinding) {
          dotNode.addAttributeFormat(
              "label", "missing binding for %s", ((MissingBinding) node).key());
        }
      } else {
        dotNode.addAttribute("style", "invis").addAttribute("shape", "point");
      }
      return dotNode;
    }

    private String label(Binding binding) {
      if (binding.kind().equals(BindingKind.MEMBERS_INJECTION)) {
        return String.format("inject(%s)", binding.key());
      } else if (binding.isProduction()) {
        return String.format("@Produces %s", binding.key());
      } else {
        return binding.key().toString();
      }
    }

    private static String clusterName(ComponentPath owningComponentPath) {
      return "cluster" + owningComponentPath;
    }
  }
}

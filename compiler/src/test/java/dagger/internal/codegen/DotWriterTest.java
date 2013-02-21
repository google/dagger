/**
 * Copyright (C) 2012 Square, Inc.
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

import java.io.IOException;
import java.io.StringWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;

@RunWith(JUnit4.class)
public final class DotWriterTest {
  private final StringWriter stringWriter = new StringWriter();
  private final DotWriter dotWriter = new DotWriter(stringWriter);

  @Test public void graphWithAttributes() throws IOException {
    dotWriter.beginGraph();
    dotWriter.edge("CoffeeMaker", "Heater", "style", "dotted", "color", "red");
    dotWriter.edge("CoffeeMaker", "Pump");
    dotWriter.node("CoffeeMaker", "shape", "box");
    dotWriter.endGraph();
    assertGraph(""
        + "digraph G1 {\n"
        + "  CoffeeMaker -> Heater [style=dotted;color=red];\n"
        + "  CoffeeMaker -> Pump;\n"
        + "  CoffeeMaker [shape=box];\n"
        + "}\n");
  }

  @Test public void subgraph() throws IOException {
    dotWriter.beginGraph("label", "10\" tall");
    dotWriter.beginGraph("style", "filled", "color", "lightgrey");
    dotWriter.edge("ElectricHeater", "Heater");
    dotWriter.endGraph();
    dotWriter.edge("CoffeeMaker", "Heater");
    dotWriter.edge("CoffeeMaker", "Pump");
    dotWriter.endGraph();
    assertGraph(""
        + "digraph G1 {\n"
        + "  label = \"10\\\" tall\";\n"
        + "  subgraph cluster2 {\n"
        + "    style = filled;\n"
        + "    color = lightgrey;\n"
        + "    ElectricHeater -> Heater;\n"
        + "  }\n"
        + "  CoffeeMaker -> Heater;\n"
        + "  CoffeeMaker -> Pump;\n"
        + "}\n");
  }

  @Test public void defaultAttributes() throws IOException {
    dotWriter.beginGraph();
    dotWriter.nodeDefaults("color", "red");
    dotWriter.edgeDefaults("style", "dotted");
    dotWriter.edge("CoffeeMaker", "Heater");
    dotWriter.endGraph();
    assertGraph(""
        + "digraph G1 {\n"
        + "  node [color=red];\n"
        + "  edge [style=dotted];\n"
        + "  CoffeeMaker -> Heater;\n"
        + "}\n");
  }

  @Test public void invalidNodeNames() throws IOException {
    dotWriter.beginGraph();
    dotWriter.edge("a.b", "a c");
    dotWriter.edge("a c", "a_d");
    dotWriter.endGraph();
    assertGraph(""
        + "digraph G1 {\n"
        + "  n2 [label=\"a.b\"];\n"
        + "  n3 [label=\"a c\"];\n"
        + "  n2 -> n3;\n"
        + "  n3 -> a_d;\n"
        + "}\n");
  }

  private void assertGraph(String expected) {
    assertThat(stringWriter.toString()).isEqualTo(expected);
  }
}

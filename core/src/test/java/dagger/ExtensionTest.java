/*
 * Copyright (C) 2012 Google Inc.
 * Copyright (C) 2012 Square Inc.
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
package dagger;

import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnit4.class)
public final class ExtensionTest {
  @Singleton
  static class A {
    @Inject A() {}
  }

  static class B {
    @Inject A a;
  }

  @Singleton
  static class C {
    @Inject A a;
    @Inject B b;
  }

  static class D {
    @Inject A a;
    @Inject B b;
    @Inject C c;
  }

  @Module(entryPoints = { A.class, B.class }) static class RootModule { }

  @Module(addsTo = RootModule.class, entryPoints = { C.class, D.class })
  static class ExtensionModule { }

  @Test public void basicExtension() {
    assertNotNull(ObjectGraph.create(new RootModule()).plus(new ExtensionModule()));
  }

  @Test public void basicInjection() {
    ObjectGraph root = ObjectGraph.create(new RootModule());
    assertThat(root.get(A.class)).isNotNull();
    assertThat(root.get(A.class)).isSameAs(root.get(A.class)); // Present and Singleton.
    assertThat(root.get(B.class)).isNotSameAs(root.get(B.class)); // Not singleton.
    assertFailNoEntryPoint(root, C.class); // Not declared in RootModule.
    assertFailNoEntryPoint(root, D.class); // Not declared in RootModule.

    // Extension graph behaves as the root graph would for root-ish things.
    ObjectGraph extension = root.plus(new ExtensionModule());
    assertThat(root.get(A.class)).isSameAs(extension.get(A.class));
    assertThat(root.get(B.class)).isNotSameAs(extension.get(B.class));
    assertThat(root.get(B.class).a).isSameAs(extension.get(B.class).a);

    assertThat(extension.get(C.class).a).isNotNull();
    assertThat(extension.get(D.class).c).isNotNull();
  }

  @Test public void scopedGraphs() {
    ObjectGraph app = ObjectGraph.create(new RootModule());
    assertThat(app.get(A.class)).isNotNull();
    assertThat(app.get(A.class)).isSameAs(app.get(A.class));
    assertThat(app.get(B.class)).isNotSameAs(app.get(B.class));
    assertFailNoEntryPoint(app, C.class);
    assertFailNoEntryPoint(app, D.class);

    ObjectGraph request1 = app.plus(new ExtensionModule());
    ObjectGraph request2 = app.plus(new ExtensionModule());
    for (ObjectGraph request : Arrays.asList(request1, request2)) {
      assertThat(request.get(A.class)).isNotNull();
      assertThat(request.get(A.class)).isSameAs(request.get(A.class));
      assertThat(request.get(B.class)).isNotSameAs(request.get(B.class));
      assertThat(request.get(C.class)).isNotNull();
      assertThat(request.get(C.class)).isSameAs(request.get(C.class));
      assertThat(request.get(D.class)).isNotSameAs(request.get(D.class));
    }

    // Singletons are one-per-graph-instance where they are declared.
    assertThat(request1.get(C.class)).isNotSameAs(request2.get(C.class));
    // Singletons that come from common roots should be one-per-common-graph-instance.
    assertThat(request1.get(C.class).a).isSameAs(request2.get(C.class).a);
  }

  private void assertFailNoEntryPoint(ObjectGraph graph, Class<?> clazz) {
    try {
      assertThat(graph.get(clazz)).isNull();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).contains("No entry point");
    }
  }
}

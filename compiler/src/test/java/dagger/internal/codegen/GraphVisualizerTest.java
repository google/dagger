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

import dagger.internal.Keys;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import javax.inject.Named;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;

@RunWith(JUnit4.class)
public final class GraphVisualizerTest {
  private final GraphVisualizer graphVisualizer = new GraphVisualizer();

  String simpleKey;
  @Test public void testSimpleKey() throws Exception {
    String key = fieldKey("simpleKey");
    assertThat(graphVisualizer.shortName(key)).isEqualTo("String");
  }

  @Named String annotatedKey;
  @Test public void testAnnotatedKey() throws Exception {
    String key = fieldKey("annotatedKey");
    assertThat(graphVisualizer.shortName(key)).isEqualTo("@Named String");
  }

  @Named("/@<>[]()") String annotatedKeyWithParameters;
  @Test public void testAnnotatedKeyWithParameters() throws Exception {
    String key = fieldKey("annotatedKeyWithParameters");
    // We intentionally omit parameters on annotated keys!
    assertThat(graphVisualizer.shortName(key)).isEqualTo("@Named String");
  }

  String[][] arrayKey;
  @Test public void testArrayKey() throws Exception {
    String key = fieldKey("arrayKey");
    assertThat(graphVisualizer.shortName(key)).isEqualTo("String[][]");
  }

  Map<String, Set<Object>> typeParameterKey;
  @Test public void testTypeParameterKey() throws Exception {
    String key = fieldKey("typeParameterKey");
    assertThat(graphVisualizer.shortName(key))
        .isEqualTo("Map<java.lang.String, java.util.Set<java.lang.Object>>");
  }

  @Named("/@<>[]()") Map<String, Set<Object>>[] everythingKey;
  @Test public void testEverythingKey() throws Exception {
    String key = fieldKey("everythingKey");
    assertThat(graphVisualizer.shortName(key))
        .isEqualTo("@Named Map<java.lang.String, java.util.Set<java.lang.Object>>[]");
  }

  @Test public void testMembersKey() throws Exception {
    String key = Keys.getMembersKey(String.class);
    assertThat(graphVisualizer.shortName(key)).isEqualTo("String");
  }

  private String fieldKey(String fieldName) throws NoSuchFieldException {
    Field field = GraphVisualizerTest.class.getDeclaredField(fieldName);
    return Keys.get(field.getGenericType(), field.getAnnotations(), field);
  }
}

/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger2.internal.codegen.writer;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;

import static dagger2.internal.codegen.writer.Writables.toStringWritable;

public final class AnnotationWriter implements Writable, HasClassReferences {
  private final ClassName annotationName;
  private final SortedMap<String, Writable> memberMap = Maps.newTreeMap();

  AnnotationWriter(ClassName annotationName) {
    this.annotationName = annotationName;
  }

  public void setValue(String value) {
    setMember("value", value);
  }

  public void setMember(String name, int value) {
    memberMap.put(name, toStringWritable(value));
  }

  public void setMember(String name, String value) {
    memberMap.put(name, toStringWritable(StringLiteral.forValue(value)));
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    appendable.append('@');
    annotationName.write(appendable, context);
    if (!memberMap.isEmpty()) {
      appendable.append('(');
      if (memberMap.size() == 1) {
        Entry<String, Writable> onlyEntry = Iterables.getOnlyElement(memberMap.entrySet());
        if (!onlyEntry.getKey().equals("value")) {
          appendable.append(onlyEntry.getKey()).append(" = ");
        }
        onlyEntry.getValue().write(appendable, context);
      }
      appendable.append(')');
    }
    return appendable;
  }

  @Override
  public Set<ClassName> referencedClasses() {
    return ImmutableSet.of(annotationName);
  }
}

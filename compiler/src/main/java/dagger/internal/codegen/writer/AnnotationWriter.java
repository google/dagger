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
package dagger.internal.codegen.writer;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;

import static dagger.internal.codegen.writer.Writables.toStringWritable;

public final class AnnotationWriter implements Writable, HasClassReferences {
  private final ClassName annotationName;
  private final Set<HasClassReferences> memberReferences = Sets.newLinkedHashSet();
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

  public <T extends Enum<T>> void setMember(String name, T value) {
    Snippet snippet = Snippet.format("%s.%s", ClassName.fromClass(value.getClass()), value);
    memberMap.put(name, snippet);
    memberReferences.add(snippet);
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    appendable.append('@');
    annotationName.write(appendable, context);
    if (!memberMap.isEmpty()) {
      appendable.append('(');
      boolean singleEntry = memberMap.size() == 1;
      Iterator<Entry<String, Writable>> iterator = memberMap.entrySet().iterator();
      while (iterator.hasNext()) {
        Entry<String, Writable> member = iterator.next();
        if (!singleEntry || !member.getKey().equals("value")) {
          appendable.append(member.getKey()).append(" = ");
        }
        member.getValue().write(appendable, context);
        if (iterator.hasNext()) {
          appendable.append(",");
        }
      }
      appendable.append(')');
    }
    return appendable;
  }

  @Override
  public Set<ClassName> referencedClasses() {
    return FluentIterable.from(memberReferences)
        .append(annotationName)
        .transformAndConcat(HasClassReferences.COMBINER)
        .toSet();
  }
}

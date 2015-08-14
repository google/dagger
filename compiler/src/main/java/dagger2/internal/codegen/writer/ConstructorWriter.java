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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.TypeElement;

import static com.google.common.base.Preconditions.checkArgument;

public final class ConstructorWriter extends Modifiable implements Writable, HasClassReferences {
  private final String name;
  private final Map<String, VariableWriter> parameterWriters;
  private final BlockWriter blockWriter;

  ConstructorWriter(String name) {
    this.name = name;
    this.parameterWriters = Maps.newLinkedHashMap();
    this.blockWriter = new BlockWriter();
  }

  public VariableWriter addParameter(Class<?> type, String name) {
    return addParameter(ClassName.fromClass(type), name);
  }

  public VariableWriter addParameter(TypeElement type, String name) {
    return addParameter(ClassName.fromTypeElement(type), name);
  }

  public VariableWriter addParameter(TypeWriter type, String name) {
    return addParameter(type.name, name);
  }

  public VariableWriter addParameter(TypeName type, String name) {
    VariableWriter parameterWriter = new VariableWriter(type, name);
    parameterWriters.put(name, parameterWriter);
    return parameterWriter;
  }
  
  public Map<String, TypeName> parameters() {
    ImmutableMap.Builder<String, TypeName> params = ImmutableMap.builder();
    for (Map.Entry<String, VariableWriter> entry : parameterWriters.entrySet()) {
      params.put(entry.getKey(), entry.getValue().type());
    }
    return params.build();
  }

  public BlockWriter body() {
    return blockWriter;
  }

  private VariableWriter addParameter(ClassName type, String name) {
    checkArgument(!parameterWriters.containsKey(name));
    VariableWriter parameterWriter = new VariableWriter(type, name);
    parameterWriters.put(name, parameterWriter);
    return parameterWriter;
  }

  @Override
  public Set<ClassName> referencedClasses() {
    return FluentIterable.from(
        Iterables.concat(parameterWriters.values(), ImmutableList.of(blockWriter)))
            .transformAndConcat(new Function<HasClassReferences, Set<ClassName>>() {
              @Override
              public Set<ClassName> apply(HasClassReferences input) {
                return input.referencedClasses();
              }
            })
            .toSet();
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    writeModifiers(appendable).append(name).append('(');
    Writables.join(", ", parameterWriters.values(), appendable, context);
    appendable.append(") {");
    blockWriter.write(new IndentingAppendable(appendable), context);
    return appendable.append("}\n");
  }
}

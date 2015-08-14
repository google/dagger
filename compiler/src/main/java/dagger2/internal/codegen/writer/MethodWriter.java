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
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.TypeElement;

import static com.google.common.base.Preconditions.checkArgument;

public final class MethodWriter extends Modifiable implements HasClassReferences, Writable {
  private final TypeName returnType;
  private final String name;
  private final Map<String, VariableWriter> parameterWriters;
  private final List<TypeVariableName> typeParameters;
  private Optional<BlockWriter> body;

  MethodWriter(TypeName returnType, String name) {
    this.returnType = returnType;
    this.name = name;
    this.parameterWriters = Maps.newLinkedHashMap();
    this.typeParameters = Lists.newArrayList();
    this.body = Optional.absent();
  }

  public String name() {
    return name;
  }
  
  public TypeName returnType() {
    return returnType;
  }
  
  public void addTypeParameter(TypeVariableName typeVariableName) {
    this.typeParameters.add(typeVariableName);
  }
  
  public void addTypeParameters(Iterable<TypeVariableName> typeVariableNames) {
    Iterables.addAll(typeParameters, typeVariableNames);
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
    checkArgument(!parameterWriters.containsKey(name));
    VariableWriter parameterWriter = new VariableWriter(type, name);
    parameterWriters.put(name, parameterWriter);
    return parameterWriter;
  }

  public BlockWriter body() {
    if (body.isPresent()) {
      return body.get();
    } else {
      BlockWriter blockWriter = new BlockWriter();
      body = Optional.of(blockWriter);
      return blockWriter;
    }
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    writeAnnotations(appendable, context);
    writeModifiers(appendable);
    Writables.join(", ", typeParameters, "<", "> ", appendable, context);
    returnType.write(appendable, context);
    appendable.append(' ').append(name).append('(');
    Writables.join(", ", parameterWriters.values(), appendable, context);
    appendable.append(")");
    if (body.isPresent()) {
      appendable.append(" {");
      body.get().write(new IndentingAppendable(appendable), context);
      appendable.append("}\n");
    } else {
      appendable.append(";\n");
    }
    return appendable;
  }

  @Override
  public Set<ClassName> referencedClasses() {
    return FluentIterable.from(
        Iterables.concat(ImmutableList.of(returnType), parameterWriters.values(), body.asSet()))
            .transformAndConcat(new Function<HasClassReferences, Set<ClassName>>() {
              @Override
              public Set<ClassName> apply(HasClassReferences input) {
                return input.referencedClasses();
              }
            })
            .toSet();
  }
}

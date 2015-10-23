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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;

import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

public final class EnumWriter extends TypeWriter {
  private final Map<String, ConstantWriter> constantWriters = Maps.newLinkedHashMap();
  private final List<ConstructorWriter> constructorWriters = Lists.newArrayList();

  EnumWriter(ClassName name) {
    super(name);
  }

  public ConstantWriter addConstant(String name) {
    ConstantWriter constantWriter = new ConstantWriter(name);
    constantWriters.put(name, constantWriter);
    return constantWriter;
  }

  public ConstructorWriter addConstructor() {
    ConstructorWriter constructorWriter = new ConstructorWriter(name.simpleName());
    constructorWriters.add(constructorWriter);
    return constructorWriter;
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    context = context.createSubcontext(FluentIterable.from(nestedTypeWriters)
        .transform(new Function<TypeWriter, ClassName>() {
          @Override public ClassName apply(TypeWriter input) {
            return input.name;
          }
        })
        .toSet());
    writeAnnotations(appendable, context);
    writeModifiers(appendable).append("enum ").append(name.simpleName());
    Iterator<TypeName> implementedTypesIterator = implementedTypes.iterator();
    if (implementedTypesIterator.hasNext()) {
      appendable.append(" implements ");
      implementedTypesIterator.next().write(appendable, context);
      while (implementedTypesIterator.hasNext()) {
        appendable.append(", ");
        implementedTypesIterator.next().write(appendable, context);
      }
    }
    appendable.append(" {");

    checkState(!constantWriters.isEmpty(), "Cannot write an enum with no constants.");
    appendable.append('\n');
    ImmutableList<ConstantWriter> constantWriterList =
        ImmutableList.copyOf(constantWriters.values());
    for (ConstantWriter constantWriter
        : constantWriterList.subList(0, constantWriterList.size() - 1)) {
      constantWriter.write(appendable, context);
      appendable.append(",\n");
    }
    constantWriterList.get(constantWriterList.size() - 1).write(appendable, context);
    appendable.append(";\n");

    if (!fieldWriters.isEmpty()) {
      appendable.append('\n');
    }
    for (VariableWriter fieldWriter : fieldWriters.values()) {
      fieldWriter.write(new IndentingAppendable(appendable), context).append("\n");
    }
    for (ConstructorWriter constructorWriter : constructorWriters) {
      appendable.append('\n');
      if (!isDefaultConstructor(constructorWriter)) {
        constructorWriter.write(new IndentingAppendable(appendable), context);
      }
    }
    for (MethodWriter methodWriter : methodWriters) {
      appendable.append('\n');
      methodWriter.write(new IndentingAppendable(appendable), context);
    }
    for (TypeWriter nestedTypeWriter : nestedTypeWriters) {
      appendable.append('\n');
      nestedTypeWriter.write(new IndentingAppendable(appendable), context);
    }
    appendable.append("}\n");
    return appendable;
  }

  private static final Set<Modifier> VISIBILIY_MODIFIERS =
      Sets.immutableEnumSet(PUBLIC, PROTECTED, PRIVATE);

  private boolean isDefaultConstructor(ConstructorWriter constructorWriter) {
    return Sets.intersection(VISIBILIY_MODIFIERS, modifiers)
        .equals(Sets.intersection(VISIBILIY_MODIFIERS, constructorWriter.modifiers))
        && constructorWriter.body().isEmpty();
  }

  @Override
  public Set<ClassName> referencedClasses() {
    @SuppressWarnings("unchecked")
    Iterable<? extends HasClassReferences> concat =
        Iterables.concat(nestedTypeWriters, constantWriters.values(), fieldWriters.values(),
            constructorWriters, methodWriters, implementedTypes, annotations);
    return FluentIterable.from(concat)
        .transformAndConcat(new Function<HasClassReferences, Set<ClassName>>() {
          @Override
          public Set<ClassName> apply(HasClassReferences input) {
            return input.referencedClasses();
          }
        })
        .toSet();
  }

  public static final class ConstantWriter implements Writable, HasClassReferences {
    private final String name;
    private final List<Snippet> constructorSnippets;

    private ConstantWriter(String name) {
      this.name = name;
      this.constructorSnippets = Lists.newArrayList();
    }

    ConstantWriter addArgument(Snippet snippet) {
      constructorSnippets.add(snippet);
      return this;
    }

    @Override
    public Appendable write(Appendable appendable, Context context) throws IOException {
      appendable.append(name);
      Iterator<Snippet> snippetIterator = constructorSnippets.iterator();
      if (snippetIterator.hasNext()) {
        appendable.append('(');
        snippetIterator.next().write(appendable, context);
        while (snippetIterator.hasNext()) {
          appendable.append(", ");
          snippetIterator.next().write(appendable, context);
        }
        appendable.append(')');
      }
      return appendable;
    }

    @Override
    public Set<ClassName> referencedClasses() {
      return FluentIterable.from(constructorSnippets)
          .transformAndConcat(new Function<Snippet, Set<ClassName>>() {
            @Override
            public Set<ClassName> apply(Snippet input) {
              return input.referencedClasses();
            }
          })
          .toSet();
    }
  }
}

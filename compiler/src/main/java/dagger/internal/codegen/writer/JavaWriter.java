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
import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import dagger.internal.codegen.writer.Writable.Context;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.JavaFileObject;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.unmodifiableList;

/**
 * Writes a single compilation unit.
 */
public final class JavaWriter {
  public static JavaWriter inPackage(String packageName) {
    return new JavaWriter(packageName);
  }

  public static JavaWriter inPackage(Package enclosingPackage) {
    return new JavaWriter(enclosingPackage.getName());
  }

  public static JavaWriter inPackage(PackageElement packageElement) {
    return new JavaWriter(packageElement.getQualifiedName().toString());
  }

  private final String packageName;
  // TODO(gak): disallow multiple types in a file?
  private final List<TypeWriter> typeWriters;
  private final List<ClassName> explicitImports;

  private JavaWriter(String packageName) {
    this.packageName = packageName;
    this.typeWriters = Lists.newArrayList();
    this.explicitImports = Lists.newArrayList();
  }

  public List<TypeWriter> getTypeWriters() {
    return unmodifiableList(typeWriters);
  }

  public JavaWriter addImport(Class<?> importedClass) {
    explicitImports.add(ClassName.fromClass(importedClass));
    return this;
  }

  public ClassWriter addClass(String simpleName) {
    checkNotNull(simpleName);
    ClassWriter classWriter = new ClassWriter(ClassName.create(packageName, simpleName));
    typeWriters.add(classWriter);
    return classWriter;
  }

  public EnumWriter addEnum(String simpleName) {
    checkNotNull(simpleName);
    EnumWriter writer = new EnumWriter(ClassName.create(packageName, simpleName));
    typeWriters.add(writer);
    return writer;
  }

  public InterfaceWriter addInterface(String simpleName) {
    InterfaceWriter writer = new InterfaceWriter(ClassName.create(packageName, simpleName));
    typeWriters.add(writer);
    return writer;
  }

  public Appendable write(Appendable appendable) throws IOException {
    if (!packageName.isEmpty()) {
      appendable.append("package ").append(packageName).append(";\n\n");
    }

    // write imports
    ImmutableSet<ClassName> classNames = FluentIterable.from(typeWriters)
        .transformAndConcat(new Function<HasClassReferences, Set<ClassName>>() {
          @Override
          public Set<ClassName> apply(HasClassReferences input) {
            return input.referencedClasses();
          }
        })
        .toSet();

    ImmutableSortedSet<ClassName> importCandidates = ImmutableSortedSet.<ClassName>naturalOrder()
        .addAll(explicitImports)
        .addAll(classNames)
        .build();
    ImmutableSet<ClassName> typeNames = FluentIterable.from(typeWriters)
        .transform(new Function<TypeWriter, ClassName>() {
          @Override public ClassName apply(TypeWriter input) {
            return input.name;
          }
        })
        .toSet();

    ImmutableSet.Builder<String> declaredSimpleNamesBuilder = ImmutableSet.builder();
    Deque<TypeWriter> declaredTypes = new ArrayDeque<>(typeWriters);
    while (!declaredTypes.isEmpty()) {
      TypeWriter currentType = declaredTypes.pop();
      declaredSimpleNamesBuilder.add(currentType.name().simpleName());
      declaredTypes.addAll(currentType.nestedTypeWriters);
    }

    ImmutableSet<String> declaredSimpleNames = declaredSimpleNamesBuilder.build();

    BiMap<String, ClassName> importedClassIndex = HashBiMap.create();
    for (ClassName className : importCandidates) {
      if (!(className.packageName().equals(packageName)
              && !className.enclosingClassName().isPresent())
          && !(className.packageName().equals("java.lang")
              && className.enclosingSimpleNames().isEmpty())
          && !typeNames.contains(className.topLevelClassName())) {
        Optional<ClassName> importCandidate = Optional.of(className);
        while (importCandidate.isPresent()
            && (importedClassIndex.containsKey(importCandidate.get().simpleName())
                || declaredSimpleNames.contains(importCandidate.get().simpleName()))) {
          importCandidate = importCandidate.get().enclosingClassName();
        }
        if (importCandidate.isPresent()) {
          appendable.append("import ").append(importCandidate.get().canonicalName()).append(";\n");
          importedClassIndex.put(importCandidate.get().simpleName(), importCandidate.get());
        }
      }
    }

    appendable.append('\n');

    CompilationUnitContext context =
        new CompilationUnitContext(packageName, ImmutableSet.copyOf(importedClassIndex.values()));

    // write types
    for (TypeWriter typeWriter : typeWriters) {
      typeWriter.write(appendable, context.createSubcontext(typeNames)).append('\n');
    }
    return appendable;
  }

  public void file(Filer filer, Iterable<? extends Element> originatingElements)
      throws IOException {
    file(filer, Iterables.getOnlyElement(typeWriters).name.canonicalName(), originatingElements);
  }

  public void file(Filer filer, CharSequence name,  Iterable<? extends Element> originatingElements)
      throws IOException {
    JavaFileObject sourceFile = filer.createSourceFile(name,
        Iterables.toArray(originatingElements, Element.class));
    Closer closer = Closer.create();
    try {
      write(closer.register(sourceFile.openWriter()));
    } catch (Exception e) {
      try {
        sourceFile.delete();
      } catch (Exception e2) {
        // couldn't delete the file
      }
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }
  }

  @Override
  public String toString() {
    try {
      return write(new StringBuilder()).toString();
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  static final class CompilationUnitContext implements Context {
    private final String packageName;
    private final ImmutableSortedSet<ClassName> visibleClasses;

    CompilationUnitContext(String packageName, Set<ClassName> visibleClasses) {
      this.packageName = packageName;
      this.visibleClasses =
          ImmutableSortedSet.copyOf(Ordering.natural().reverse(), visibleClasses);
    }

    @Override
    public Context createSubcontext(Set<ClassName> newTypes) {
      return new CompilationUnitContext(packageName, Sets.union(visibleClasses, newTypes));
    }

    @Override
    public String sourceReferenceForClassName(ClassName className) {
      if (isImported(className)) {
        return className.simpleName();
      }
      Optional<ClassName> enclosingClassName = className.enclosingClassName();
      while (enclosingClassName.isPresent()) {
        if (isImported(enclosingClassName.get())) {
          return enclosingClassName.get().simpleName()
              + className.canonicalName()
                  .substring(enclosingClassName.get().canonicalName().length());
        }
        enclosingClassName = enclosingClassName.get().enclosingClassName();
      }
      return className.canonicalName();
    }

    private boolean collidesWithVisibleClass(ClassName className) {
      return collidesWithVisibleClass(className.simpleName());
    }

    private boolean collidesWithVisibleClass(String simpleName) {
      return FluentIterable.from(visibleClasses)
          .transform(new Function<ClassName, String>() {
            @Override public String apply(ClassName input) {
              return input.simpleName();
            }
          })
          .contains(simpleName);
    }

    private boolean isImported(ClassName className) {
      return (packageName.equals(className.packageName())
              && !className.enclosingClassName().isPresent()
              && !collidesWithVisibleClass(className)) // need to account for scope & hiding
          || visibleClasses.contains(className)
          || (className.packageName().equals("java.lang")
              && className.enclosingSimpleNames().isEmpty());
    }
  }
}

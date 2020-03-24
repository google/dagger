/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Utf8;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/** Generates a Dagger component or subcomponent interface. */
// TODO(user): Make this non-public
public final class ComponentGenerator {
  private static final Joiner JOINER = Joiner.on(".");
  private static final Comparator<ClassName> SIMPLE_NAME_SORTER =
      Comparator.comparing((ClassName c) -> JOINER.join(c.simpleNames()))
          .thenComparing(ClassName::compareTo);
  private static final Comparator<TypeName> TYPE_NAME_SORTER = comparing(TypeName::toString);

  private final ProcessingEnvironment processingEnv;
  private final ClassName name;
  private final TypeElement rootElement;
  private final Optional<ClassName> superclass;
  private final ImmutableList<ClassName> modules;
  private final ImmutableList<TypeName> entryPoints;
  private final ImmutableCollection<ClassName> scopes;
  private final ImmutableList<AnnotationSpec> extraAnnotations;
  private final ClassName componentAnnotation;
  private final Optional<TypeSpec> componentBuilder;

  public ComponentGenerator(
      ProcessingEnvironment processingEnv,
      ClassName name,
      TypeElement rootElement,
      Optional<ClassName> superclass,
      Set<? extends ClassName> modules,
      Set<? extends TypeName> entryPoints,
      ImmutableCollection<ClassName> scopes,
      ImmutableList<AnnotationSpec> extraAnnotations,
      ClassName componentAnnotation,
      Optional<TypeSpec> componentBuilder) {
    this.processingEnv = processingEnv;
    this.name = name;
    this.rootElement = rootElement;
    this.superclass = superclass;
    this.modules = modules.stream().sorted(SIMPLE_NAME_SORTER).collect(toImmutableList());
    this.entryPoints = entryPoints.stream().sorted(TYPE_NAME_SORTER).collect(toImmutableList());
    this.scopes = scopes;
    this.extraAnnotations = extraAnnotations;
    this.componentAnnotation = componentAnnotation;
    this.componentBuilder = componentBuilder;
  }

  public void generate() throws IOException {
    TypeSpec.Builder generator =
        TypeSpec.classBuilder(name)
            // Public because components from a scope below must reference to create
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addOriginatingElement(rootElement)
            .addAnnotation(getComponentAnnotation());

    Processors.addGeneratedAnnotation(
        generator, processingEnv, ClassNames.ROOT_PROCESSOR.toString());

    componentBuilder.ifPresent(generator::addType);

    scopes.forEach(generator::addAnnotation);

    addEntryPoints(generator);

    superclass.ifPresent(generator::superclass);

    generator.addAnnotations(extraAnnotations);

    // (See b/33108646) Typically we would just use JavaFile.Builder to generate the file.
    // However, here we write the file as a string so that we can add new lines between each of the
    // component's super-interfaces
    String fileContent = addNewLinesBetweenInterfaces(generator.build());
    Element[] originatingElements = new Element[] {rootElement};
    writeToJavaSourceFile(processingEnv.getFiler(), name, fileContent, originatingElements);
  }

  /** Returns the component annotation with the list of modules to install for the component. */
  private AnnotationSpec getComponentAnnotation() {
    AnnotationSpec.Builder builder = AnnotationSpec.builder(componentAnnotation);
    modules.forEach(module -> builder.addMember("modules", "$T.class", module));
    return builder.build();
  }

  /** Write a String to a java source file. */
  private static void writeToJavaSourceFile(
      Filer filer, ClassName className, String contents, Element... elements) throws IOException {
    String fileName = className.packageName().isEmpty()
        ? className.simpleName()
        : className.packageName() + "." + className.simpleName();

    JavaFileObject filerSourceFile = filer.createSourceFile(fileName, elements);

    try (Writer writer = filerSourceFile.openWriter()) {
      writer.write(contents);
    } catch (Exception e) {
      try {
        filerSourceFile.delete();
      } catch (Exception ignored) {
        // Nothing to do.
      }
      throw e;
    }
  }

  /** Takes a {@link TypeSpec} and adds new lines between each interface (if more than one). */
  private String addNewLinesBetweenInterfaces(TypeSpec typeSpec) throws IOException {
    StringBuilder sb = new StringBuilder("");
    JavaFile.builder(name.packageName(), typeSpec)
        .build()
        .writeTo(sb);

    String typeDeclaration;
    boolean hasSuperInterfaces;
    if (sb.indexOf("class " + name.simpleName()) >= 0) {
      typeDeclaration = "class " + name.simpleName();
      hasSuperInterfaces = sb.indexOf(typeDeclaration + " implements ") >= 0;
    } else if (sb.indexOf("interface " + name.simpleName()) >= 0) {
      typeDeclaration = "interface " + name.simpleName();
      hasSuperInterfaces = sb.indexOf(typeDeclaration + " extends ") >= 0;
    } else {
      throw new IllegalStateException(
          "ComponentGenerator formatting error. Could not find class/interface declaration.");
    }

    // Only format if the class/interface contains superinterfaces.
    if (hasSuperInterfaces) {
      int i = sb.indexOf(typeDeclaration);
      int end = sb.indexOf("{", i);
      Preconditions.checkState(i >= 0 && end > i);

      // Add a line break after the type declaration
      sb.insert(i + typeDeclaration.length(), "\n    ");

      // Add a line break after each interface so that there's only 1 interface per line.
      int bracketCount = 0;
      while (i >= 0 && i < end) {
        char c = sb.charAt(i++);
        if (c == '<') {
          bracketCount++;
        } else if (c == '>') {
          bracketCount--;
        } else if (c == ',' && bracketCount == 0) {
          sb.insert(i++, "\n   ");
        }
      }
    }

    return sb.toString();
  }

  /**
   * Adds entry points to the component.
   *
   * See b/140979968. If the entry points exceed 65763 bytes, we have to partition them to avoid the
   * limit. To be safe, we split at 60000 bytes.
   */
  private void addEntryPoints(TypeSpec.Builder builder) throws IOException {
    int currBytes = 0;
    List<Integer> partitionIndexes = new ArrayList<>();

    partitionIndexes.add(0);
    for (int i = 0; i < entryPoints.size(); i++) {
      // This over estimates the actual length because it includes the fully qualified name (FQN).
      // TODO(user): Have a better way to estimate the upper bound. For example, most types will
      // not include the FQN, but we'll have to consider all of the different subtypes of TypeName,
      // simple name collisions, etc...
      int nextBytes = Utf8.encodedLength(entryPoints.get(i).toString());

      // To be safe, we split at 60000 to account for the component name, spaces, commas, etc...
      if (currBytes + nextBytes > 60000) {
        partitionIndexes.add(i);
        currBytes = 0;
      }

      currBytes += nextBytes;
    }
    partitionIndexes.add(entryPoints.size());

    if (partitionIndexes.size() <= 2) {
      // No extra partitions are needed, so just add all of the entrypoints as is.
      builder.addSuperinterfaces(entryPoints);
    } else {
      // Create interfaces for each partition.
      // The partitioned interfaces will be added to the component instead of the real entry points.
      for (int i = 1; i < partitionIndexes.size(); i++) {
        int startIndex = partitionIndexes.get(i - 1);
        int endIndex = partitionIndexes.get(i);
        builder.addSuperinterface(
            createPartitionInterface(entryPoints.subList(startIndex, endIndex), i));
      }
    }
  }

  private ClassName createPartitionInterface(List<TypeName> partition, int partitionIndex)
      throws IOException {
    ClassName partitionName = Processors.append(name, "_EntryPointPartition" + partitionIndex);
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(partitionName)
            .addOriginatingElement(rootElement)
            .addModifiers(Modifier.ABSTRACT)
            .addSuperinterfaces(partition);

    Processors.addGeneratedAnnotation(builder, processingEnv, ClassNames.ROOT_PROCESSOR.toString());

    JavaFile.builder(name.packageName(), builder.build()).build().writeTo(processingEnv.getFiler());
    return partitionName;
  }
}

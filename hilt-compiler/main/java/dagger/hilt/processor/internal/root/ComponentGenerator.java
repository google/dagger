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

package dagger.hilt.processor.internal.root;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static java.util.Comparator.comparing;

import androidx.room.compiler.processing.XFiler.Mode;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.base.Joiner;
import com.google.common.base.Utf8;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Generates a Dagger component or subcomponent interface. */
final class ComponentGenerator {
  private static final Joiner JOINER = Joiner.on(".");
  private static final Comparator<ClassName> SIMPLE_NAME_SORTER =
      Comparator.comparing((ClassName c) -> JOINER.join(c.simpleNames()))
          .thenComparing(ClassName::compareTo);
  private static final Comparator<TypeName> TYPE_NAME_SORTER = comparing(TypeName::toString);

  private final XProcessingEnv processingEnv;
  private final ClassName name;
  private final Optional<ClassName> superclass;
  private final ImmutableList<ClassName> modules;
  private final ImmutableList<TypeName> entryPoints;
  private final ImmutableCollection<ClassName> scopes;
  private final ImmutableList<AnnotationSpec> extraAnnotations;
  private final ClassName componentAnnotation;
  private final Optional<TypeSpec> componentBuilder;

  public ComponentGenerator(
      XProcessingEnv processingEnv,
      ClassName name,
      Optional<ClassName> superclass,
      Set<? extends ClassName> modules,
      Set<? extends TypeName> entryPoints,
      ImmutableCollection<ClassName> scopes,
      ImmutableList<AnnotationSpec> extraAnnotations,
      ClassName componentAnnotation,
      Optional<TypeSpec> componentBuilder) {
    this.processingEnv = processingEnv;
    this.name = name;
    this.superclass = superclass;
    this.modules = modules.stream().sorted(SIMPLE_NAME_SORTER).collect(toImmutableList());
    this.entryPoints = entryPoints.stream().sorted(TYPE_NAME_SORTER).collect(toImmutableList());
    this.scopes = scopes;
    this.extraAnnotations = extraAnnotations;
    this.componentAnnotation = componentAnnotation;
    this.componentBuilder = componentBuilder;
  }

  public TypeSpec.Builder typeSpecBuilder() throws IOException {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(name)
            // Public because components from a scope below must reference to create
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(getComponentAnnotation());

    componentBuilder.ifPresent(builder::addType);

    scopes.forEach(builder::addAnnotation);

    addEntryPoints(builder);

    superclass.ifPresent(builder::superclass);

    builder.addAnnotations(extraAnnotations);

    return builder;
  }

  /** Returns the component annotation with the list of modules to install for the component. */
  private AnnotationSpec getComponentAnnotation() {
    AnnotationSpec.Builder builder = AnnotationSpec.builder(componentAnnotation);
    modules.forEach(module -> builder.addMember("modules", "$T.class", module));
    return builder.build();
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
      // TODO(bcorso): Have a better way to estimate the upper bound. For example, most types will
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
    // TODO(bcorso): Nest the partition inside the HiltComponents wrapper rather than appending name
    ClassName partitionName =
        Processors.append(
            Processors.getEnclosedClassName(name), "_EntryPointPartition" + partitionIndex);
    TypeSpec.Builder builder =
        TypeSpec.interfaceBuilder(partitionName)
            .addModifiers(Modifier.ABSTRACT)
            .addSuperinterfaces(partition);

    Processors.addGeneratedAnnotation(builder, processingEnv, ClassNames.ROOT_PROCESSOR.toString());

    processingEnv
        .getFiler()
        .write(JavaFile.builder(name.packageName(), builder.build()).build(), Mode.Isolating);
    return partitionName;
  }
}

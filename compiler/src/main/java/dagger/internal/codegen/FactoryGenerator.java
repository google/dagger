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
package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.squareup.javawriter.JavaWriter;
import dagger.Factory;
import dagger.MembersInjector;
import dagger.Provides;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.squareup.javawriter.JavaWriter.stringLiteral;
import static com.squareup.javawriter.JavaWriter.type;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.collectImportsFromDependencies;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.flattenVariableMap;
import static dagger.internal.codegen.SourceFiles.generateProviderNamesForDependencies;
import static dagger.internal.codegen.SourceFiles.providerUsageStatement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates {@link Factory} implementations from {@link ProvisionBinding} instances for
 * {@link Inject} constructors.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class FactoryGenerator extends SourceFileGenerator<ProvisionBinding> {
  private final ProviderTypeRepository providerTypeRepository;

  FactoryGenerator(Filer filer, ProviderTypeRepository providerTypeRepository) {
    super(filer);
    this.providerTypeRepository = providerTypeRepository;
  }

  @Override
  ClassName nameGeneratedType(ProvisionBinding binding) {
    return factoryNameForProvisionBinding(binding);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(ProvisionBinding binding) {
    return ImmutableSet.of(binding.bindingElement());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProvisionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  void write(ClassName factoryClassName, JavaWriter writer, ProvisionBinding binding)
      throws IOException {
    TypeMirror providedType = binding.providedKey().type();
    String providedTypeString = Util.typeToString(providedType);

    writer.emitPackage(factoryClassName.packageName());

    writeImports(writer, factoryClassName, binding, providedType);

    writer.emitAnnotation(Generated.class, stringLiteral(ComponentProcessor.class.getName()))
        .beginType(factoryClassName.classFileName(), "class", EnumSet.of(PUBLIC, FINAL), null,
            type(Factory.class, Util.typeToString(binding.providedKey().type())));

    final ImmutableBiMap<Key, String> providerNames =
        generateProviderNamesForDependencies(binding.dependencies());

    ImmutableMap.Builder<String, String> variableMapBuilder =
        new ImmutableMap.Builder<String, String>();
    if (binding.bindingKind().equals(PROVISION)) {
      variableMapBuilder.put("module", binding.bindingTypeElement().getQualifiedName().toString());
    }
    if (binding.requiresMemberInjection()) {
      variableMapBuilder.put("membersInjector", type(MembersInjector.class, providedTypeString));
    }
    ImmutableMap<String, String> variableMap = variableMapBuilder
        .putAll(providersAsVariableMap(providerNames))
        .build();

    if (binding.requiresMemberInjection()) {
      writeMembersInjectorField(writer, providedTypeString);
    }
    if (binding.bindingKind().equals(PROVISION)) {
      writeModuleField(writer, binding.bindingTypeElement());
    }
    writeProviderFields(writer, providerNames);

    writeConstructor(writer, variableMap);

    writeGetMethod(writer, binding, providedTypeString, providerNames);

    // TODO(gak): write a sensible toString

    writer.endType();
  }

  private void writeImports(JavaWriter writer, ClassName factoryClassName, ProvisionBinding binding,
      TypeMirror providedType) throws IOException {
    ImmutableSortedSet.Builder<ClassName> importsBuilder =
        ImmutableSortedSet.<ClassName>naturalOrder()
            .addAll(collectImportsFromDependencies(factoryClassName, binding.dependencies()))
            .addAll(SourceFiles.originatingClassNames(getOriginatingElements(binding)))
            .add(ClassName.fromClass(Factory.class))
            .add(ClassName.fromClass(Generated.class));

    if (binding.provisionType().equals(Provides.Type.SET)) {
      importsBuilder.add(ClassName.fromClass(Collections.class));
    }
    if (binding.requiresMemberInjection()) {
      importsBuilder.add(ClassName.fromClass(MembersInjector.class));
    }
    for (TypeElement referencedProvidedType : MoreTypes.referencedTypes(providedType)) {
      ClassName className = ClassName.fromTypeElement(referencedProvidedType);
      if (!className.packageName().equals("java.lang")
          && !className.packageName().equals(factoryClassName.packageName()))
      importsBuilder.add(className);
    }

    writer.emitImports(Collections2.transform(importsBuilder.build(), Functions.toStringFunction()))
        .emitEmptyLine();
  }

  private void writeMembersInjectorField(JavaWriter writer, String providedTypeString)
      throws IOException {
    writer.emitField(type(MembersInjector.class, providedTypeString),
        "membersInjector", EnumSet.of(PRIVATE, FINAL));
  }

  private void writeModuleField(JavaWriter writer, TypeElement moduleType) throws IOException {
    writer.emitField(moduleType.getQualifiedName().toString(), "module",
        EnumSet.of(PRIVATE, FINAL));
  }

  private void writeProviderFields(JavaWriter writer, ImmutableBiMap<Key, String> providerNames)
      throws IOException {
    for (Entry<Key, String> providerEntry : providerNames.entrySet()) {
      Key key = providerEntry.getKey();
      // TODO(gak): provide more elaborate information about which requests relate
      writer.emitJavadoc(key.toString())
          .emitField(providerTypeString(key), providerEntry.getValue(),
              EnumSet.of(PRIVATE, FINAL));
    }
    writer.emitEmptyLine();
  }

  private void writeConstructor(JavaWriter writer, Map<String, String> variableMap)
      throws IOException {
    if (!variableMap.isEmpty()) {
      writer.beginConstructor(EnumSet.of(PUBLIC),
          flattenVariableMap(variableMap),
          ImmutableList.<String>of());
      for (String variableName : variableMap.keySet()) {
        writer.emitStatement("assert %s != null", variableName);
        writer.emitStatement("this.%1$s = %1$s", variableName);
      }
      writer.endConstructor().emitEmptyLine();
    }
  }

  private void writeGetMethod(JavaWriter writer, ProvisionBinding binding,
      String providedTypeString, final ImmutableBiMap<Key, String> providerNames)
          throws IOException {
    writer.emitAnnotation(Override.class)
        .beginMethod(providedTypeString, "get", EnumSet.of(PUBLIC));
    String parameterString =
        Joiner.on(", ").join(FluentIterable.from(binding.dependencies())
            .transform(new Function<DependencyRequest, String>() {
              @Override public String apply(DependencyRequest input) {
                return providerUsageStatement(providerNames.get(input.key()), input.kind());
              }
            }));
    if (binding.bindingKind().equals(PROVISION)) {
      switch (binding.provisionType()) {
        case UNIQUE:
        case SET_VALUES:
          writer.emitStatement("return module.%s(%s)",
              binding.bindingElement().getSimpleName(), parameterString);
          break;
        case SET:
          writer.emitStatement("return Collections.singleton(module.%s(%s))",
              binding.bindingElement().getSimpleName(), parameterString);
          break;
        default:
          throw new AssertionError();
      }
    } else if (binding.requiresMemberInjection()) {
      writer.emitStatement("%1$s instance = new %1$s(%2$s)",
          writer.compressType(providedTypeString), parameterString);
      writer.emitStatement("membersInjector.injectMembers(instance)");
      writer.emitStatement("return instance");
    } else {
      writer.emitStatement("return new %s(%s)",
          writer.compressType(providedTypeString), parameterString);
    }
    writer.endMethod().emitEmptyLine();
  }

  private Map<String, String> providersAsVariableMap(ImmutableBiMap<Key, String> providerNames) {
    return Maps.transformValues(providerNames.inverse(), new Function<Key, String>() {
      @Override public String apply(Key key) {
        return providerTypeString(key);
      }
    });
  }

  private String providerTypeString(Key key) {
    return Util.typeToString(providerTypeRepository.getProviderType(key));
  }
}

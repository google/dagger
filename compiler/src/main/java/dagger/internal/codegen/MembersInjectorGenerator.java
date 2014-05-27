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
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.squareup.javawriter.JavaWriter;
import dagger.MembersInjector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;

import static com.squareup.javawriter.JavaWriter.stringLiteral;
import static com.squareup.javawriter.JavaWriter.type;
import static dagger.internal.codegen.SourceFiles.DEPENDENCY_ORDERING;
import static dagger.internal.codegen.SourceFiles.collectImportsFromDependencies;
import static dagger.internal.codegen.SourceFiles.flattenVariableMap;
import static dagger.internal.codegen.SourceFiles.generateProviderNamesForDependencies;
import static dagger.internal.codegen.SourceFiles.providerUsageStatement;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class MembersInjectorGenerator extends SourceFileGenerator<MembersInjectorDescriptor> {
  private final ProviderTypeRepository providerTypeRepository;

  MembersInjectorGenerator(Filer filer, ProviderTypeRepository providerTypeRepository) {
    super(filer);
    this.providerTypeRepository = providerTypeRepository;
  }

  @Override
  ClassName nameGeneratedType(MembersInjectorDescriptor descriptor) {
    ClassName injectedClassName = descriptor.injectedClassName();
    return injectedClassName.peerNamed(injectedClassName.simpleName() + "$$MembersInjector");
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(
      MembersInjectorDescriptor descriptor) {
    return FluentIterable.from(descriptor.bindings())
        .transform(new Function<MembersInjectionBinding, Element>() {
          @Override public Element apply(MembersInjectionBinding binding) {
            return binding.bindingElement();
          }
        })
        .toSet();
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(MembersInjectorDescriptor input) {
    return Optional.of(input.injectedClass());
  }

  @Override
  void write(ClassName injectorClassName, JavaWriter writer, MembersInjectorDescriptor descriptor)
      throws IOException {
    ClassName injectedClassName = descriptor.injectedClassName();
    ImmutableSortedSet<MembersInjectionBinding> bindings = descriptor.bindings();

    writer.emitPackage(injectedClassName.packageName());

    ImmutableSortedSet<DependencyRequest> dependencies = FluentIterable.from(descriptor.bindings())
        .transformAndConcat(new Function<MembersInjectionBinding, Set<DependencyRequest>>() {
          @Override public Set<DependencyRequest> apply(MembersInjectionBinding input) {
            return input.dependencies();
          }
        })
        .toSortedSet(DEPENDENCY_ORDERING);

    List<ClassName> importsBuilder = new ArrayList<ClassName>();
    importsBuilder.addAll(collectImportsFromDependencies(injectorClassName, dependencies));
    importsBuilder.addAll(SourceFiles.originatingClassNames(getOriginatingElements(descriptor)));
    importsBuilder.add(ClassName.fromClass(MembersInjector.class));
    importsBuilder.add(ClassName.fromClass(Generated.class));
    ImmutableSortedSet<String> imports = FluentIterable.from(importsBuilder)
        .transform(Functions.toStringFunction())
        .toSortedSet(Ordering.natural());
    writer.emitImports(imports).emitEmptyLine();

    writer.emitJavadoc("A {@link MembersInjector} implementation for {@link %s}.",
        injectedClassName.simpleName());

    String membersInjectorType = type(MembersInjector.class, injectedClassName.simpleName());
    // @Generated("dagger.internal.codegen.InjectProcessor")
    // public final class Blah$$MembersInjector implements MembersInjector<Blah>
    writer.emitAnnotation(Generated.class, stringLiteral(ComponentProcessor.class.getName()))
        .beginType(injectorClassName.classFileName(), "class", EnumSet.of(FINAL), null,
            membersInjectorType);


    final ImmutableBiMap<Key, String> providerNames =
        generateProviderNamesForDependencies(dependencies);

    // Add the fields
    writeProviderFields(writer, providerNames);

    // Add the constructor
    writeConstructor(writer, providerNames);

    // @Override public void injectMembers(Blah instance)
    writer.emitAnnotation(Override.class)
        .beginMethod("void", "injectMembers", EnumSet.of(PUBLIC),
            injectedClassName.simpleName(), "instance");
    // TODO(gak): figure out what (if anything) to do about being passed a subtype of the class
    // specified as the type parameter for the MembersInjector.
    writer.beginControlFlow("if (instance == null)")
        .emitStatement(
            "throw new NullPointerException(\"Cannot inject members into a null reference\")")
        .endControlFlow();

    for (MembersInjectionBinding binding : bindings) {
      Element target = binding.bindingElement();
      switch (target.getKind()) {
        case FIELD:
          Name fieldName = ((VariableElement) target).getSimpleName();
          DependencyRequest singleDependency = Iterables.getOnlyElement(binding.dependencies());
          String providerName = providerNames.get(singleDependency.key());
          writer.emitStatement("instance.%s = %s",
              fieldName, providerUsageStatement(providerName, singleDependency.kind()));
          break;
        case METHOD:
          Name methodName = ((ExecutableElement) target).getSimpleName();
          String parameterString =
              Joiner.on(", ").join(FluentIterable.from(binding.dependencies())
                  .transform(new Function<DependencyRequest, String>() {
                    @Override public String apply(DependencyRequest input) {
                      return providerUsageStatement(providerNames.get(input.key()), input.kind());
                    }
                  }));
          writer.emitStatement("instance.%s(%s)", methodName, parameterString);
          break;
        default:
          throw new IllegalStateException(target.getKind().toString());
      }
    }
    writer.endMethod();

    writer.endType();
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

  private void writeConstructor(JavaWriter writer, ImmutableBiMap<Key, String> providerNames)
      throws IOException {
    writer.beginConstructor(EnumSet.noneOf(Modifier.class),
        flattenVariableMap(providersAsVariableMap(providerNames)),
        ImmutableList.<String>of());
    for (String providerName : providerNames.values()) {
      writer.emitStatement("assert %s != null", providerName);
      writer.emitStatement("this.%1$s = %1$s", providerName);
    }
    writer.endConstructor().emitEmptyLine();
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

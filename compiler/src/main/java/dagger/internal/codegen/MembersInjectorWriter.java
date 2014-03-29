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

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.squareup.javawriter.JavaWriter.stringLiteral;
import static com.squareup.javawriter.JavaWriter.type;
import static dagger.internal.codegen.JavaWriterUtil.flattenVariableMap;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.squareup.javawriter.JavaWriter;

import dagger.Lazy;
import dagger.MembersInjector;
import dagger.internal.DoubleCheckLazy;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

/**
 * Writes {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class MembersInjectorWriter {
  private final Filer filer;
  private final ProviderTypeRepository providerTypeRepository;

  MembersInjectorWriter(Filer filer, ProviderTypeRepository providerTypeRepository) {
    this.filer = checkNotNull(filer);
    this.providerTypeRepository = providerTypeRepository;
  }

  /**
   * Writes a new source file for the generated {@link MembersInjector}.
   *
   * @throws IllegalArgumentException if the given bindings are not all for the same
   *     {@link MembersInjectionBinding#targetEnclosingType()}.
   */
  void write(ImmutableList<MembersInjectionBinding> bindings) throws IOException {
    checkNotNull(bindings);
    FluentIterable<MembersInjectionBinding> fluentBindings = FluentIterable.from(bindings);
    checkArgument(!fluentBindings.isEmpty());
    TypeElement injectedTypeElement = Iterables.getOnlyElement(fluentBindings
        .transform(new Function<MembersInjectionBinding, TypeElement>() {
          @Override public TypeElement apply(MembersInjectionBinding binding) {
            return binding.targetEnclosingType();
          }
        })
        .toSet());


    ClassName injectedClassName = ClassName.fromTypeElement(injectedTypeElement);
    ClassName injectorClassName =
        injectedClassName.peerNamed(injectedClassName.simpleName() + "$$MembersInjector");

    JavaFileObject sourceFile = filer.createSourceFile(injectorClassName.fullyQualifiedName(),
        fluentBindings.transform(new Function<MembersInjectionBinding, Element>() {
          @Override public Element apply(MembersInjectionBinding binding) {
            return binding.target();
          }
        })
        .toArray(Element.class));

    JavaWriter writer = new JavaWriter(sourceFile.openWriter());
    try {
      writer.emitPackage(injectedClassName.packageName());


      for (ClassName className : collectImports(injectorClassName, fluentBindings)) {
        writer.emitImports(className.fullyQualifiedName());
      }
      writer.emitEmptyLine();

      writer.emitJavadoc("A {@link MembersInjector} implementation for {@link %s}.",
          injectedClassName.simpleName());

      String membersInjectorType = type(MembersInjector.class, injectedClassName.simpleName());
      // @Generated("dagger.internal.codegen.InjectProcessor")
      // public final class Blah$$MembersInjector implements MembersInjector<Blah>
      writer.emitAnnotation(Generated.class, stringLiteral(InjectProcessor.class.getName()))
          .beginType(injectorClassName.simpleName(), "class", EnumSet.of(FINAL), null,
              membersInjectorType);

      // Require a Provider/MembersInjector for each request
      ImmutableSetMultimap.Builder<Key, DependencyRequest> dependenciesByKeyBuilder =
          new ImmutableSetMultimap.Builder<Key, DependencyRequest>()
              .orderValuesBy(DEPENDENCY_ORDERING);
      for (MembersInjectionBinding binding : bindings) {
        for (DependencyRequest dependency : binding.dependencies()) {
          dependenciesByKeyBuilder.put(dependency.key(), dependency);
        }
      }
      ImmutableSetMultimap<Key, DependencyRequest> dependenciesByKey =
          dependenciesByKeyBuilder.build();


      final ImmutableBiMap<Key, String> providerNames = generateProviderNames(dependenciesByKey);

      // Add the fields
      writeProviderFields(writer, providerNames);

      // Add the constructor
      writeConstructor(writer, providerNames);

      // @Override public void injectMembers(Blah instance)
      writer.emitAnnotation(Override.class)
          .beginMethod("void", "injectMembers", EnumSet.of(PUBLIC),
              injectedClassName.simpleName(), "instance");
      writer.beginControlFlow("if (instance == null)")
          .emitStatement(
              "throw new NullPointerException(\"Cannot inject members into a null reference\")")
          .endControlFlow();

      for (MembersInjectionBinding binding : bindings) {
        Element target = binding.target();
        switch (target.getKind()) {
          case FIELD:
            Name fieldName = ((VariableElement) target).getSimpleName();
            DependencyRequest singleDependency = Iterables.getOnlyElement(binding.dependencies());
            String providerName = providerNames.get(singleDependency.key());
            switch (singleDependency.kind()) {
              case LAZY:
                writer.emitStatement("instance.%s = %s.create(%s)",
                    fieldName, DoubleCheckLazy.class.getSimpleName(), providerName);
                break;
              case INSTANCE:
                writer.emitStatement("instance.%s = %s.get()", fieldName, providerName);
                break;
              case PROVIDER:
                writer.emitStatement("instance.%s = %s", fieldName, providerName);
                break;
              default:
                throw new AssertionError();
            }
            break;
          case METHOD:
            Name methodName = ((ExecutableElement) target).getSimpleName();
            String parameterString =
                Joiner.on(", ").join(FluentIterable.from(binding.dependencies())
                    .transform(new Function<DependencyRequest, String>() {
                      @Override public String apply(DependencyRequest input) {
                        String providerName = providerNames.get(input.key());
                        switch (input.kind()) {
                          case LAZY:
                            return String.format("%s.create(%s)",
                                DoubleCheckLazy.class.getSimpleName(), providerName);
                          case INSTANCE:
                            return String.format("%s.get()", providerName);
                          case PROVIDER:
                            return String.format("%s", providerName);
                          default:
                            throw new AssertionError();
                        }
                      }
                    }));
            writer.emitStatement("instance.%s(%s)", methodName, parameterString);
            break;
          default:
            throw new IllegalStateException(target.getKind().toString());
        }
      }
      writer.endMethod();

      writeToString(writer, injectedClassName);

      writer.endType();
    } finally {
      writer.close();
      // TODO(gak): clean up malformed files caused by failures
    }
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
    writer.emitAnnotation(Inject.class);
    writer.beginConstructor(EnumSet.noneOf(Modifier.class),
        flattenVariableMap(providersAsVariableMap(providerNames)),
        ImmutableList.<String>of());
    for (String providerName : providerNames.values()) {
      writer.emitStatement("assert %s != null", providerName);
      writer.emitStatement("this.%1$s = %1$s", providerName);
    }
    writer.endConstructor().emitEmptyLine();
  }

  private void writeToString(JavaWriter writer, ClassName injectedClassName) throws IOException {
    writer.emitAnnotation(Override.class)
        .beginMethod("String", "toString", EnumSet.of(PUBLIC))
        .emitStatement("return \"MembersInjector<%s>\"", injectedClassName.simpleName())
        .endMethod();
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

  /**
   * Returns the sorted set of all classes required by the {@link MembersInjector} implementation
   * being generated.
   */
  private ImmutableSortedSet<ClassName> collectImports(ClassName topLevelClassName,
      Iterable<MembersInjectionBinding> bindings) {
    ImmutableSortedSet.Builder<ClassName> builder = ImmutableSortedSet.<ClassName>naturalOrder()
        .add(ClassName.fromClass(Inject.class))
        .add(ClassName.fromClass(MembersInjector.class))
        .add(ClassName.fromClass(Generated.class));
    ImmutableSet<String> packagesToSkip  =
        ImmutableSet.of("java.lang", topLevelClassName.packageName());
    for (MembersInjectionBinding binding : bindings) {
      for (DependencyRequest dependency : binding.dependencies()) {
        ImmutableSet<TypeElement> referencedTypes =
            Mirrors.referencedTypes(dependency.key().type());
        switch (dependency.kind()) {
          case LAZY:
            builder.add(ClassName.fromClass(Lazy.class), ClassName.fromClass(DoubleCheckLazy.class));
            // fall through
          case INSTANCE:
          case PROVIDER:
            builder.add(ClassName.fromClass(Provider.class));
            break;
          default:
            throw new AssertionError();
        }
        for (TypeElement referencedType : referencedTypes) {
          ClassName className = ClassName.fromTypeElement(referencedType);
          // don't include classes in java.lang or the same package
          if (!packagesToSkip.contains(className.packageName())
              // or that are members of the same top-level class
              && !className.nameOfTopLevelClass().equals(topLevelClassName)) {
            builder.add(className);
          }
        }
      }
    }
    return builder.build();
  }


  /**
   * Sorts {@link DependencyRequest} instances in an order likely to reflect their logical
   * importance.
   */
  private static final Ordering<DependencyRequest> DEPENDENCY_ORDERING =
      new Ordering<DependencyRequest>() {
        @Override
        public int compare(DependencyRequest left, DependencyRequest right) {
          return ComparisonChain.start()
              // put fields before parameters
              .compare(left.requestElement().getKind(), right.requestElement().getKind())
              // order by dependency kind
              .compare(left.kind(), right.kind())
              // then sort by name
              .compare(
                  left.requestElement().getSimpleName().toString(),
                  right.requestElement().getSimpleName().toString())
              .result();
        }
      };

  /**
   * This method generates names for the {@link Provider} references necessary for all of the
   * bindings. It is responsible for the following:
   * <ul>
   * <li>Choosing a name that associates the provider with all of the dependency requests for this
   * type.
   * <li>Choosing a name that is <i>probably</i> associated with the type being provided.
   * <li>Ensuring that no two providers end up with the same name.
   * </ul>
   *
   * @return Returns the mapping from {@link Key} to provider name sorted by the name of the
   * provider.
   */
  private ImmutableBiMap<Key, String> generateProviderNames(
      SetMultimap<Key, DependencyRequest> dependenciesByKey) {
    Map<Key, Collection<DependencyRequest>> dependenciesByKeyMap = dependenciesByKey.asMap();
    BiMap<Key, String> providerNames = HashBiMap.create(dependenciesByKeyMap.size());
    for (Entry<Key, Collection<DependencyRequest>> entry : dependenciesByKeyMap.entrySet()) {
      // collect together all of the names that we would want to call the provider
      ImmutableSet<String> dependencyNames = FluentIterable.from(entry.getValue())
          .transform(new Function<DependencyRequest, String>() {
            @Override public String apply(DependencyRequest input) {
              return nameForDependency(input);
            }
          })
          .toSet();

      final String baseProviderName;
      if (dependencyNames.size() == 1) {
        // if there's only one name, great!  use it!
        String name = Iterables.getOnlyElement(dependencyNames);
        baseProviderName = name.endsWith("Provider") ? name : name + "Provider";
      } else {
        // in the event that a provider is being used for a bunch of deps with different names,
        // add all the names together with "And"s in the middle.  E.g.: stringAndS
        Iterator<String> namesIterator = dependencyNames.iterator();
        String first = namesIterator.next();
        StringBuilder compositeNameBuilder = new StringBuilder(first);
        while (namesIterator.hasNext()) {
          compositeNameBuilder.append("And")
              .append(CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL, namesIterator.next()));
        }
        baseProviderName = compositeNameBuilder.append("Provider").toString();
      }

      // in the unlikely event that we have more that one provider with the exact same name,
      // just add numbers at the end until there is no collision
      String candidateName = baseProviderName;
      for (int candidateNum = 2; providerNames.containsValue(candidateName); candidateNum++) {
        candidateName = baseProviderName + candidateNum;
      }

      providerNames.put(entry.getKey(), candidateName);
    }
    // return the map so that it is sorted by name
    return ImmutableBiMap.copyOf(ImmutableSortedMap.copyOf(providerNames.inverse())).inverse();
  }

  /**
   * Picks a reasonable name for what we think is being provided from the variable name associated
   * with the {@link DependencyRequest}.  I.e. strips out words like "lazy" and "Provider" if we
   * believe that those refer to {@link Lazy} and {@link Provider} rather than the type being
   * provided.
   */
  // TODO(gak): develop the heuristics to get better names
  private String nameForDependency(DependencyRequest dependency) {
    String variableName = dependency.requestElement().getSimpleName().toString();
    switch (dependency.kind()) {
      case INSTANCE:
        return variableName;
      case LAZY:
        return variableName.startsWith("lazy") && !variableName.equals("lazy")
            ? Ascii.toLowerCase(variableName.charAt(4)) + variableName.substring(5)
            : variableName;
      case PROVIDER:
        return variableName.endsWith("Provider") && !variableName.equals("Provider")
            ? variableName.substring(0, variableName.length() - 8)
            : variableName;
      default:
        throw new AssertionError();
    }
  }
}

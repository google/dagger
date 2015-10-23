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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Component;
import dagger.MembersInjector;
import dagger.internal.Factory;
import dagger.internal.InstanceFactory;
import dagger.internal.MapFactory;
import dagger.internal.MapProviderFactory;
import dagger.internal.MembersInjectors;
import dagger.internal.ScopedProvider;
import dagger.internal.SetFactory;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ContributionBinding.BindingType;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ClassWriter;
import dagger.internal.codegen.writer.ConstructorWriter;
import dagger.internal.codegen.writer.FieldWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.StringLiteral;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.internal.codegen.writer.TypeWriter;
import dagger.internal.codegen.writer.VoidName;
import dagger.producers.Producer;
import dagger.producers.internal.Producers;
import dagger.producers.internal.SetProducer;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.Binding.bindingPackageFor;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;
import static dagger.internal.codegen.MapKeys.getMapKeySnippet;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.NO_OP;
import static dagger.internal.codegen.ProvisionBinding.FactoryCreationStrategy.ENUM_INSTANCE;
import static dagger.internal.codegen.ProvisionBinding.Kind.PROVISION;
import static dagger.internal.codegen.SourceFiles.factoryNameForProductionBinding;
import static dagger.internal.codegen.SourceFiles.factoryNameForProvisionBinding;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForMembersInjectionBinding;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static dagger.internal.codegen.Util.getKeyTypeOfMap;
import static dagger.internal.codegen.Util.getProvidedValueTypeOfMap;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

/**
 * Generates the implementation of the abstract types annotated with {@link Component}.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ComponentGenerator extends SourceFileGenerator<BindingGraph> {
  private final Types types;
  private final Diagnostic.Kind nullableValidationType;

  ComponentGenerator(Filer filer, Types types, Diagnostic.Kind nullableValidationType) {
    super(filer);
    this.types = types;
    this.nullableValidationType = nullableValidationType;
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    ClassName componentDefinitionClassName =
        ClassName.fromTypeElement(input.componentDescriptor().componentDefinitionType());
    String componentName =
        "Dagger" + componentDefinitionClassName.classFileName().replace('$', '_');
    return componentDefinitionClassName.topLevelClassName().peerNamed(componentName);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(BindingGraph input) {
    return ImmutableSet.of(input.componentDescriptor().componentDefinitionType());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
    return Optional.of(input.componentDescriptor().componentDefinitionType());
  }

  @AutoValue
  static abstract class ProxyClassAndField {
    abstract ClassWriter proxyWriter();
    abstract FieldWriter proxyFieldWriter();

    static ProxyClassAndField create(ClassWriter proxyWriter, FieldWriter proxyFieldWriter) {
      return new AutoValue_ComponentGenerator_ProxyClassAndField(proxyWriter, proxyFieldWriter);
    }
  }

  @AutoValue static abstract class MemberSelect {
    static MemberSelect instanceSelect(ClassName owningClass, Snippet snippet) {
      return new AutoValue_ComponentGenerator_MemberSelect(
          Optional.<TypeName> absent(), owningClass, false, snippet);
    }

    static MemberSelect staticSelect(ClassName owningClass, Snippet snippet) {
      return new AutoValue_ComponentGenerator_MemberSelect(
          Optional.<TypeName> absent(), owningClass, true, snippet);
    }

    static MemberSelect staticMethodInvocationWithCast(
        ClassName owningClass, Snippet snippet, TypeName castType) {
      return new AutoValue_ComponentGenerator_MemberSelect(
          Optional.of(castType), owningClass, true, snippet);
    }

    /**
     * This exists only to facilitate edge cases in which we need to select a member, but that
     * member uses a type parameter that can't be inferred.
     */
    abstract Optional<TypeName> selectedCast();
    abstract ClassName owningClass();
    abstract boolean staticMember();
    abstract Snippet snippet();

    private Snippet qualifiedSelectSnippet() {
      return Snippet.format(
          "%s" + (staticMember() ? "" : ".this") + ".%s",
          owningClass(), snippet());
    }

    Snippet getSnippetWithRawTypeCastFor(ClassName usingClass) {
      Snippet snippet = getSnippetFor(usingClass);
      return selectedCast().isPresent()
          ? Snippet.format("(%s) %s", selectedCast().get(), snippet)
          : snippet;
    }

    Snippet getSnippetFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? snippet()
          : qualifiedSelectSnippet();
    }
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName componentName, BindingGraph input) {
    TypeElement componentDefinitionType = input.componentDescriptor().componentDefinitionType();
    ClassName componentDefinitionTypeName =
        ClassName.fromTypeElement(componentDefinitionType);

    JavaWriter writer = JavaWriter.inPackage(componentName.packageName());

    ClassWriter componentWriter = writer.addClass(componentName.simpleName());
    componentWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getCanonicalName());
    componentWriter.addModifiers(PUBLIC, FINAL);
    checkState(componentDefinitionType.getModifiers().contains(ABSTRACT));
    componentWriter.setSupertype(componentDefinitionType);

    Set<JavaWriter> javaWriters = Sets.newHashSet();
    javaWriters.add(writer);
    writeComponent(input, componentDefinitionTypeName, componentWriter, javaWriters);

    return ImmutableSet.copyOf(javaWriters);
  }

  /**
   * Writes out a builder for a component or subcomponent.
   *
   * @param input the component or subcomponent
   * @param componentApiName the API name of the component we're building (not our impl)
   * @param componentImplName the implementation name of the component we're building
   * @param componentWriter the class we're adding this builder to
   * @param componentContributionFields a map of member selects so we can later use the fields
   */
  private ClassWriter writeBuilder(BindingGraph input, ClassName componentApiName,
      ClassName componentImplName, ClassWriter componentWriter,
      Map<TypeElement, MemberSelect> componentContributionFields) {
    ClassWriter builderWriter;
    Optional<BuilderSpec> builderSpec = input.componentDescriptor().builderSpec();
    switch (input.componentDescriptor().kind()) {
      case COMPONENT:
      case PRODUCTION_COMPONENT:
        builderWriter = componentWriter.addNestedClass("Builder");
        builderWriter.addModifiers(STATIC);

        // Only top-level components have the factory builder() method.
        // Mirror the user's builder API type if they had one.
        MethodWriter builderFactoryMethod = builderSpec.isPresent()
            ? componentWriter.addMethod(
                builderSpec.get().builderDefinitionType().asType(), "builder")
            : componentWriter.addMethod(builderWriter, "builder");
        builderFactoryMethod.addModifiers(PUBLIC, STATIC);
        builderFactoryMethod.body().addSnippet("return new %s();", builderWriter.name());
        break;
      case SUBCOMPONENT:
        verify(builderSpec.isPresent()); // we only write subcomponent builders if there was a spec
        builderWriter =
            componentWriter.addNestedClass(componentApiName.simpleName() + "Builder");
        break;
      default:
        throw new IllegalStateException();
    }
    builderWriter.addModifiers(FINAL);
    builderWriter.addConstructor().addModifiers(PRIVATE);
    if (builderSpec.isPresent()) {
      builderWriter.addModifiers(PRIVATE);
      builderWriter.setSupertype(builderSpec.get().builderDefinitionType());
    } else {
      builderWriter.addModifiers(PUBLIC);
    }

    // the full set of types that calling code uses to construct a component instance
    ImmutableMap<TypeElement, String> componentContributionNames =
        ImmutableMap.copyOf(Maps.asMap(
            input.componentRequirements(),
            Functions.compose(
                CaseFormat.UPPER_CAMEL.converterTo(LOWER_CAMEL),
                new Function<TypeElement, String>() {
                  @Override public String apply(TypeElement input) {
                    return input.getSimpleName().toString();
                  }
                })));

    MethodWriter buildMethod;
    if (builderSpec.isPresent()) {
      ExecutableElement specBuildMethod = builderSpec.get().buildMethod();
      // Note: we don't use the specBuildMethod.getReturnType() as the return type
      // because it might be a type variable.  We make use of covariant returns to allow
      // us to return the component type, which will always be valid.
      buildMethod = builderWriter.addMethod(componentApiName,
          specBuildMethod.getSimpleName().toString());
      buildMethod.annotate(Override.class);
    } else {
      buildMethod = builderWriter.addMethod(componentApiName, "build");
    }
    buildMethod.addModifiers(PUBLIC);

    for (Entry<TypeElement, String> entry : componentContributionNames.entrySet()) {
      TypeElement contributionElement = entry.getKey();
      String contributionName = entry.getValue();
      FieldWriter builderField = builderWriter.addField(contributionElement, contributionName);
      builderField.addModifiers(PRIVATE);
      componentContributionFields.put(contributionElement, MemberSelect.instanceSelect(
          componentImplName, Snippet.format("builder.%s", builderField.name())));
      if (componentCanMakeNewInstances(contributionElement)) {
        buildMethod.body()
            .addSnippet("if (%s == null) {", builderField.name())
            .addSnippet("  this.%s = new %s();",
                builderField.name(), ClassName.fromTypeElement(contributionElement))
            .addSnippet("}");
      } else {
        buildMethod.body()
            .addSnippet("if (%s == null) {", builderField.name())
            .addSnippet("  throw new IllegalStateException(\"%s must be set\");",
                builderField.name())
            .addSnippet("}");
      }
      MethodWriter builderMethod;
      boolean returnsVoid = false;
      if (builderSpec.isPresent()) {
        ExecutableElement method = builderSpec.get().methodMap().get(contributionElement);
        if (method == null) { // no method in the API, nothing to write out.
          continue;
        }
        // If the return type is void, we add a method with the void return type.
        // Otherwise we use the builderWriter and take advantage of covariant returns
        // (so that we don't have to worry about setter methods that return type variables).
        if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
          returnsVoid = true;
          builderMethod =
              builderWriter.addMethod(method.getReturnType(), method.getSimpleName().toString());
        } else {
          builderMethod = builderWriter.addMethod(builderWriter, method.getSimpleName().toString());
        }
        builderMethod.annotate(Override.class);
      } else {
        builderMethod = builderWriter.addMethod(builderWriter, contributionName);
      }
      // TODO(gak): Mirror the API's visibility.
      // (Makes no difference to the user since this class is private,
      //  but makes generated code prettier.)
      builderMethod.addModifiers(PUBLIC);
      builderMethod.addParameter(contributionElement, contributionName);
      builderMethod.body()
          .addSnippet("if (%s == null) {", contributionName)
          .addSnippet("  throw new NullPointerException(%s);",
              StringLiteral.forValue(contributionName))
          .addSnippet("}")
          .addSnippet("this.%s = %s;", builderField.name(), contributionName);
      if (!returnsVoid) {
        builderMethod.body().addSnippet("return this;");
      }
    }
    buildMethod.body().addSnippet("return new %s(this);", componentImplName);
    return builderWriter;
  }

  /** Returns true if the graph has any dependents that can't be automatically constructed. */
  private boolean requiresUserSuppliedDependents(BindingGraph input) {
    Set<TypeElement> userRequiredDependents =
        Sets.filter(input.componentRequirements(), new Predicate<TypeElement>() {
          @Override public boolean apply(TypeElement input) {
            return !Util.componentCanMakeNewInstances(input);
          }
        });
    return !userRequiredDependents.isEmpty();
  }

  private ImmutableMap<BindingKey, MemberSelect> writeComponent(
      BindingGraph input, ClassName componentDefinitionTypeName, ClassWriter componentWriter,
      Set<JavaWriter> proxyWriters) {
    Map<TypeElement, MemberSelect> componentContributionFields = Maps.newHashMap();
    ClassWriter builderWriter = writeBuilder(input, componentDefinitionTypeName,
        componentWriter.name(), componentWriter, componentContributionFields);
    if (!requiresUserSuppliedDependents(input)) {
      MethodWriter factoryMethod = componentWriter.addMethod(componentDefinitionTypeName, "create");
      factoryMethod.addModifiers(PUBLIC, STATIC);
      // TODO(gak): replace this with something that doesn't allocate a builder
      factoryMethod.body().addSnippet("return builder().%s();",
          input.componentDescriptor().builderSpec().isPresent()
              ? input.componentDescriptor().builderSpec().get().buildMethod().getSimpleName()
              : "build");
    }

    Map<BindingKey, MemberSelect> memberSelectSnippetsBuilder = Maps.newHashMap();
    Map<ContributionBinding, Snippet> multibindingContributionSnippetsBuilder = Maps.newHashMap();
    ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder = ImmutableSet.builder();

    Map<String, ProxyClassAndField> packageProxies = Maps.newHashMap();

    writeFields(input,
        componentWriter,
        proxyWriters,
        memberSelectSnippetsBuilder,
        multibindingContributionSnippetsBuilder,
        enumBindingKeysBuilder,
        packageProxies);

    ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets =
        ImmutableMap.copyOf(memberSelectSnippetsBuilder);
    ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets =
        ImmutableMap.copyOf(multibindingContributionSnippetsBuilder);
    ImmutableSet<BindingKey> enumBindingKeys = enumBindingKeysBuilder.build();

    ConstructorWriter constructorWriter = componentWriter.addConstructor();
    constructorWriter.addModifiers(PRIVATE);
    constructorWriter.addParameter(builderWriter, "builder");
    constructorWriter.body().addSnippet("assert builder != null;");
    initializeFrameworkTypes(input,
        componentWriter,
        constructorWriter,
        Optional.of(builderWriter.name()),
        componentContributionFields,
        memberSelectSnippets,
        ImmutableMap.<ContributionBinding, Snippet>of(),
        multibindingContributionSnippets);

    writeInterfaceMethods(input, componentWriter, memberSelectSnippets, enumBindingKeys);

    for (Entry<ExecutableElement, BindingGraph> subgraphEntry : input.subgraphs().entrySet()) {
      writeSubcomponent(componentWriter,
          MoreTypes.asDeclared(input.componentDescriptor().componentDefinitionType().asType()),
          proxyWriters,
          memberSelectSnippets,
          multibindingContributionSnippets,
          subgraphEntry.getKey(),
          subgraphEntry.getValue());
    }

    return memberSelectSnippets;
  }

  private void writeSubcomponent(ClassWriter componentWriter,
      DeclaredType containingComponent,
      Set<JavaWriter> proxyWriters,
      ImmutableMap<BindingKey, MemberSelect> parentMemberSelectSnippets,
      ImmutableMap<ContributionBinding, Snippet> parentMultibindingContributionSnippets,
      ExecutableElement subcomponentFactoryMethod,
      BindingGraph subgraph) {
    ClassName subcomponentApiName =
        ClassName.fromTypeElement(subgraph.componentDescriptor().componentDefinitionType());
    ClassWriter subcomponentWriter =
        componentWriter.addNestedClass(subcomponentApiName.simpleName() + "Impl");
    subcomponentWriter.addModifiers(PRIVATE, FINAL);

    ConstructorWriter constructorWriter = subcomponentWriter.addConstructor();
    constructorWriter.addModifiers(PRIVATE);
    constructorWriter.body();

    Map<TypeElement, MemberSelect> componentContributionFields = Maps.newHashMap();
    ImmutableList.Builder<Snippet> subcomponentConstructorParameters = ImmutableList.builder();

    TypeMirror subcomponentType;
    MethodWriter componentMethod;
    Optional<ClassName> builderName;
    if (subgraph.componentDescriptor().builderSpec().isPresent()) {
      BuilderSpec spec = subgraph.componentDescriptor().builderSpec().get();
      subcomponentType = spec.componentType();
      componentMethod = componentWriter.addMethod(
          ClassName.fromTypeElement(spec.builderDefinitionType()),
          subcomponentFactoryMethod.getSimpleName().toString());
      ClassWriter builderWriter = writeBuilder(subgraph, subcomponentApiName,
          subcomponentWriter.name(), componentWriter, componentContributionFields);
      builderName = Optional.of(builderWriter.name());
      constructorWriter.addParameter(builderWriter, "builder");
      constructorWriter.body().addSnippet("assert builder != null;");
      componentMethod.body().addSnippet("return new %s();", builderWriter.name());
    } else {
      builderName = Optional.absent();
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(types.asMemberOf(containingComponent, subcomponentFactoryMethod));
      subcomponentType = resolvedMethod.getReturnType();
      componentMethod = componentWriter.addMethod(subcomponentType,
          subcomponentFactoryMethod.getSimpleName().toString());
      writeSubcomponentWithoutBuilder(subcomponentFactoryMethod,
          subgraph,
          subcomponentWriter,
          constructorWriter,
          componentContributionFields,
          subcomponentConstructorParameters,
          componentMethod,
          resolvedMethod);
    }
    componentMethod.addModifiers(PUBLIC);
    componentMethod.annotate(Override.class);

    TypeElement subcomponentElement = MoreTypes.asTypeElement(subcomponentType);
    checkState(subcomponentElement.getModifiers().contains(ABSTRACT));
    subcomponentWriter.setSupertype(subcomponentElement);

    Map<BindingKey, MemberSelect> memberSelectSnippetsBuilder = Maps.newHashMap();

    Map<ContributionBinding, Snippet> multibindingContributionSnippetsBuilder = Maps.newHashMap();
    ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder = ImmutableSet.builder();

    Map<String, ProxyClassAndField> packageProxies = Maps.newHashMap();

    writeFields(subgraph,
        subcomponentWriter,
        proxyWriters,
        memberSelectSnippetsBuilder,
        multibindingContributionSnippetsBuilder,
        enumBindingKeysBuilder,
        packageProxies);

    for (Entry<BindingKey, MemberSelect> parentBindingEntry :
        parentMemberSelectSnippets.entrySet()) {
      if (!memberSelectSnippetsBuilder.containsKey(parentBindingEntry.getKey())) {
        memberSelectSnippetsBuilder.put(parentBindingEntry.getKey(), parentBindingEntry.getValue());
      }
    }

    ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets =
        ImmutableMap.copyOf(memberSelectSnippetsBuilder);
    ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets =
        ImmutableMap.copyOf(multibindingContributionSnippetsBuilder);
    ImmutableSet<BindingKey> enumBindingKeys = enumBindingKeysBuilder.build();

    initializeFrameworkTypes(subgraph,
        subcomponentWriter,
        constructorWriter,
        builderName,
        componentContributionFields,
        memberSelectSnippets,
        parentMultibindingContributionSnippets,
        multibindingContributionSnippets);

    writeInterfaceMethods(subgraph, subcomponentWriter, memberSelectSnippets, enumBindingKeys);

    for (Entry<ExecutableElement, BindingGraph> subgraphEntry : subgraph.subgraphs().entrySet()) {
      writeSubcomponent(subcomponentWriter,
          MoreTypes.asDeclared(subgraph.componentDescriptor().componentDefinitionType().asType()),
          proxyWriters,
          memberSelectSnippets,
          new ImmutableMap.Builder<ContributionBinding, Snippet>()
              .putAll(parentMultibindingContributionSnippets)
              .putAll(multibindingContributionSnippets)
              .build(),
          subgraphEntry.getKey(),
          subgraphEntry.getValue());
    }
  }

  private void writeSubcomponentWithoutBuilder(ExecutableElement subcomponentFactoryMethod,
      BindingGraph subgraph,
      ClassWriter subcomponentWriter,
      ConstructorWriter constructorWriter,
      Map<TypeElement, MemberSelect> componentContributionFields,
      ImmutableList.Builder<Snippet> subcomponentConstructorParameters,
      MethodWriter componentMethod,
      ExecutableType resolvedMethod) {
    List<? extends VariableElement> params = subcomponentFactoryMethod.getParameters();
    List<? extends TypeMirror> paramTypes = resolvedMethod.getParameterTypes();
    for (int i = 0; i < params.size(); i++) {
      VariableElement moduleVariable = params.get(i);
      TypeElement moduleTypeElement = MoreTypes.asTypeElement(paramTypes.get(i));
      TypeName moduleType = TypeNames.forTypeMirror(paramTypes.get(i));
      componentMethod.addParameter(moduleType, moduleVariable.getSimpleName().toString());
      if (!componentContributionFields.containsKey(moduleTypeElement)) {
        String preferredModuleName = CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL,
            moduleTypeElement.getSimpleName().toString());
        FieldWriter contributionField =
            subcomponentWriter.addField(moduleTypeElement, preferredModuleName);
        contributionField.addModifiers(PRIVATE, FINAL);
        String actualModuleName = contributionField.name();
        constructorWriter.addParameter(moduleType, actualModuleName);
        constructorWriter.body().addSnippet(Snippet.format(Joiner.on('\n').join(
            "if (%s == null) {",
            "  throw new NullPointerException();",
            "}"), actualModuleName));
        constructorWriter.body().addSnippet(
            Snippet.format("this.%1$s = %1$s;", actualModuleName));
        MemberSelect moduleSelect = MemberSelect.instanceSelect(
            subcomponentWriter.name(), Snippet.format(actualModuleName));
        componentContributionFields.put(moduleTypeElement, moduleSelect);
        subcomponentConstructorParameters.add(
            Snippet.format("%s", moduleVariable.getSimpleName()));
      }
    }

    ImmutableSet<TypeElement> uninitializedModules =
        FluentIterable.from(subgraph.componentDescriptor().transitiveModules())
            .transform(ModuleDescriptor.getModuleElement())
            .filter(Predicates.not(Predicates.in(componentContributionFields.keySet())))
            .toSet();

    for (TypeElement moduleType : uninitializedModules) {
      String preferredModuleName = CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL,
          moduleType.getSimpleName().toString());
      FieldWriter contributionField =
          subcomponentWriter.addField(moduleType, preferredModuleName);
      contributionField.addModifiers(PRIVATE, FINAL);
      String actualModuleName = contributionField.name();
      constructorWriter.body().addSnippet(
          Snippet.format("this.%s = new %s();", actualModuleName,
              ClassName.fromTypeElement(moduleType)));
      MemberSelect moduleSelect = MemberSelect.instanceSelect(
          subcomponentWriter.name(), Snippet.format(actualModuleName));
      componentContributionFields.put(moduleType, moduleSelect);
    }

    componentMethod.body().addSnippet("return new %s(%s);",
        subcomponentWriter.name(),
        Snippet.makeParametersSnippet(subcomponentConstructorParameters.build()));
  }

  private void writeFields(BindingGraph input,
      ClassWriter componentWriter,
      Set<JavaWriter> proxyWriters,
      Map<BindingKey, MemberSelect> memberSelectSnippetsBuilder,
      Map<ContributionBinding, Snippet> multibindingContributionSnippetsBuilder,
      ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder,
      Map<String, ProxyClassAndField> packageProxies) {
    for (ResolvedBindings resolvedBindings : input.resolvedBindings().values()) {
      writeField(
          componentWriter,
          proxyWriters,
          memberSelectSnippetsBuilder,
          multibindingContributionSnippetsBuilder,
          enumBindingKeysBuilder,
          packageProxies,
          resolvedBindings);
    }
  }

  private void writeField(
      ClassWriter componentWriter,
      Set<JavaWriter> proxyWriters,
      Map<BindingKey, MemberSelect> memberSelectSnippetsBuilder,
      Map<ContributionBinding, Snippet> multibindingContributionSnippetsBuilder,
      ImmutableSet.Builder<BindingKey> enumBindingKeysBuilder,
      Map<String, ProxyClassAndField> packageProxies, ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();

    if (bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)
        && resolvedBindings.ownedContributionBindings().isEmpty()
        && !ContributionBinding.bindingTypeFor(resolvedBindings.contributionBindings())
            .isMultibinding()) {
      return;
    }

    if (resolvedBindings.bindings().size() == 1) {
      if (bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)) {
        ContributionBinding contributionBinding =
            Iterables.getOnlyElement(resolvedBindings.contributionBindings());
        if (!contributionBinding.bindingType().isMultibinding()
            && (contributionBinding instanceof ProvisionBinding)) {
          ProvisionBinding provisionBinding = (ProvisionBinding) contributionBinding;
          if (provisionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
              && !provisionBinding.scope().isPresent()) {
            enumBindingKeysBuilder.add(bindingKey);
            // skip keys whose factories are enum instances and aren't scoped
            memberSelectSnippetsBuilder.put(bindingKey,
                MemberSelect.staticSelect(
                    factoryNameForProvisionBinding(provisionBinding),
                    Snippet.format("create()")));
            return;
          }
        }
      } else if (bindingKey.kind().equals(BindingKey.Kind.MEMBERS_INJECTION)) {
        MembersInjectionBinding membersInjectionBinding =
            Iterables.getOnlyElement(resolvedBindings.membersInjectionBindings());
        if (membersInjectionBinding.injectionStrategy().equals(NO_OP)) {
          // TODO(gak): refactor to use enumBindingKeys throughout the generator
          enumBindingKeysBuilder.add(bindingKey);
          // TODO(gak): suppress the warnings in a reasonable place
          memberSelectSnippetsBuilder.put(bindingKey,
              MemberSelect.staticMethodInvocationWithCast(
                  ClassName.fromClass(MembersInjectors.class),
                  Snippet.format("noOp()"),
                  ClassName.fromClass(MembersInjector.class)));
          return;
        }
      }
    }

    String bindingPackage = bindingPackageFor(resolvedBindings.bindings())
        .or(componentWriter.name().packageName());

    final Optional<String> proxySelector;
    final TypeWriter classWithFields;
    final Set<Modifier> fieldModifiers;

    if (bindingPackage.equals(componentWriter.name().packageName())) {
      // no proxy
      proxySelector = Optional.absent();
      // component gets the fields
      classWithFields = componentWriter;
      // private fields
      fieldModifiers = EnumSet.of(PRIVATE);
    } else {
      // get or create the proxy
      ProxyClassAndField proxyClassAndField = packageProxies.get(bindingPackage);
      if (proxyClassAndField == null) {
        JavaWriter proxyJavaWriter = JavaWriter.inPackage(bindingPackage);
        proxyWriters.add(proxyJavaWriter);
        ClassWriter proxyWriter =
            proxyJavaWriter.addClass(componentWriter.name().simpleName() + "_PackageProxy");
        proxyWriter.annotate(Generated.class)
            .setValue(ComponentProcessor.class.getCanonicalName());
        proxyWriter.addModifiers(PUBLIC, FINAL);
        // create the field for the proxy in the component
        FieldWriter proxyFieldWriter =
            componentWriter.addField(proxyWriter.name(),
                bindingPackage.replace('.', '_') + "_Proxy");
        proxyFieldWriter.addModifiers(PRIVATE, FINAL);
        proxyFieldWriter.setInitializer("new %s()", proxyWriter.name());
        proxyClassAndField = ProxyClassAndField.create(proxyWriter, proxyFieldWriter);
        packageProxies.put(bindingPackage, proxyClassAndField);
      }
      // add the field for the member select
      proxySelector = Optional.of(proxyClassAndField.proxyFieldWriter().name());
      // proxy gets the fields
      classWithFields = proxyClassAndField.proxyWriter();
      // public fields in the proxy
      fieldModifiers = EnumSet.of(PUBLIC);
    }

    if (bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION)) {
      ImmutableSet<? extends ContributionBinding> contributionBindings =
          resolvedBindings.contributionBindings();
      if (ContributionBinding.bindingTypeFor(contributionBindings).isMultibinding()) {
        // note that here we rely on the order of the resolved bindings being from parent to child
        // otherwise, the numbering wouldn't work
        int contributionNumber = 0;
        for (ContributionBinding contributionBinding : contributionBindings) {
          if (!contributionBinding.isSyntheticBinding()) {
            contributionNumber++;
            if (resolvedBindings.ownedBindings().contains(contributionBinding)) {
              FrameworkField contributionBindingField =
                  frameworkFieldForSyntheticContributionBinding(
                        bindingKey, contributionNumber, contributionBinding);
              FieldWriter contributionField = classWithFields.addField(
                  contributionBindingField.frameworkType(), contributionBindingField.name());
              contributionField.addModifiers(fieldModifiers);

              ImmutableList<String> contributionSelectTokens = new ImmutableList.Builder<String>()
                  .addAll(proxySelector.asSet())
                  .add(contributionField.name())
                  .build();
              multibindingContributionSnippetsBuilder.put(contributionBinding,
                  Snippet.memberSelectSnippet(contributionSelectTokens));
            }
          }
        }
      }
    }

    FrameworkField bindingField = frameworkFieldForResolvedBindings(resolvedBindings);
    FieldWriter frameworkField =
        classWithFields.addField(bindingField.frameworkType(), bindingField.name());
    frameworkField.addModifiers(fieldModifiers);

    ImmutableList<String> memberSelectTokens = new ImmutableList.Builder<String>()
        .addAll(proxySelector.asSet())
        .add(frameworkField.name())
        .build();
    memberSelectSnippetsBuilder.put(bindingKey, MemberSelect.instanceSelect(
        componentWriter.name(),
        Snippet.memberSelectSnippet(memberSelectTokens)));
  }

  private void writeInterfaceMethods(BindingGraph input, ClassWriter componentWriter,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets,
      ImmutableSet<BindingKey> enumBindingKeys) throws AssertionError {
    Set<MethodSignature> interfaceMethods = Sets.newHashSet();

    for (ComponentMethodDescriptor componentMethod :
        input.componentDescriptor().componentMethods()) {
      if (componentMethod.dependencyRequest().isPresent()) {
        DependencyRequest interfaceRequest = componentMethod.dependencyRequest().get();
        ExecutableElement requestElement =
            MoreElements.asExecutable(interfaceRequest.requestElement());
        ExecutableType requestType = MoreTypes.asExecutable(types.asMemberOf(
            MoreTypes.asDeclared(input.componentDescriptor().componentDefinitionType().asType()),
            requestElement));
        MethodSignature signature = MethodSignature.fromExecutableType(
            requestElement.getSimpleName().toString(),
            requestType);
        if (!interfaceMethods.contains(signature)) {
          interfaceMethods.add(signature);
          MethodWriter interfaceMethod = requestType.getReturnType().getKind().equals(VOID)
              ? componentWriter.addMethod(VoidName.VOID, requestElement.getSimpleName().toString())
                  : componentWriter.addMethod(requestType.getReturnType(),
                      requestElement.getSimpleName().toString());
          interfaceMethod.annotate(Override.class);
          interfaceMethod.addModifiers(PUBLIC);
          BindingKey bindingKey = interfaceRequest.bindingKey();
          switch(interfaceRequest.kind()) {
            case MEMBERS_INJECTOR:
              MemberSelect membersInjectorSelect = memberSelectSnippets.get(bindingKey);
              List<? extends VariableElement> parameters = requestElement.getParameters();
              if (parameters.isEmpty()) {
                // we're returning the framework type
                interfaceMethod.body().addSnippet("return %s;",
                    membersInjectorSelect.getSnippetFor(componentWriter.name()));
              } else {
                VariableElement parameter = Iterables.getOnlyElement(parameters);
                Name parameterName = parameter.getSimpleName();
                interfaceMethod.addParameter(
                    TypeNames.forTypeMirror(
                        Iterables.getOnlyElement(requestType.getParameterTypes())),
                    parameterName.toString());
                interfaceMethod.body().addSnippet("%s.injectMembers(%s);",
                    // in this case we know we won't need the cast because we're never going to pass
                    // the reference to anything
                    membersInjectorSelect.getSnippetFor(componentWriter.name()),
                    parameterName);
                if (!requestType.getReturnType().getKind().equals(VOID)) {
                  interfaceMethod.body().addSnippet("return %s;", parameterName);
                }
              }
              break;
            case INSTANCE:
              if (enumBindingKeys.contains(bindingKey)
                  && (bindingKey.key().type().getKind().equals(DECLARED)
                      && !((DeclaredType) bindingKey.key().type()).getTypeArguments().isEmpty())) {
                // If using a parameterized enum type, then we need to store the factory
                // in a temporary variable, in order to help javac be able to infer
                // the generics of the Factory.create methods.
                TypeName factoryType = ParameterizedTypeName.create(Provider.class,
                    TypeNames.forTypeMirror(requestType.getReturnType()));
                interfaceMethod.body().addSnippet("%s factory = %s;", factoryType,
                    memberSelectSnippets.get(bindingKey).getSnippetFor(componentWriter.name()));
                interfaceMethod.body().addSnippet("return factory.get();");
                break;
              }
              // fall through in the else case.
            case LAZY:
            case PRODUCED:
            case PRODUCER:
            case PROVIDER:
            case FUTURE:
              interfaceMethod.body().addSnippet("return %s;",
                  frameworkTypeUsageStatement(
                      memberSelectSnippets.get(bindingKey).getSnippetFor(componentWriter.name()),
                      interfaceRequest.kind()));
              break;
            default:
              throw new AssertionError();
          }
        }
      }
    }
  }

  private void initializeFrameworkTypes(BindingGraph input,
      ClassWriter componentWriter,
      ConstructorWriter constructorWriter,
      Optional<ClassName> builderName,
      Map<TypeElement, MemberSelect> componentContributionFields,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets,
      ImmutableMap<ContributionBinding, Snippet> parentMultibindingContributionSnippets,
      ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets)
      throws AssertionError {
    List<List<BindingKey>> partitions = Lists.partition(
        input.resolvedBindings().keySet().asList(), 100);
    for (int i = 0; i < partitions.size(); i++) {
      MethodWriter initializeMethod =
          componentWriter.addMethod(VoidName.VOID, "initialize" + ((i == 0) ? "" : i));
      initializeMethod.body();
      initializeMethod.addModifiers(PRIVATE);
      if (builderName.isPresent()) {
        initializeMethod.addParameter(builderName.get(), "builder").addModifiers(FINAL);
        constructorWriter.body().addSnippet("%s(builder);", initializeMethod.name());
      } else {
        constructorWriter.body().addSnippet("%s();", initializeMethod.name());
      }

      for (BindingKey bindingKey : partitions.get(i)) {
        Snippet memberSelectSnippet =
            memberSelectSnippets.get(bindingKey).getSnippetFor(componentWriter.name());
        ResolvedBindings resolvedBindings = input.resolvedBindings().get(bindingKey);
        switch (bindingKey.kind()) {
          case CONTRIBUTION:
            ImmutableSet<? extends ContributionBinding> bindings =
                resolvedBindings.contributionBindings();

            switch (ContributionBinding.bindingTypeFor(bindings)) {
              case SET:
                boolean hasOnlyProvisions =
                    Iterables.all(bindings, Predicates.instanceOf(ProvisionBinding.class));
                ImmutableList.Builder<Snippet> parameterSnippets = ImmutableList.builder();
                for (ContributionBinding binding : bindings) {
                  if (multibindingContributionSnippets.containsKey(binding)) {
                    Snippet initializeSnippet = initializeFactoryForContributionBinding(
                        binding,
                        input,
                        componentWriter.name(),
                        componentContributionFields,
                        memberSelectSnippets);
                    Snippet snippet = multibindingContributionSnippets.get(binding);
                    initializeMethod.body().addSnippet("this.%s = %s;", snippet, initializeSnippet);
                    parameterSnippets.add(snippet);
                  } else if (parentMultibindingContributionSnippets.containsKey(binding)) {
                    parameterSnippets.add(parentMultibindingContributionSnippets.get(binding));
                  } else {
                    throw new IllegalStateException(binding + " was not found in");
                  }
                }
                Snippet initializeSetSnippet = Snippet.format("%s.create(%s)",
                    hasOnlyProvisions
                        ? ClassName.fromClass(SetFactory.class)
                        : ClassName.fromClass(SetProducer.class),
                    Snippet.makeParametersSnippet(parameterSnippets.build()));
                initializeMethod.body().addSnippet("this.%s = %s;",
                    memberSelectSnippet, initializeSetSnippet);
                break;
              case MAP:
                if (Sets.filter(bindings, Predicates.instanceOf(ProductionBinding.class))
                    .isEmpty()) {
                  @SuppressWarnings("unchecked")  // checked by the instanceof filter above
                  ImmutableSet<ProvisionBinding> provisionBindings =
                      (ImmutableSet<ProvisionBinding>) bindings;
                  for (ProvisionBinding provisionBinding : provisionBindings) {
                    if (!isNonProviderMap(provisionBinding.key().type())
                        && multibindingContributionSnippets.containsKey(provisionBinding)) {
                      Snippet snippet = multibindingContributionSnippets.get(provisionBinding);
                      initializeMethod.body().addSnippet("this.%s = %s;",
                          snippet,
                          initializeFactoryForProvisionBinding(provisionBinding,
                              componentWriter.name(),
                              input.componentDescriptor().dependencyMethodIndex(),
                              componentContributionFields,
                              memberSelectSnippets));
                    }
                  }
                  if (!provisionBindings.isEmpty()) {
                    Snippet initializeMapSnippet = initializeMapBinding(
                        componentWriter.name(), memberSelectSnippets,
                        new ImmutableMap.Builder<ContributionBinding, Snippet>()
                            .putAll(parentMultibindingContributionSnippets)
                            .putAll(multibindingContributionSnippets)
                            .build(),
                        provisionBindings);
                    initializeMethod.body().addSnippet("this.%s = %s;",
                        memberSelectSnippet, initializeMapSnippet);
                  }
                } else {
                  // TODO(user): Implement producer map bindings.
                  throw new IllegalStateException("producer map bindings not implemented yet");
                }
                break;
              case UNIQUE:
                if (!resolvedBindings.ownedContributionBindings().isEmpty()) {
                  ContributionBinding binding = Iterables.getOnlyElement(bindings);
                  if (binding instanceof ProvisionBinding) {
                    ProvisionBinding provisionBinding = (ProvisionBinding) binding;
                    if (!provisionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
                        || provisionBinding.scope().isPresent()) {
                      initializeMethod.body().addSnippet("this.%s = %s;",
                          memberSelectSnippet,
                          initializeFactoryForProvisionBinding(provisionBinding,
                              componentWriter.name(),
                              input.componentDescriptor().dependencyMethodIndex(),
                              componentContributionFields, memberSelectSnippets));
                    }
                  } else if (binding instanceof ProductionBinding) {
                    ProductionBinding productionBinding = (ProductionBinding) binding;
                    initializeMethod.body().addSnippet("this.%s = %s;",
                        memberSelectSnippet,
                        initializeFactoryForProductionBinding(productionBinding,
                            input,
                            componentWriter.name(),
                            input.componentDescriptor().dependencyMethodIndex(),
                            componentContributionFields, memberSelectSnippets));
                  } else {
                    throw new AssertionError();
                  }
                }
                break;
              default:
                throw new IllegalStateException();
            }
            break;
          case MEMBERS_INJECTION:
            MembersInjectionBinding binding = Iterables.getOnlyElement(
                resolvedBindings.membersInjectionBindings());
            if (!binding.injectionStrategy().equals(MembersInjectionBinding.Strategy.NO_OP)) {
              initializeMethod.body().addSnippet("this.%s = %s;",
                  memberSelectSnippet,
                  initializeMembersInjectorForBinding(
                      componentWriter.name(), binding, memberSelectSnippets));
            }
            break;
          default:
            throw new AssertionError();
        }
      }
    }
  }

  private static FrameworkField frameworkFieldForSyntheticContributionBinding(BindingKey bindingKey,
      int contributionNumber, ContributionBinding contributionBinding) throws AssertionError {
    switch (contributionBinding.bindingType()) {
      case MAP:
        return FrameworkField.createForMapBindingContribution(
            contributionBinding.frameworkClass(),
            BindingKey.create(bindingKey.kind(), contributionBinding.key()),
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
      case SET:
        return FrameworkField.createWithTypeFromKey(
            contributionBinding.frameworkClass(),
            bindingKey,
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
      case UNIQUE:
        return FrameworkField.createWithTypeFromKey(
            contributionBinding.frameworkClass(),
            bindingKey,
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
      default:
        throw new AssertionError();
    }
  }

  private static Class<?> frameworkClassForResolvedBindings(ResolvedBindings resolvedBindings) {
    switch (resolvedBindings.bindingKey().kind()) {
      case CONTRIBUTION:
        for (ContributionBinding binding : resolvedBindings.contributionBindings()) {
          if (binding instanceof ProductionBinding) {
            return Producer.class;
          }
        }
        return Provider.class;
      case MEMBERS_INJECTION:
        return MembersInjector.class;
      default:
        throw new AssertionError();
    }
  }

  private FrameworkField frameworkFieldForResolvedBindings(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();
    switch (bindingKey.kind()) {
      case CONTRIBUTION:
        ImmutableSet<? extends ContributionBinding> contributionBindings =
            resolvedBindings.contributionBindings();
        BindingType bindingsType = ProvisionBinding.bindingTypeFor(contributionBindings);
        switch (bindingsType) {
          case SET:
          case MAP:
            return FrameworkField.createWithTypeFromKey(
                frameworkClassForResolvedBindings(resolvedBindings),
                bindingKey,
                KeyVariableNamer.INSTANCE.apply(bindingKey.key()));
          case UNIQUE:
            ContributionBinding binding = Iterables.getOnlyElement(contributionBindings);
            return FrameworkField.createWithTypeFromKey(
                frameworkClassForResolvedBindings(resolvedBindings),
                bindingKey,
                binding.bindingElement().accept(new ElementKindVisitor6<String, Void>() {
                  @Override
                  public String visitExecutableAsConstructor(ExecutableElement e, Void p) {
                    return e.getEnclosingElement().accept(this, null);
                  }

                  @Override
                  public String visitExecutableAsMethod(ExecutableElement e, Void p) {
                    return e.getSimpleName().toString();
                  }

                  @Override
                  public String visitType(TypeElement e, Void p) {
                    return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,
                        e.getSimpleName().toString());
                  }
                }, null));
          default:
            throw new AssertionError();
        }
      case MEMBERS_INJECTION:
        return FrameworkField.createWithTypeFromKey(
            MembersInjector.class,
            bindingKey,
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL,
                Iterables.getOnlyElement(resolvedBindings.bindings())
                .bindingElement().getSimpleName().toString()));
      default:
        throw new AssertionError();
    }
  }

  private Snippet initializeFactoryForContributionBinding(ContributionBinding binding,
      BindingGraph input,
      ClassName componentName,
      Map<TypeElement, MemberSelect> componentContributionFields,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets) {
    if (binding instanceof ProvisionBinding) {
      return initializeFactoryForProvisionBinding(
          (ProvisionBinding) binding,
          componentName,
          input.componentDescriptor().dependencyMethodIndex(),
          componentContributionFields,
          memberSelectSnippets);
    } else if (binding instanceof ProductionBinding) {
      return initializeFactoryForProductionBinding(
          (ProductionBinding) binding,
          input,
          componentName,
          input.componentDescriptor().dependencyMethodIndex(),
          componentContributionFields,
          memberSelectSnippets);
    } else {
      throw new AssertionError();
    }
}

  private Snippet initializeFactoryForProvisionBinding(ProvisionBinding binding,
      ClassName componentName,
      ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex,
      Map<TypeElement, MemberSelect> contributionFields,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets) {
    switch(binding.bindingKind()) {
      case COMPONENT:
        MemberSelect componentContributionSelect =
            contributionFields.get(MoreTypes.asTypeElement(binding.key().type()));
        return Snippet.format("%s.<%s>create(%s)",
            ClassName.fromClass(InstanceFactory.class),
            TypeNames.forTypeMirror(binding.key().type()),
            componentContributionSelect != null
                ? componentContributionSelect.getSnippetFor(componentName) : "this");
      case COMPONENT_PROVISION:
        TypeElement bindingTypeElement = dependencyMethodIndex.get(binding.bindingElement());
        String sourceFieldName =
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, bindingTypeElement.getSimpleName().toString());
        if (binding.nullableType().isPresent()
            || nullableValidationType.equals(Diagnostic.Kind.WARNING)) {
          Snippet nullableSnippet = binding.nullableType().isPresent()
              ? Snippet.format("@%s ", TypeNames.forTypeMirror(binding.nullableType().get()))
              : Snippet.format("");
          return Snippet.format(Joiner.on('\n').join(
            "new %s<%2$s>() {",
            "  private final %6$s %7$s = %3$s;",
            "  %5$s@Override public %2$s get() {",
            "    return %7$s.%4$s();",
            "  }",
            "}"),
            ClassName.fromClass(Factory.class),
            TypeNames.forTypeMirror(binding.key().type()),
            contributionFields.get(bindingTypeElement).getSnippetFor(componentName),
            binding.bindingElement().getSimpleName().toString(),
            nullableSnippet,
            TypeNames.forTypeMirror(bindingTypeElement.asType()),
            sourceFieldName);
        } else {
          // TODO(sameb): This throws a very vague NPE right now.  The stack trace doesn't
          // help to figure out what the method or return type is.  If we include a string
          // of the return type or method name in the error message, that can defeat obfuscation.
          // We can easily include the raw type (no generics) + annotation type (no values),
          // using .class & String.format -- but that wouldn't be the whole story.
          // What should we do?
          StringLiteral failMsg =
              StringLiteral.forValue(CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);
          return Snippet.format(Joiner.on('\n').join(
            "new %s<%2$s>() {",
            "  private final %6$s %7$s = %3$s;",
            "  @Override public %2$s get() {",
            "    %2$s provided = %7$s.%4$s();",
            "    if (provided == null) {",
            "      throw new NullPointerException(%5$s);",
            "    }",
            "    return provided;",
            "  }",
            "}"),
            ClassName.fromClass(Factory.class),
            TypeNames.forTypeMirror(binding.key().type()),
            contributionFields.get(bindingTypeElement).getSnippetFor(componentName),
            binding.bindingElement().getSimpleName().toString(),
            failMsg,
            TypeNames.forTypeMirror(bindingTypeElement.asType()),
            sourceFieldName);
        }
      case INJECTION:
      case PROVISION:
        List<Snippet> parameters =
            Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
        if (binding.bindingKind().equals(PROVISION)
            && !binding.bindingElement().getModifiers().contains(STATIC)) {
          parameters.add(contributionFields.get(binding.contributedBy().get())
              .getSnippetFor(componentName));
        }
        parameters.addAll(getDependencyParameters(componentName, binding.implicitDependencies(),
            memberSelectSnippets));

        Snippet factorySnippet = Snippet.format("%s.create(%s)",
            factoryNameForProvisionBinding(binding),
            Snippet.makeParametersSnippet(parameters));
        return binding.scope().isPresent()
            ? Snippet.format("%s.create(%s)",
                ClassName.fromClass(ScopedProvider.class),
                factorySnippet)
            : factorySnippet;
      default:
        throw new AssertionError();
    }
  }

  private Snippet initializeFactoryForProductionBinding(ProductionBinding binding,
      BindingGraph bindingGraph,
      ClassName componentName,
      ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex,
      Map<TypeElement, MemberSelect> contributionFields,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets) {
    switch (binding.bindingKind()) {
      case COMPONENT_PRODUCTION:
        TypeElement bindingTypeElement = dependencyMethodIndex.get(binding.bindingElement());
        String sourceFieldName =
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, bindingTypeElement.getSimpleName().toString());
        return Snippet.format(Joiner.on('\n').join(
            "new %s<%2$s>() {",
            "  private final %6$s %7$s = %4$s;",
            "  @Override public %3$s<%2$s> get() {",
            "    return %7$s.%5$s();",
            "  }",
            "}"),
            ClassName.fromClass(Producer.class),
            TypeNames.forTypeMirror(binding.key().type()),
            ClassName.fromClass(ListenableFuture.class),
            contributionFields.get(bindingTypeElement).getSnippetFor(componentName),
            binding.bindingElement().getSimpleName().toString(),
            TypeNames.forTypeMirror(bindingTypeElement.asType()),
            sourceFieldName);
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        List<Snippet> parameters =
            Lists.newArrayListWithCapacity(binding.dependencies().size() + 2);
        parameters.add(contributionFields.get(binding.bindingTypeElement())
            .getSnippetFor(componentName));
        parameters.add(contributionFields.get(
            bindingGraph.componentDescriptor().executorDependency().get())
                .getSnippetFor(componentName));
        parameters.addAll(getProducerDependencyParameters(
            bindingGraph, componentName, binding.dependencies(), memberSelectSnippets));

        return Snippet.format("new %s(%s)",
            factoryNameForProductionBinding(binding),
            Snippet.makeParametersSnippet(parameters));
      default:
        throw new AssertionError();
    }
  }

  private Snippet initializeMembersInjectorForBinding(
      ClassName componentName,
      MembersInjectionBinding binding,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets) {
    switch (binding.injectionStrategy()) {
      case NO_OP:
        return Snippet.format("%s.noOp()",
            ClassName.fromClass(MembersInjectors.class));
      case DELEGATE:
        DependencyRequest parentInjectorRequest = binding.parentInjectorRequest().get();
        return Snippet.format("%s.delegatingTo(%s)",
            ClassName.fromClass(MembersInjectors.class),
            memberSelectSnippets.get(parentInjectorRequest.bindingKey())
                .getSnippetFor(componentName));
      case INJECT_MEMBERS:
        List<Snippet> parameters = getDependencyParameters(
            componentName,
            binding.implicitDependencies(),
            memberSelectSnippets);
        return Snippet.format("%s.create(%s)",
            membersInjectorNameForMembersInjectionBinding(binding),
            Snippet.makeParametersSnippet(parameters));
      default:
        throw new AssertionError();
    }
  }

  private List<Snippet> getDependencyParameters(
      ClassName componentName,
      Iterable<DependencyRequest> dependencies,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (Collection<DependencyRequest> requestsForKey :
         SourceFiles.indexDependenciesByUnresolvedKey(types, dependencies).asMap().values()) {
      BindingKey key = Iterables.getOnlyElement(FluentIterable.from(requestsForKey)
          .transform(new Function<DependencyRequest, BindingKey>() {
            @Override public BindingKey apply(DependencyRequest request) {
              return request.bindingKey();
            }
          })
          .toSet());
      parameters.add(memberSelectSnippets.get(key).getSnippetWithRawTypeCastFor(componentName));
    }
    return parameters.build();
  }

  private List<Snippet> getProducerDependencyParameters(
      BindingGraph bindingGraph,
      ClassName componentName,
      Iterable<DependencyRequest> dependencies,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets) {
    ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
    for (Collection<DependencyRequest> requestsForKey :
         SourceFiles.indexDependenciesByUnresolvedKey(types, dependencies).asMap().values()) {
      BindingKey key = Iterables.getOnlyElement(FluentIterable.from(requestsForKey)
          .transform(new Function<DependencyRequest, BindingKey>() {
            @Override public BindingKey apply(DependencyRequest request) {
              return request.bindingKey();
            }
          }));
      ResolvedBindings resolvedBindings = bindingGraph.resolvedBindings().get(key);
      Class<?> frameworkClass =
          DependencyRequestMapper.FOR_PRODUCER.getFrameworkClass(requestsForKey);
      if (frameworkClassForResolvedBindings(resolvedBindings).equals(Provider.class)
          && frameworkClass.equals(Producer.class)) {
        parameters.add(Snippet.format(
            "%s.producerFromProvider(%s)",
            ClassName.fromClass(Producers.class),
            memberSelectSnippets.get(key).getSnippetFor(componentName)));
      } else {
        parameters.add(memberSelectSnippets.get(key).getSnippetFor(componentName));
      }
    }
    return parameters.build();
  }

  private Snippet initializeMapBinding(
      ClassName componentName,
      ImmutableMap<BindingKey, MemberSelect> memberSelectSnippets,
      ImmutableMap<ContributionBinding, Snippet> multibindingContributionSnippets,
      Set<ProvisionBinding> bindings) {
    // Get type information from the first binding.
    ProvisionBinding firstBinding = bindings.iterator().next();
    DeclaredType mapType = asDeclared(firstBinding.key().type());

    if (isNonProviderMap(mapType)) {
      return Snippet.format(
          "%s.create(%s)",
          ClassName.fromClass(MapFactory.class),
          memberSelectSnippets
              .get(getOnlyElement(firstBinding.dependencies()).bindingKey())
              .getSnippetFor(componentName));
    }

    ImmutableList.Builder<dagger.internal.codegen.writer.Snippet> snippets =
        ImmutableList.builder();
    snippets.add(
        Snippet.format(
            "%s.<%s, %s>builder(%d)",
            ClassName.fromClass(MapProviderFactory.class),
            TypeNames.forTypeMirror(getKeyTypeOfMap(mapType)),
            TypeNames.forTypeMirror(getProvidedValueTypeOfMap(mapType)), // V of Map<K, Provider<V>>
            bindings.size()));

    for (ProvisionBinding binding : bindings) {
      snippets.add(
          Snippet.format(
              "    .put(%s, %s)",
              getMapKeySnippet(binding.bindingElement()),
              multibindingContributionSnippets.get(binding)));
    }

    snippets.add(Snippet.format("    .build()"));

    return Snippet.join(Joiner.on('\n'), snippets.build());
  }

  private boolean isNonProviderMap(TypeMirror type) {
    return MoreTypes.isTypeOf(Map.class, type) // Implicitly guarantees a declared type.
        && !MoreTypes.isTypeOf(Provider.class, asDeclared(type).getTypeArguments().get(1));
  }
}

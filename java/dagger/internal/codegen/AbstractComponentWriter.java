/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.AnnotationSpecs.Suppression.UNCHECKED;
import static dagger.internal.codegen.MemberSelect.localField;
import static dagger.internal.codegen.Scope.reusableScope;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK;
import static dagger.internal.codegen.TypeNames.REFERENCE_RELEASING_PROVIDER;
import static dagger.internal.codegen.TypeNames.REFERENCE_RELEASING_PROVIDER_MANAGER;
import static dagger.internal.codegen.TypeNames.SINGLE_CHECK;
import static dagger.internal.codegen.Util.toImmutableList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.type.TypeKind.VOID;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.InjectionMethods.InjectionSiteMethod;
import dagger.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger.producers.Producer;
import dagger.releasablereferences.CanReleaseReferences;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Creates the implementation class for a component or subcomponent. */
abstract class AbstractComponentWriter implements GeneratedComponentModel {
  // TODO(dpb): Make all these fields private after refactoring is complete.
  protected final Elements elements;
  protected final Types types;
  protected final Key.Factory keyFactory;
  protected final CompilerOptions compilerOptions;
  protected final ClassName name;
  protected final BindingGraph graph;
  protected final ImmutableMap<ComponentDescriptor, String> subcomponentNames;
  protected final TypeSpec.Builder component;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final UniqueNameSet componentMethodNames = new UniqueNameSet();
  private final ComponentBindingExpressions bindingExpressions;
  protected final ComponentRequirementFields componentRequirementFields;
  // TODO(user): Merge into ComponentBindingExpressions after we refactor BindingKey.
  private final Map<BindingKey, FrameworkInstanceBindingExpression>
      producerFromProviderBindingExpressions = new LinkedHashMap<>();
  private final List<CodeBlock> initializations = new ArrayList<>();
  protected final List<MethodSpec> interfaceMethods = new ArrayList<>();
  private final BindingExpression.Factory bindingExpressionFactory;
  private final ComponentRequirementField.Factory componentRequirementFieldFactory;

  private final Map<Key, MethodSpec> membersInjectionMethods = new LinkedHashMap<>();
  protected final MethodSpec.Builder constructor = constructorBuilder().addModifiers(PRIVATE);
  private final OptionalFactories optionalFactories;
  private ComponentBuilder builder;
  private boolean done;

  /**
   * For each component requirement, the builder field. This map is empty for subcomponents that do
   * not use a builder.
   */
  private final ImmutableMap<ComponentRequirement, FieldSpec> builderFields;

  /**
   * The member-selects for {@link dagger.internal.ReferenceReleasingProviderManager} fields,
   * indexed by their {@link CanReleaseReferences @CanReleaseReferences} scope.
   */
  private ImmutableMap<Scope, MemberSelect> referenceReleasingProviderManagerFields;

  AbstractComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph,
      ImmutableMap<ComponentDescriptor, String> subcomponentNames,
      OptionalFactories optionalFactories,
      ComponentBindingExpressions bindingExpressions,
      ComponentRequirementFields componentRequirementFields) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.component = classBuilder(name);
    this.name = name;
    this.graph = graph;
    this.subcomponentNames = subcomponentNames;
    this.optionalFactories = optionalFactories;
    this.bindingExpressions = bindingExpressions;
    // TODO(dpb): Allow ComponentBuilder.create to return a no-op object
    if (hasBuilder(graph)) {
      builder = ComponentBuilder.create(name, graph, subcomponentNames, elements, types);
      builderFields = builder.builderFields();
    } else {
      builderFields = ImmutableMap.of();
    }
    this.componentRequirementFields = componentRequirementFields;
    this.bindingExpressionFactory =
        new BindingExpression.Factory(
            compilerOptions,
            name,
            componentFieldNames,
            bindingExpressions,
            componentRequirementFields,
            this,
            childComponentNames(keyFactory, subcomponentNames),
            graph,
            elements,
            optionalFactories);
    this.componentRequirementFieldFactory =
        new ComponentRequirementField.Factory(this, componentFieldNames, name, builderFields);
  }

  private static ImmutableMap<BindingKey, String> childComponentNames(
      Key.Factory keyFactory, ImmutableMap<ComponentDescriptor, String> subcomponentNames) {
    ImmutableMap.Builder<BindingKey, String> builder = ImmutableMap.builder();
    subcomponentNames.forEach(
        (component, name) -> {
          if (component.builderSpec().isPresent()) {
            TypeMirror builderType = component.builderSpec().get().builderDefinitionType().asType();
            builder.put(
                BindingKey.contribution(keyFactory.forSubcomponentBuilder(builderType)), name);
          }
        });
    return builder.build();
  }

  protected AbstractComponentWriter(
      AbstractComponentWriter parent, ClassName name, BindingGraph graph) {
    this(
        parent.types,
        parent.elements,
        parent.keyFactory,
        parent.compilerOptions,
        name,
        graph,
        parent.subcomponentNames,
        parent.optionalFactories,
        parent.bindingExpressions.forChildComponent(),
        parent.componentRequirementFields.forChildComponent());
  }

  protected final ClassName componentDefinitionTypeName() {
    return ClassName.get(graph.componentType());
  }

  /**
   * Creates a {@link FieldSpec.Builder} with a unique name based off of {@code name}.
   */
  protected final FieldSpec.Builder componentField(TypeName type, String name) {
    return FieldSpec.builder(type, componentFieldNames.getUniqueName(name));
  }

  /** Adds the given code block to the initialize methods of the component. */
  @Override
  public void addInitialization(CodeBlock codeBlock) {
    initializations.add(codeBlock);
  }

  /** Adds the given field to the component. */
  @Override
  public void addField(FieldSpec fieldSpec) {
    component.addField(fieldSpec);
  }

  @Override
  public void addType(TypeSpec typeSpec) {
    component.addType(typeSpec);
  }

  @Override
  public String getSubcomponentName(ComponentDescriptor subcomponentDescriptor) {
    return checkNotNull(subcomponentNames.get(subcomponentDescriptor));
  }

  @Override
  public CodeBlock getReferenceReleasingProviderManagerExpression(Scope scope) {
    return referenceReleasingProviderManagerFields.get(scope).getExpressionFor(name);
  }

  /**
   * Constructs a {@link TypeSpec.Builder} that models the {@link BindingGraph} for this component.
   * This is only intended to be called once (and will throw on successive invocations). If the
   * component must be regenerated, use a new instance.
   */
  final TypeSpec.Builder write() {
    checkState(!done, "ComponentWriter has already been generated.");
    decorateComponent();
    if (hasBuilder(graph)) {
      addBuilder();
    }

    getLocalAndInheritedMethods(
            graph.componentDescriptor().componentDefinitionType(), types, elements)
        .forEach(method -> componentMethodNames.claim(method.getSimpleName()));

    addFactoryMethods();
    addReferenceReleasingProviderManagerFields();
    createBindingExpressions();
    createComponentRequirementFields();
    implementInterfaceMethods();
    addSubcomponents();
    writeInitializeAndInterfaceMethods();
    writeMembersInjectionMethods();
    component.addMethod(constructor.build());
    if (graph.componentDescriptor().kind().isTopLevel()) {
      optionalFactories.addMembers(component);
    }
    done = true;
    return component;
  }

  /**
   * Adds Javadoc, modifiers, supertypes, and annotations to the component implementation class
   * declaration.
   */
  protected abstract void decorateComponent();

  private static boolean hasBuilder(BindingGraph graph) {
    ComponentDescriptor component = graph.componentDescriptor();
    return component.kind().isTopLevel() || component.builderSpec().isPresent();
  }

  /**
   * Adds a builder type.
   */
  private void addBuilder() {
    addBuilderClass(builder.typeSpec());

    constructor.addParameter(builderName(), "builder");
  }

  /**
   * Adds {@code builder} as a nested builder class. Root components and subcomponents will nest
   * this in different classes.
   */
  protected abstract void addBuilderClass(TypeSpec builder);

  protected final ClassName builderName() {
    return builder.name();
  }

  /**
   * Adds component factory methods.
   */
  protected abstract void addFactoryMethods();

  /**
   * Adds a {@link dagger.internal.ReferenceReleasingProviderManager} field for every scope for
   * which {@linkplain BindingGraph#scopesRequiringReleasableReferenceManagers() one is required}.
   */
  private void addReferenceReleasingProviderManagerFields() {
    ImmutableMap.Builder<Scope, MemberSelect> fields = ImmutableMap.builder();
    for (Scope scope : graph.scopesRequiringReleasableReferenceManagers()) {
      FieldSpec field = referenceReleasingProxyManagerField(scope);
      component.addField(field);
      fields.put(scope, localField(name, field.name));
    }
    referenceReleasingProviderManagerFields = fields.build();
  }

  /**
   * Returns {@code true} if {@code scope} is in {@link
   * BindingGraph#scopesRequiringReleasableReferenceManagers()} for the root graph.
   */
  protected abstract boolean requiresReleasableReferences(Scope scope);

  private FieldSpec referenceReleasingProxyManagerField(Scope scope) {
    return componentField(
            REFERENCE_RELEASING_PROVIDER_MANAGER,
            UPPER_CAMEL.to(
                LOWER_CAMEL, scope.scopeAnnotationElement().getSimpleName() + "References"))
        .addModifiers(PRIVATE, FINAL)
        .initializer(
            "new $T($T.class)",
            REFERENCE_RELEASING_PROVIDER_MANAGER,
            scope.scopeAnnotationElement())
        .addJavadoc(
            "The manager that releases references for the {@link $T} scope.\n",
            scope.scopeAnnotationElement())
        .build();
  }

  private void createBindingExpressions() {
    graph.resolvedBindings().values().forEach(this::createBindingExpression);
  }

  private void createBindingExpression(ResolvedBindings resolvedBindings) {
    // If the binding can be satisfied with a static method call without dependencies or state,
    // no field is necessary.
    // TODO(ronshapiro): can these be merged into bindingExpressionFactory.forResolvedBindings()?
    Optional<BindingExpression> staticBindingExpression =
        bindingExpressionFactory.forStaticMethod(resolvedBindings);
    if (staticBindingExpression.isPresent()) {
      bindingExpressions.addBindingExpression(staticBindingExpression.get());
      return;
    }

    // No field needed if there are no owned bindings.
    if (resolvedBindings.ownedBindings().isEmpty()) {
      return;
    }

    // TODO(gak): get rid of the field for unscoped delegated bindings
    bindingExpressions.addBindingExpression(bindingExpressionFactory.forField(resolvedBindings));
  }

  private void createComponentRequirementFields() {
    builderFields
        .keySet()
        .stream()
        .map(componentRequirementFieldFactory::forBuilderField)
        .forEach(componentRequirementFields::add);
  }

  private void implementInterfaceMethods() {
    Set<MethodSignature> interfaceMethodSignatures = Sets.newHashSet();
    for (ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      if (componentMethod.dependencyRequest().isPresent()) {
        DependencyRequest interfaceRequest = componentMethod.dependencyRequest().get();
        ExecutableElement methodElement =
            MoreElements.asExecutable(componentMethod.methodElement());
        ExecutableType requestType =
            MoreTypes.asExecutable(
                types.asMemberOf(
                    MoreTypes.asDeclared(graph.componentType().asType()), methodElement));
        MethodSignature signature =
            MethodSignature.fromExecutableType(
                methodElement.getSimpleName().toString(), requestType);
        if (!interfaceMethodSignatures.contains(signature)) {
          interfaceMethodSignatures.add(signature);
          MethodSpec.Builder interfaceMethod =
              methodSpecForComponentMethod(methodElement, requestType);
          List<? extends VariableElement> parameters = methodElement.getParameters();
          if (interfaceRequest.kind().equals(DependencyRequest.Kind.MEMBERS_INJECTOR)
              && !parameters.isEmpty() /* i.e. it's not a request for a MembersInjector<T> */) {
            ParameterSpec parameter = ParameterSpec.get(getOnlyElement(parameters));
            MembersInjectionBinding binding =
                graph
                    .resolvedBindings()
                    .get(interfaceRequest.bindingKey())
                    .membersInjectionBinding()
                    .get();
            if (requestType.getReturnType().getKind().equals(VOID)) {
              if (!binding.injectionSites().isEmpty()) {
                interfaceMethod.addStatement(
                    "$N($N)", getMembersInjectionMethod(binding.key()), parameter);
              }
            } else if (binding.injectionSites().isEmpty()) {
              interfaceMethod.addStatement("return $N", parameter);
            } else {
              interfaceMethod.addStatement(
                  "return $N($N)", getMembersInjectionMethod(binding.key()), parameter);
            }
          } else {
            interfaceMethod.addStatement(
                "return $L", bindingExpressions.getDependencyExpression(interfaceRequest, name));
          }
          interfaceMethods.add(interfaceMethod.build());
        }
      }
    }
  }

  private MethodSpec.Builder methodSpecForComponentMethod(
      ExecutableElement method, ExecutableType methodType) {
    String methodName = method.getSimpleName().toString();
    MethodSpec.Builder methodBuilder = methodBuilder(methodName);

    methodBuilder.addAnnotation(Override.class);

    Set<Modifier> modifiers = EnumSet.copyOf(method.getModifiers());
    modifiers.remove(Modifier.ABSTRACT);
    methodBuilder.addModifiers(modifiers);

    methodBuilder.returns(TypeName.get(methodType.getReturnType()));

    List<? extends VariableElement> parameters = method.getParameters();
    List<? extends TypeMirror> resolvedParameterTypes = methodType.getParameterTypes();
    verify(parameters.size() == resolvedParameterTypes.size());
    for (int i = 0; i < parameters.size(); i++) {
      VariableElement parameter = parameters.get(i);
      TypeName type = TypeName.get(resolvedParameterTypes.get(i));
      String name = parameter.getSimpleName().toString();
      Set<Modifier> parameterModifiers = parameter.getModifiers();
      ParameterSpec.Builder parameterBuilder =
          ParameterSpec.builder(type, name)
              .addModifiers(parameterModifiers.toArray(new Modifier[0]));
      methodBuilder.addParameter(parameterBuilder.build());
    }
    for (TypeMirror thrownType : method.getThrownTypes()) {
      methodBuilder.addException(TypeName.get(thrownType));
    }
    return methodBuilder;
  }

  private void addSubcomponents() {
    for (BindingGraph subgraph : graph.subgraphs()) {
      ComponentMethodDescriptor componentMethodDescriptor =
          graph.componentDescriptor()
              .subcomponentsByFactoryMethod()
              .inverse()
              .get(subgraph.componentDescriptor());
      SubcomponentWriter subcomponent =
          new SubcomponentWriter(this, Optional.ofNullable(componentMethodDescriptor), subgraph);
      component.addType(subcomponent.write().build());
    }
  }

  private static final int INITIALIZATIONS_PER_INITIALIZE_METHOD = 100;

  private void writeInitializeAndInterfaceMethods() {
    List<List<CodeBlock>> partitions =
        Lists.partition(initializations, INITIALIZATIONS_PER_INITIALIZE_METHOD);

    UniqueNameSet methodNames = new UniqueNameSet();
    for (List<CodeBlock> partition : partitions) {
      String methodName = methodNames.getUniqueName("initialize");
      MethodSpec.Builder initializeMethod =
          methodBuilder(methodName)
              .addModifiers(PRIVATE)
              /* TODO(gak): Strictly speaking, we only need the suppression here if we are also
               * initializing a raw field in this method, but the structure of this code makes it
               * awkward to pass that bit through.  This will be cleaned up when we no longer
               * separate fields and initilization as we do now. */
              .addAnnotation(AnnotationSpecs.suppressWarnings(UNCHECKED))
              .addCode(CodeBlocks.concat(partition));
      if (hasBuilder(graph)) {
        initializeMethod.addParameter(builderName(), "builder", FINAL);
        constructor.addStatement("$L(builder)", methodName);
      } else {
        constructor.addStatement("$L()", methodName);
      }
      component.addMethod(initializeMethod.build());
    }

    component.addMethods(interfaceMethods);
  }

  private void writeMembersInjectionMethods() {
    component.addMethods(membersInjectionMethods.values());
  }

  @Override
  public MethodSpec getMembersInjectionMethod(Key key) {
    return membersInjectionMethods.computeIfAbsent(key, this::membersInjectionMethod);
  }

  private MethodSpec membersInjectionMethod(Key key) {
    Binding binding =
        MoreObjects.firstNonNull(
                graph.resolvedBindings().get(BindingKey.membersInjection(key)),
                graph.resolvedBindings().get(BindingKey.contribution(key)))
            .binding();
    TypeMirror keyType = binding.key().type();
    TypeMirror membersInjectedType =
        isTypeAccessibleFrom(keyType, name.packageName())
            ? keyType
            : elements.getTypeElement("java.lang.Object").asType();
    TypeName membersInjectedTypeName = TypeName.get(membersInjectedType);
    Name bindingTypeName = binding.bindingTypeElement().get().getSimpleName();
    // TODO(ronshapiro): include type parameters in this name e.g. injectFooOfT, and outer class
    // simple names Foo.Builder -> injectFooBuilder
    String methodName = componentMethodNames.getUniqueName("inject" + bindingTypeName);
    ParameterSpec parameter = ParameterSpec.builder(membersInjectedTypeName, "instance").build();
    MethodSpec.Builder method =
        methodBuilder(methodName)
            .addModifiers(PRIVATE)
            .returns(membersInjectedTypeName)
            .addParameter(parameter);
    TypeElement canIgnoreReturnValue =
        elements.getTypeElement("com.google.errorprone.annotations.CanIgnoreReturnValue");
    if (canIgnoreReturnValue != null) {
      method.addAnnotation(ClassName.get(canIgnoreReturnValue));
    }
    CodeBlock instance = CodeBlock.of("$N", parameter);
    method.addCode(
        InjectionSiteMethod.invokeAll(
            injectionSites(binding),
            name,
            instance,
            membersInjectedType,
            types,
            request -> bindingExpressions.getDependencyArgumentExpression(request, name)));
    method.addStatement("return $L", instance);

    return method.build();
  }

  static ImmutableSet<InjectionSite> injectionSites(Binding binding) {
    if (binding instanceof ProvisionBinding) {
      return ((ProvisionBinding) binding).injectionSites();
    } else if (binding instanceof MembersInjectionBinding) {
      return ((MembersInjectionBinding) binding).injectionSites();
    }
    throw new IllegalArgumentException(binding.key().toString());
  }

  // TODO(user): Pull this out into a separate Scoper object or move to field initializer?
  @Override
  public CodeBlock decorateForScope(CodeBlock factoryCreate, Optional<Scope> maybeScope) {
    if (!maybeScope.isPresent()) {
      return factoryCreate;
    }
    Scope scope = maybeScope.get();
    if (requiresReleasableReferences(scope)) {
      return CodeBlock.of(
          "$T.create($L, $L)",
          REFERENCE_RELEASING_PROVIDER,
          factoryCreate,
          getReferenceReleasingProviderManagerExpression(scope));
    } else {
      return CodeBlock.of(
          "$T.provider($L)",
          scope.equals(reusableScope(elements)) ? SINGLE_CHECK : DOUBLE_CHECK,
          factoryCreate);
    }
  }

  @Override
  public ImmutableList<CodeBlock> getBindingDependencyExpressions(Binding binding) {
    ImmutableList<FrameworkDependency> dependencies = binding.frameworkDependencies();
    return dependencies.stream().map(this::getDependencyExpression).collect(toImmutableList());
  }

  @Override
  public CodeBlock getDependencyExpression(FrameworkDependency frameworkDependency) {
    return isProducerFromProvider(frameworkDependency)
        ? getProducerFromProviderBindingExpression(frameworkDependency)
            .getDependencyExpression(frameworkDependency.dependencyRequestKind(), name)
        : bindingExpressions.getDependencyExpression(frameworkDependency, name);
  }

  private FrameworkInstanceBindingExpression getProducerFromProviderBindingExpression(
      FrameworkDependency frameworkDependency) {
    checkState(isProducerFromProvider(frameworkDependency));
    return producerFromProviderBindingExpressions.computeIfAbsent(
        frameworkDependency.bindingKey(),
        dependencyKey ->
            bindingExpressionFactory.forProducerFromProviderField(
                graph.resolvedBindings().get(dependencyKey)));
  }

  private boolean isProducerFromProvider(FrameworkDependency frameworkDependency) {
    ResolvedBindings resolvedBindings =
        graph.resolvedBindings().get(frameworkDependency.bindingKey());
    return resolvedBindings.frameworkClass().equals(Provider.class)
        && frameworkDependency.frameworkClass().equals(Producer.class);
  }
}

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
package dagger2.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import dagger2.MembersInjector;
import dagger2.internal.codegen.MembersInjectionBinding.InjectionSite;
import dagger2.internal.codegen.writer.ClassName;
import dagger2.internal.codegen.writer.ClassWriter;
import dagger2.internal.codegen.writer.ConstructorWriter;
import dagger2.internal.codegen.writer.FieldWriter;
import dagger2.internal.codegen.writer.JavaWriter;
import dagger2.internal.codegen.writer.MethodWriter;
import dagger2.internal.codegen.writer.ParameterizedTypeName;
import dagger2.internal.codegen.writer.Snippet;
import dagger2.internal.codegen.writer.TypeName;
import dagger2.internal.codegen.writer.TypeNames;
import dagger2.internal.codegen.writer.TypeVariableName;
import dagger2.internal.codegen.writer.VoidName;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger2.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger2.internal.codegen.SourceFiles.parameterizedMembersInjectorNameForMembersInjectionBinding;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Generates {@link MembersInjector} implementations from {@link MembersInjectionBinding} instances.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class MembersInjectorGenerator extends SourceFileGenerator<MembersInjectionBinding> {
  private final Elements elements;
  private final Types types;
  private final DependencyRequestMapper dependencyRequestMapper;

  MembersInjectorGenerator(
      Filer filer,
      Elements elements,
      Types types,
      DependencyRequestMapper dependencyRequestMapper) {
    super(filer);
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
    this.dependencyRequestMapper = dependencyRequestMapper;
  }

  @Override
  ClassName nameGeneratedType(MembersInjectionBinding binding) {
    return SourceFiles.membersInjectorNameForMembersInjectionBinding(binding);
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(
      MembersInjectionBinding binding) {
    return FluentIterable.from(binding.injectionSites())
        .transform(new Function<InjectionSite, Element>() {
          @Override public Element apply(InjectionSite injectionSite) {
            return injectionSite.element();
          }
        })
        .toSet();
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(MembersInjectionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, MembersInjectionBinding binding) {
    // We don't want to write out resolved bindings -- we want to write out the generic version.
    checkState(!binding.hasNonDefaultTypeParameters());

    TypeName injectedTypeName = TypeNames.forTypeMirror(binding.key().type());
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());

    ClassWriter injectorWriter = writer.addClass(generatedTypeName.simpleName());
    List<TypeVariableName> typeParameters = Lists.newArrayList();
    for (TypeParameterElement typeParameter : binding.bindingTypeElement().getTypeParameters()) {
      typeParameters.add(TypeVariableName.fromTypeParameterElement(typeParameter));
    }
    injectorWriter.addTypeParameters(typeParameters);
    injectorWriter.annotate(Generated.class)
        .setValue(ComponentProcessor.class.getCanonicalName());
    injectorWriter.addModifiers(PUBLIC, FINAL);
    TypeName implementedType =
        ParameterizedTypeName.create(MembersInjector.class, injectedTypeName);
    injectorWriter.addImplementedType(implementedType);

    ConstructorWriter constructorWriter = injectorWriter.addConstructor();
    constructorWriter.addModifiers(PUBLIC);
    MethodWriter injectMembersWriter = injectorWriter.addMethod(VoidName.VOID, "injectMembers");
    injectMembersWriter.addModifiers(PUBLIC);
    injectMembersWriter.annotate(Override.class);
    injectMembersWriter.addParameter(injectedTypeName, "instance");
    injectMembersWriter.body().addSnippet(Joiner.on('\n').join(
        "if (instance == null) {",
        "  throw new NullPointerException(\"Cannot inject members into a null reference\");",
        "}"));

    Optional<DeclaredType> supertype =
        MoreTypes.nonObjectSuperclass(types, elements, MoreTypes.asDeclared(binding.key().type()));
    if (supertype.isPresent()) {
      ParameterizedTypeName supertypeMemebersInjectorType = ParameterizedTypeName.create(
          MembersInjector.class, TypeNames.forTypeMirror(supertype.get()));
      injectorWriter
          .addField(supertypeMemebersInjectorType, "supertypeInjector")
          .addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(supertypeMemebersInjectorType, "supertypeInjector");
      constructorWriter.body()
          .addSnippet("assert supertypeInjector != null;")
          .addSnippet("this.supertypeInjector = supertypeInjector;");
      injectMembersWriter.body().addSnippet("supertypeInjector.injectMembers(instance);");
    }

    ImmutableMap<BindingKey, FrameworkField> fields =
        SourceFiles.generateBindingFieldsForDependencies(
            dependencyRequestMapper, ImmutableSet.copyOf(binding.dependencies()));

    ImmutableMap.Builder<BindingKey, FieldWriter> dependencyFieldsBuilder =
        ImmutableMap.builder();

    for (Entry<BindingKey, FrameworkField> fieldEntry : fields.entrySet()) {
      FrameworkField bindingField = fieldEntry.getValue();
      TypeName fieldType = bindingField.frameworkType();
      FieldWriter field = injectorWriter.addField(fieldType, bindingField.name());
      field.addModifiers(PRIVATE, FINAL);
      constructorWriter.addParameter(field.type(), field.name());
      constructorWriter.body().addSnippet("assert %s != null;", field.name());
      constructorWriter.body().addSnippet("this.%1$s = %1$s;", field.name());
      dependencyFieldsBuilder.put(fieldEntry.getKey(), field);
    }
    
    // We use a static create method so that generated components can avoid having
    // to refer to the generic types of the factory.
    // (Otherwise they may have visibility problems referring to the types.)
    MethodWriter createMethodWriter = injectorWriter.addMethod(implementedType, "create");
    createMethodWriter.addTypeParameters(typeParameters);
    createMethodWriter.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
    Map<String, TypeName> params = constructorWriter.parameters();
    for (Map.Entry<String, TypeName> param : params.entrySet()) {
      createMethodWriter.addParameter(param.getValue(), param.getKey());      
    }
    createMethodWriter.body().addSnippet("  return new %s(%s);",
        parameterizedMembersInjectorNameForMembersInjectionBinding(binding),
        Joiner.on(", ").join(params.keySet()));
    
    ImmutableMap<BindingKey, FieldWriter> depedencyFields = dependencyFieldsBuilder.build();
    for (InjectionSite injectionSite : binding.injectionSites()) {
      switch (injectionSite.kind()) {
        case FIELD:
          DependencyRequest fieldDependency =
              Iterables.getOnlyElement(injectionSite.dependencies());
          FieldWriter singleField = depedencyFields.get(
              fieldDependency.bindingKey());
          injectMembersWriter.body().addSnippet("instance.%s = %s;",
              injectionSite.element().getSimpleName(),
              frameworkTypeUsageStatement(Snippet.format(singleField.name()),
                  fieldDependency.kind()));
          break;
        case METHOD:
          ImmutableList.Builder<Snippet> parameters = ImmutableList.builder();
          for (DependencyRequest methodDependency : injectionSite.dependencies()) {
            FieldWriter field = depedencyFields.get(
                methodDependency.bindingKey());
            parameters.add(frameworkTypeUsageStatement(Snippet.format(field.name()),
                methodDependency.kind()));
          }
          injectMembersWriter.body().addSnippet("instance.%s(%s);",
              injectionSite.element().getSimpleName(),
              Snippet.makeParametersSnippet(parameters.build()));
          break;
        default:
          throw new AssertionError();
      }
    }
    return ImmutableSet.of(writer);
  }
}

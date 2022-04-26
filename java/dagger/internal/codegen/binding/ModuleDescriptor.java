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

package dagger.internal.codegen.binding;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Collections2.transform;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.SourceFiles.classFileName;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.getMethodDescriptor;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Traverser;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.ModuleKind;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.xprocessing.XTypeElements;
import dagger.spi.model.Key;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Contains metadata that describes a module. */
@AutoValue
public abstract class ModuleDescriptor {

  public abstract XTypeElement moduleElement();

  public abstract ImmutableSet<ContributionBinding> bindings();

  /** The multibinding declarations contained in this module. */
  abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();

  /** The {@link Module#subcomponents() subcomponent declarations} contained in this module. */
  abstract ImmutableSet<SubcomponentDeclaration> subcomponentDeclarations();

  /** The {@link Binds} method declarations that define delegate bindings. */
  abstract ImmutableSet<DelegateDeclaration> delegateDeclarations();

  /** The {@link BindsOptionalOf} method declarations that define optional bindings. */
  abstract ImmutableSet<OptionalBindingDeclaration> optionalDeclarations();

  /** The kind of the module. */
  public abstract ModuleKind kind();

  /** Returns all of the bindings declared in this module. */
  @Memoized
  public ImmutableSet<BindingDeclaration> allBindingDeclarations() {
    return ImmutableSet.<BindingDeclaration>builder()
        .addAll(bindings())
        .addAll(delegateDeclarations())
        .addAll(multibindingDeclarations())
        .addAll(optionalDeclarations())
        .addAll(subcomponentDeclarations())
        .build();
  }

  /** Returns the keys of all bindings declared by this module. */
  ImmutableSet<Key> allBindingKeys() {
    return allBindingDeclarations().stream().map(BindingDeclaration::key).collect(toImmutableSet());
  }

  /** A {@link ModuleDescriptor} factory. */
  @Singleton
  public static final class Factory implements ClearableCache {
    private final XProcessingEnv processingEnv;
    private final BindingFactory bindingFactory;
    private final MultibindingDeclaration.Factory multibindingDeclarationFactory;
    private final DelegateDeclaration.Factory bindingDelegateDeclarationFactory;
    private final SubcomponentDeclaration.Factory subcomponentDeclarationFactory;
    private final OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory;
    private final DaggerSuperficialValidation superficialValidation;
    private final Map<XTypeElement, ModuleDescriptor> cache = new HashMap<>();

    @Inject
    Factory(
        XProcessingEnv processingEnv,
        BindingFactory bindingFactory,
        MultibindingDeclaration.Factory multibindingDeclarationFactory,
        DelegateDeclaration.Factory bindingDelegateDeclarationFactory,
        SubcomponentDeclaration.Factory subcomponentDeclarationFactory,
        OptionalBindingDeclaration.Factory optionalBindingDeclarationFactory,
        DaggerSuperficialValidation superficialValidation) {
      this.processingEnv = processingEnv;
      this.bindingFactory = bindingFactory;
      this.multibindingDeclarationFactory = multibindingDeclarationFactory;
      this.bindingDelegateDeclarationFactory = bindingDelegateDeclarationFactory;
      this.subcomponentDeclarationFactory = subcomponentDeclarationFactory;
      this.optionalBindingDeclarationFactory = optionalBindingDeclarationFactory;
      this.superficialValidation = superficialValidation;
    }

    public ModuleDescriptor create(XTypeElement moduleElement) {
      return reentrantComputeIfAbsent(cache, moduleElement, this::createUncached);
    }

    public ModuleDescriptor createUncached(XTypeElement moduleElement) {
      ImmutableSet.Builder<ContributionBinding> bindings = ImmutableSet.builder();
      ImmutableSet.Builder<DelegateDeclaration> delegates = ImmutableSet.builder();
      ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarations =
          ImmutableSet.builder();
      ImmutableSet.Builder<OptionalBindingDeclaration> optionalDeclarations =
          ImmutableSet.builder();

      XTypeElements.getAllMethods(moduleElement).stream()
          .forEach(
              moduleMethod -> {
                if (moduleMethod.hasAnnotation(TypeNames.PROVIDES)) {
                  bindings.add(bindingFactory.providesMethodBinding(moduleMethod, moduleElement));
                }
                if (moduleMethod.hasAnnotation(TypeNames.PRODUCES)) {
                  bindings.add(bindingFactory.producesMethodBinding(moduleMethod, moduleElement));
                }
                if (moduleMethod.hasAnnotation(TypeNames.BINDS)) {
                  delegates.add(
                      bindingDelegateDeclarationFactory.create(moduleMethod, moduleElement));
                }
                if (moduleMethod.hasAnnotation(TypeNames.MULTIBINDS)) {
                  multibindingDeclarations.add(
                      multibindingDeclarationFactory.forMultibindsMethod(
                          moduleMethod, moduleElement));
                }
                if (moduleMethod.hasAnnotation(TypeNames.BINDS_OPTIONAL_OF)) {
                  optionalDeclarations.add(
                      optionalBindingDeclarationFactory.forMethod(moduleMethod, moduleElement));
                }
              });

      moduleElement.getEnclosedTypeElements().stream()
          .filter(XTypeElement::isCompanionObject)
          .collect(toOptional())
          .ifPresent(companionModule -> collectCompanionModuleBindings(companionModule, bindings));

      return new AutoValue_ModuleDescriptor(
          moduleElement,
          bindings.build(),
          multibindingDeclarations.build(),
          subcomponentDeclarationFactory.forModule(moduleElement),
          delegates.build(),
          optionalDeclarations.build(),
          ModuleKind.forAnnotatedElement(moduleElement).get());
    }

    private void collectCompanionModuleBindings(
        XTypeElement companionModule, ImmutableSet.Builder<ContributionBinding> bindings) {
      ImmutableSet<String> bindingElementDescriptors =
          bindings.build().stream()
              .map(binding -> getMethodDescriptor(asMethod(binding.bindingElement().get())))
              .collect(toImmutableSet());

      XTypeElements.getAllMethods(companionModule).stream()
          // Binding methods in companion objects with @JvmStatic are mirrored in the enclosing
          // class, therefore we should ignore it or else it'll be a duplicate binding.
          .filter(method -> !method.hasAnnotation(TypeNames.JVM_STATIC))
          // Fallback strategy for de-duping contributing bindings in the companion module with
          // @JvmStatic by comparing descriptors. Contributing bindings are the only valid bindings
          // a companion module can declare. See: https://youtrack.jetbrains.com/issue/KT-35104
          // TODO(danysantiago): Checks qualifiers too.
          .filter(method -> !bindingElementDescriptors.contains(getMethodDescriptor(method)))
          .forEach(
              method -> {
                if (method.hasAnnotation(TypeNames.PROVIDES)) {
                  bindings.add(bindingFactory.providesMethodBinding(method, companionModule));
                }
                if (method.hasAnnotation(TypeNames.PRODUCES)) {
                  bindings.add(bindingFactory.producesMethodBinding(method, companionModule));
                }
              });
    }

    /** Returns all the modules transitively included by given modules, including the arguments. */
    ImmutableSet<ModuleDescriptor> transitiveModules(Collection<XTypeElement> modules) {
      // Traverse as a graph to automatically handle modules with cyclic includes.
      return ImmutableSet.copyOf(
          Traverser.forGraph(
                  (ModuleDescriptor module) -> transform(includedModules(module), this::create))
              .depthFirstPreOrder(transform(modules, this::create)));
    }

    private ImmutableSet<XTypeElement> includedModules(ModuleDescriptor moduleDescriptor) {
      return ImmutableSet.copyOf(
          collectIncludedModules(new LinkedHashSet<>(), moduleDescriptor.moduleElement()));
    }

    private Set<XTypeElement> collectIncludedModules(
        Set<XTypeElement> includedModules, XTypeElement moduleElement) {
      XType superclass = moduleElement.getSuperType();
      if (superclass != null) {
        verify(isDeclared(superclass));
        if (!TypeName.OBJECT.equals(superclass.getTypeName())) {
          collectIncludedModules(includedModules, superclass.getTypeElement());
        }
      }
      moduleAnnotation(moduleElement, superficialValidation)
          .ifPresent(
              moduleAnnotation -> {
                includedModules.addAll(moduleAnnotation.includes());
                includedModules.addAll(implicitlyIncludedModules(moduleElement));
              });
      return includedModules;
    }

    private static final ClassName CONTRIBUTES_ANDROID_INJECTOR =
        ClassName.get("dagger.android", "ContributesAndroidInjector");

    // @ContributesAndroidInjector generates a module that is implicitly included in the enclosing
    // module
    private ImmutableSet<XTypeElement> implicitlyIncludedModules(XTypeElement module) {
      if (processingEnv.findTypeElement(CONTRIBUTES_ANDROID_INJECTOR) == null) {
        return ImmutableSet.of();
      }
      return module.getDeclaredMethods().stream()
          .filter(method -> method.hasAnnotation(CONTRIBUTES_ANDROID_INJECTOR))
          .map(
              method ->
                  DaggerSuperficialValidation.requireTypeElement(
                      processingEnv, implicitlyIncludedModuleName(module, method)))
          .collect(toImmutableSet());
    }

    private ClassName implicitlyIncludedModuleName(XTypeElement module, XMethodElement method) {
      return ClassName.get(
          module.getPackageName(),
          String.format(
              "%s_%s",
              classFileName(module.getClassName()),
              LOWER_CAMEL.to(UPPER_CAMEL, getSimpleName(method))));
    }

    @Override
    public void clearCache() {
      cache.clear();
    }
  }
}

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

package dagger.hilt.android.processor.internal.androidentrypoint;

import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.google.common.base.Preconditions;
import com.google.common.collect.MoreCollectors;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

/** Helper class for writing Hilt generators. */
final class Generators {

  /**
   * Copies all constructors with arguments to the builder, if the base class is abstract.
   * Otherwise throws an exception.
   */
  static void copyConstructors(TypeElement baseClass, TypeSpec.Builder builder) {
    List<ExecutableElement> constructors =
        ElementFilter.constructorsIn(baseClass.getEnclosedElements())
            .stream()
            .filter(constructor -> !constructor.getModifiers().contains(PRIVATE))
            .collect(Collectors.toList());

    if (constructors.size() == 1 && getOnlyElement(constructors).getParameters().isEmpty()) {
      // No need to copy the constructor if the default constructor will handle it.
      return;
    }

    constructors.forEach(constructor -> builder.addMethod(copyConstructor(constructor)));
  }

  /** Returns Optional with AnnotationSpec for Nullable if found on element, empty otherwise. */
  private static Optional<AnnotationSpec> getNullableAnnotationSpec(Element element) {
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      if (annotationMirror
          .getAnnotationType()
          .asElement()
          .getSimpleName()
          .contentEquals("Nullable")) {
        AnnotationSpec annotationSpec = AnnotationSpec.get(annotationMirror);
        // If using the android internal Nullable, convert it to the externally-visible version.
        return AndroidClassNames.NULLABLE_INTERNAL.equals(annotationSpec.type)
            ? Optional.of(AnnotationSpec.builder(AndroidClassNames.NULLABLE).build())
            : Optional.of(annotationSpec);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns a ParameterSpec of the input parameter, @Nullable annotated if existing in original
   * (this does not handle Nullable type annotations).
   */
  private static ParameterSpec getParameterSpecWithNullable(VariableElement parameter) {
    ParameterSpec.Builder builder = ParameterSpec.get(parameter).toBuilder();
    getNullableAnnotationSpec(parameter).ifPresent(builder::addAnnotation);
    return builder.build();
  }

  /**
   * Returns a {@link MethodSpec} for a constructor matching the given {@link ExecutableElement}
   * constructor signature, and just calls super. If the constructor is
   * {@link android.annotation.TargetApi} guarded, adds the TargetApi as well.
   */
  // Example:
  //   Foo(Param1 param1, Param2 param2) {
  //     super(param1, param2);
  //   }
  static MethodSpec copyConstructor(ExecutableElement constructor) {
    List<ParameterSpec> params =
        constructor.getParameters().stream()
            .map(parameter -> getParameterSpecWithNullable(parameter))
            .collect(Collectors.toList());

    final MethodSpec.Builder builder =
        MethodSpec.constructorBuilder()
            .addParameters(params)
            .addStatement(
                "super($L)",
                params.stream().map(param -> param.name).collect(Collectors.joining(", ")));

    constructor.getAnnotationMirrors().stream()
        .filter(a -> Processors.hasAnnotation(a, AndroidClassNames.TARGET_API))
        .collect(MoreCollectors.toOptional())
        .map(AnnotationSpec::get)
        .ifPresent(builder::addAnnotation);

    return builder.build();
  }

  /**
   * Copies the Android lint annotations from the annotated element to the generated element.
   *
   * <p>Note: For now we only copy over {@link android.annotation.TargetApi}.
   */
  static void copyLintAnnotations(Element element, TypeSpec.Builder builder) {
    if (Processors.hasAnnotation(element, AndroidClassNames.TARGET_API)) {
      builder.addAnnotation(
          AnnotationSpec.get(
              Processors.getAnnotationMirror(element, AndroidClassNames.TARGET_API)));
    }
  }

  // @Override
  // public CompT generatedComponent() {
  //   return componentManager().generatedComponent();
  // }
  static void addComponentOverride(AndroidEntryPointMetadata metadata, TypeSpec.Builder builder) {
    if (metadata.overridesAndroidEntryPointClass()) {
      // We don't need to override this method if we are extending a Hilt type.
      return;
    }
    builder
        .addSuperinterface(ParameterizedTypeName.get(ClassNames.COMPONENT_MANAGER, TypeName.OBJECT))
        .addMethod(
            MethodSpec.methodBuilder("generatedComponent")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(TypeName.OBJECT)
                .addStatement("return componentManager().generatedComponent()")
                .build());
  }

  /** Adds the inject() and optionally the componentManager() methods to allow for injection. */
  static void addInjectionMethods(AndroidEntryPointMetadata metadata, TypeSpec.Builder builder) {
    switch (metadata.androidType()) {
      case ACTIVITY:
      case FRAGMENT:
      case VIEW:
      case SERVICE:
        addComponentManagerMethods(metadata, builder);
        // fall through
      case BROADCAST_RECEIVER:
        addInjectMethod(metadata, builder);
        break;
      default:
        throw new AssertionError();
    }
  }

  // @Override
  // public FragmentComponentManager componentManager() {
  //   if (componentManager == null) {
  //     synchronize (componentManagerLock) {
  //       if (componentManager == null) {
  //         componentManager = createComponentManager();
  //       }
  //     }
  //   }
  //   return componentManager;
  // }
  private static void addComponentManagerMethods(
      AndroidEntryPointMetadata metadata, TypeSpec.Builder typeSpecBuilder) {
    if (metadata.overridesAndroidEntryPointClass()) {
      // We don't need to override this method if we are extending a Hilt type.
      return;
    }

    ParameterSpec managerParam = metadata.componentManagerParam();
    typeSpecBuilder.addField(componentManagerField(metadata));

    typeSpecBuilder.addMethod(createComponentManagerMethod(metadata));

    MethodSpec.Builder methodSpecBuilder =
        MethodSpec.methodBuilder("componentManager")
            .addModifiers(Modifier.PROTECTED, Modifier.FINAL)
            .returns(managerParam.type)
            .beginControlFlow("if ($N == null)", managerParam);

    // Views do not do double-checked locking because this is called from the constructor
    if (metadata.androidType() != AndroidEntryPointMetadata.AndroidType.VIEW) {
      typeSpecBuilder.addField(componentManagerLockField());

      methodSpecBuilder
        .beginControlFlow("synchronized (componentManagerLock)")
        .beginControlFlow("if ($N == null)", managerParam);
    }

    methodSpecBuilder
        .addStatement("$N = createComponentManager()", managerParam)
        .endControlFlow();

    if (metadata.androidType() != AndroidEntryPointMetadata.AndroidType.VIEW) {
      methodSpecBuilder
          .endControlFlow()
          .endControlFlow();
    }

    methodSpecBuilder.addStatement("return $N", managerParam);

    typeSpecBuilder.addMethod(methodSpecBuilder.build());
  }

  // protected FragmentComponentManager createComponentManager() {
  //   return new FragmentComponentManager(initArgs);
  // }
  private static MethodSpec createComponentManagerMethod(AndroidEntryPointMetadata metadata) {
    Preconditions.checkState(
        metadata.componentManagerInitArgs().isPresent(),
        "This method should not have been called for metadata where the init args are not"
            + " present.");
    return MethodSpec.methodBuilder("createComponentManager")
        .addModifiers(Modifier.PROTECTED)
        .returns(metadata.componentManager())
        .addStatement(
            "return new $T($L)",
            metadata.componentManager(),
            metadata.componentManagerInitArgs().get())
        .build();
  }

  // private volatile ComponentManager componentManager;
  private static FieldSpec componentManagerField(AndroidEntryPointMetadata metadata) {
    ParameterSpec managerParam = metadata.componentManagerParam();
    FieldSpec.Builder builder = FieldSpec.builder(managerParam.type, managerParam.name)
        .addModifiers(Modifier.PRIVATE);

    // Views do not need volatile since these are set in the constructor if ever set.
    if (metadata.androidType() != AndroidEntryPointMetadata.AndroidType.VIEW) {
      builder.addModifiers(Modifier.VOLATILE);
    }

    return builder.build();
  }

  // private final Object componentManagerLock = new Object();
  private static FieldSpec componentManagerLockField() {
    return FieldSpec.builder(TypeName.get(Object.class), "componentManagerLock")
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
        .initializer("new Object()")
        .build();
  }

  // protected void inject() {
  //   if (!injected) {
  //     generatedComponent().inject$CLASS(($CLASS) this);
  //     injected = true;
  //   }
  // }
  private static void addInjectMethod(
      AndroidEntryPointMetadata metadata, TypeSpec.Builder typeSpecBuilder) {
    MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("inject")
        .addModifiers(Modifier.PROTECTED);

    // Check if the parent is a Hilt type. If it isn't or if it is but it
    // wasn't injected by hilt, then return.
    // Object parent = ...depends on type...
    // if (!(parent instanceof GeneratedComponentManager)
    //     || ((parent instanceof InjectedByHilt) &&
    //         !((InjectedByHilt) parent).wasInjectedByHilt())) {
    //   return;
    //
    if (metadata.allowsOptionalInjection()) {
      methodSpecBuilder
          .addStatement("$T parent = $L", ClassNames.OBJECT, getParentCodeBlock(metadata))
          .beginControlFlow(
              "if (!(parent instanceof $T) "
              + "|| ((parent instanceof $T) && !(($T) parent).wasInjectedByHilt()))",
              ClassNames.COMPONENT_MANAGER,
              AndroidClassNames.INJECTED_BY_HILT,
              AndroidClassNames.INJECTED_BY_HILT)
          .addStatement("return")
          .endControlFlow();
    }

    switch (metadata.androidType()) {
      case ACTIVITY:
      case FRAGMENT:
      case VIEW:
      case SERVICE:
        if (metadata.overridesAndroidEntryPointClass()) {
          typeSpecBuilder.addField(injectedField(metadata));

          methodSpecBuilder
              .addAnnotation(Override.class)
              .beginControlFlow("if (!injected)")
              .addStatement("injected = true");
        } else if (metadata.allowsOptionalInjection()) {
          typeSpecBuilder.addField(injectedField(metadata));
          methodSpecBuilder.addStatement("injected = true");
        }
        methodSpecBuilder.addStatement(
            "(($T) generatedComponent()).$L($T.<$T>unsafeCast(this))",
            metadata.injectorClassName(),
            metadata.injectMethodName(),
            ClassNames.UNSAFE_CASTS,
            metadata.elementClassName());
        if (metadata.overridesAndroidEntryPointClass()) {
          methodSpecBuilder.endControlFlow();
        }
        break;
      case BROADCAST_RECEIVER:
        typeSpecBuilder.addField(injectedLockField());
        typeSpecBuilder.addField(injectedField(metadata));

        methodSpecBuilder
            .addParameter(ParameterSpec.builder(AndroidClassNames.CONTEXT, "context").build())
            .beginControlFlow("if (!injected)")
            .beginControlFlow("synchronized (injectedLock)")
            .beginControlFlow("if (!injected)")
            .addStatement(
                "(($T) $T.generatedComponent(context)).$L($T.<$T>unsafeCast(this))",
                metadata.injectorClassName(),
                metadata.componentManager(),
                metadata.injectMethodName(),
                ClassNames.UNSAFE_CASTS,
                metadata.elementClassName())
            .addStatement("injected = true")
            .endControlFlow()
            .endControlFlow()
            .endControlFlow();
        break;
      default:
        throw new AssertionError();
    }

    // Also add a wasInjectedByHilt method if needed.
    // Even if we aren't optionally injected, if we override an optionally injected Hilt class
    // we also need to override the wasInjectedByHilt method.
    if (metadata.allowsOptionalInjection() || metadata.baseAllowsOptionalInjection()) {
      typeSpecBuilder.addMethod(
          MethodSpec.methodBuilder("wasInjectedByHilt")
              .addAnnotation(Override.class)
              .addModifiers(Modifier.PUBLIC)
              .returns(boolean.class)
              .addStatement("return injected")
              .build());
      // Only add the interface though if this class allows optional injection (not that it
      // really matters since if the base allows optional injection the class implements the
      // interface anyway). But it is probably better to be consistent about only optionally
      // injected classes extend the interface.
      if (metadata.allowsOptionalInjection()) {
        typeSpecBuilder.addSuperinterface(AndroidClassNames.INJECTED_BY_HILT);
      }
    }

    typeSpecBuilder.addMethod(methodSpecBuilder.build());
  }

  private static CodeBlock getParentCodeBlock(AndroidEntryPointMetadata metadata) {
    switch (metadata.androidType()) {
      case ACTIVITY:
      case SERVICE:
        return CodeBlock.of("getApplicationContext()");
      case FRAGMENT:
        return CodeBlock.of("getHost()");
      case VIEW:
        return CodeBlock.of(
            "componentManager().maybeGetParentComponentManager()",
            metadata.componentManagerParam());
      case BROADCAST_RECEIVER:
        // Broadcast receivers receive a "context" parameter
        return CodeBlock.of("context.getApplicationContext()");
      default:
        throw new AssertionError();
    }
  }

  // private boolean injected = false;
  private static FieldSpec injectedField(AndroidEntryPointMetadata metadata) {
    FieldSpec.Builder builder = FieldSpec.builder(TypeName.BOOLEAN, "injected")
        .addModifiers(Modifier.PRIVATE);

    // Broadcast receivers do double-checked locking so this needs to be volatile
    if (metadata.androidType() == AndroidEntryPointMetadata.AndroidType.BROADCAST_RECEIVER) {
      builder.addModifiers(Modifier.VOLATILE);
    }

    // Views should not add an initializer here as this runs after the super constructor
    // and may reset state set during the super constructor call.
    if (metadata.androidType() != AndroidEntryPointMetadata.AndroidType.VIEW) {
      builder.initializer("false");
    }
    return builder.build();
  }

  // private final Object injectedLock = new Object();
  private static FieldSpec injectedLockField() {
    return FieldSpec.builder(TypeName.OBJECT, "injectedLock")
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
        .initializer("new $T()", TypeName.OBJECT)
        .build();
  }

  private Generators() {}
}

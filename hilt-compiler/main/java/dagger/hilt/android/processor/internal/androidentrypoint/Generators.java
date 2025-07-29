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

import static androidx.room.compiler.processing.JavaPoetExtKt.toAnnotationSpec;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.JavaPoetExtKt;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.android.processor.internal.androidentrypoint.AndroidEntryPointMetadata.AndroidType;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;

/** Helper class for writing Hilt generators. */
final class Generators {
  private static final ImmutableMap<ClassName, String> SUPPRESS_ANNOTATION_PROPERTY_NAME =
      ImmutableMap.<ClassName, String>builder()
          .put(ClassNames.SUPPRESS_WARNINGS, "value")
          .put(ClassNames.KOTLIN_SUPPRESS, "names")
          .build();

  static void addGeneratedBaseClassJavadoc(TypeSpec.Builder builder, ClassName annotation) {
    builder.addJavadoc("A generated base class to be extended by the @$T annotated class. If using"
        + " the Gradle plugin, this is swapped as the base class via bytecode transformation.",
        annotation);
  }

  /** Copies all constructors with arguments to the builder. */
  static void copyConstructors(
      XTypeElement baseClass, TypeSpec.Builder builder, XTypeElement subclassReference) {
    copyConstructors(baseClass, CodeBlock.builder().build(), builder, subclassReference);
  }

  /** Copies all constructors with arguments along with an appended body to the builder. */
  static void copyConstructors(
      XTypeElement baseClass,
      CodeBlock body,
      TypeSpec.Builder builder,
      XTypeElement subclassReference) {
    ImmutableList<XConstructorElement> constructors =
        baseClass.getConstructors().stream()
            .filter(constructor -> isConstructorVisibleToSubclass(constructor, subclassReference))
            .collect(toImmutableList());

    if (constructors.size() == 1
        && getOnlyElement(constructors).getParameters().isEmpty()
        && body.isEmpty()) {
      // No need to copy the constructor if the default constructor will handle it.
      return;
    }

    constructors.forEach(constructor -> builder.addMethod(copyConstructor(constructor, body)));
  }

  /**
   * Returns true if the constructor is visible to a subclass in the same package as the reference.
   * A reference is used because usually for generators the subclass is being generated and so
   * doesn't actually exist.
   */
  static boolean isConstructorVisibleToSubclass(
      XConstructorElement constructor, XTypeElement subclassReference) {
    // Check if the constructor has package private visibility and we're outside the package
    if (Processors.hasJavaPackagePrivateVisibility(constructor)
        && !constructor
            .getEnclosingElement()
            .getPackageName()
            .contentEquals(subclassReference.getPackageName())) {
      return false;
      // Or if it is private, we know generated code can't be in the same file
    } else if (constructor.isPrivate()) {
      return false;
    }

    // Assume this is for a subclass per the name, so both protected and public methods are always
    // accessible.
    return true;
  }

  /** Returns Optional with AnnotationSpec for Nullable if found on element, empty otherwise. */
  private static Optional<AnnotationSpec> getNullableAnnotationSpec(XElement element) {
    for (XAnnotation annotation : element.getAllAnnotations()) {
      if (annotation.getClassName().simpleName().contentEquals("Nullable")) {
        AnnotationSpec annotationSpec = toAnnotationSpec(annotation);
        // If using the android internal Nullable, convert it to the externally-visible version.
        return AndroidClassNames.NULLABLE_INTERNAL.equals(annotationSpec.type)
            ? Optional.of(AnnotationSpec.builder(AndroidClassNames.NULLABLE).build())
            : Optional.of(annotationSpec);
      }
    }
    return Optional.empty();
  }

  /** Returns a TypeName for the given type, including any @Nullable annotations on it. */
  private static TypeName withAnyNullnessAnnotation(XType type) {
    for (XAnnotation annotation : type.getAllAnnotations()) {
      if (annotation.getClassName().simpleName().contentEquals("Nullable")) {
        return type.getTypeName().annotated(toAnnotationSpec(annotation));
      }
    }
    return type.getTypeName();
  }

  /**
   * Returns a ParameterSpec of the input parameter, @Nullable annotated if existing in original.
   */
  private static ParameterSpec getParameterSpecWithNullable(XVariableElement parameter) {
    TypeName type = withAnyNullnessAnnotation(parameter.getType());
    ParameterSpec.Builder builder = ParameterSpec.builder(type, getSimpleName(parameter));
    /*
     * If we already have a type-use Nullable, don't consider also adding a declaration Nullable,
     * which could be a duplicate in the case of "hybrid" annotations that support both type-use and
     * declaration targets.
     */
    if (!type.isAnnotated()) {
      getNullableAnnotationSpec(parameter).ifPresent(builder::addAnnotation);
    }
    return builder.build();
  }

  /**
   * Returns a {@link MethodSpec} for a constructor matching the given {@link XConstructorElement}
   * constructor signature, and just calls super. If the constructor is {@link
   * android.annotation.TargetApi} guarded, adds the TargetApi as well.
   */
  // Example:
  //   Foo(Param1 param1, Param2 param2) {
  //     super(param1, param2);
  //   }
  static MethodSpec copyConstructor(XConstructorElement constructor) {
    return copyConstructor(constructor, CodeBlock.builder().build());
  }

  private static MethodSpec copyConstructor(XConstructorElement constructor, CodeBlock body) {
    List<ParameterSpec> params =
        constructor.getParameters().stream()
            .map(Generators::getParameterSpecWithNullable)
            .collect(Collectors.toList());

    final MethodSpec.Builder builder =
        MethodSpec.constructorBuilder()
            .addParameters(params)
            .addStatement(
                "super($L)", params.stream().map(param -> param.name).collect(joining(", ")))
            .addCode(body);

    constructor.getAllAnnotations().stream()
        .filter(a -> a.getTypeElement().hasAnnotation(AndroidClassNames.TARGET_API))
        .collect(toOptional())
        .map(JavaPoetExtKt::toAnnotationSpec)
        .ifPresent(builder::addAnnotation);

    return builder.build();
  }

  /** Copies SuppressWarnings annotations from the annotated element to the generated element. */
  static void copySuppressAnnotations(XElement element, TypeSpec.Builder builder) {
    ImmutableSet<String> suppressValues =
        SUPPRESS_ANNOTATION_PROPERTY_NAME.keySet().stream()
            .filter(element::hasAnnotation)
            .flatMap(
                annotation ->
                    element
                        .getAnnotation(annotation)
                        .getAsStringList(SUPPRESS_ANNOTATION_PROPERTY_NAME.get(annotation))
                        .stream())
            .collect(toImmutableSet());

    if (!suppressValues.isEmpty()) {
      // Replace kotlin Suppress with java SuppressWarnings, as the generated file is java.
      AnnotationSpec.Builder annotation = AnnotationSpec.builder(ClassNames.SUPPRESS_WARNINGS);
      suppressValues.forEach(value -> annotation.addMember("value", "$S", value));
      builder.addAnnotation(annotation.build());
    }
  }

  /**
   * Copies the Android lint annotations from the annotated element to the generated element.
   *
   * <p>Note: For now we only copy over {@link android.annotation.TargetApi}.
   */
  static void copyLintAnnotations(XElement element, TypeSpec.Builder builder) {
    if (element.hasAnnotation(AndroidClassNames.TARGET_API)) {
      builder.addAnnotation(toAnnotationSpec(element.getAnnotation(AndroidClassNames.TARGET_API)));
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
        .addSuperinterface(ClassNames.GENERATED_COMPONENT_MANAGER_HOLDER)
        .addMethod(
            MethodSpec.methodBuilder("generatedComponent")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(TypeName.OBJECT)
                .addStatement("return $L.generatedComponent()", componentManagerCallBlock(metadata))
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
        addInjectAndMaybeOptionalInjectMethod(metadata, builder);
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
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
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
  private static void addInjectAndMaybeOptionalInjectMethod(
      AndroidEntryPointMetadata metadata, TypeSpec.Builder typeSpecBuilder) {
    MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("inject")
        .addModifiers(Modifier.PROTECTED);

    // Check if the parent is a Hilt type. If it isn't or if it is but it
    // wasn't injected by hilt, then return.
    // Object parent = ...depends on type...
    // if (!optionalInjectParentUsesHilt()) {
    //   return;
    //
    if (metadata.allowsOptionalInjection()) {
      CodeBlock parentCodeBlock;
      if (metadata.androidType() != AndroidType.BROADCAST_RECEIVER) {
        parentCodeBlock = CodeBlock.of("optionalInjectGetParent()");

        // Also, add the optionalInjectGetParent method we just used. This is a separate method so
        // other parts of the code when dealing with @OptionalInject. BroadcastReceiver can't have
        // this method since the context is only accessible as a parameter to receive()/inject().
        typeSpecBuilder.addMethod(MethodSpec.methodBuilder("optionalInjectGetParent")
            .addModifiers(Modifier.PRIVATE)
            .returns(TypeName.OBJECT)
            .addStatement("return $L", getParentCodeBlock(metadata))
            .build());
      } else {
        // For BroadcastReceiver, use the "context" field that is on the stack.
        parentCodeBlock = CodeBlock.of(
            "$T.getApplication(context.getApplicationContext())", ClassNames.CONTEXTS);
      }

      methodSpecBuilder
          .beginControlFlow("if (!optionalInjectParentUsesHilt($L))", parentCodeBlock)
          .addStatement("return")
          .endControlFlow();

      // Add the optionalInjectParentUsesHilt used above.
      typeSpecBuilder.addMethod(MethodSpec.methodBuilder("optionalInjectParentUsesHilt")
          .addModifiers(Modifier.PRIVATE)
          .addParameter(TypeName.OBJECT, "parent")
          .returns(TypeName.BOOLEAN)
          .addStatement("return (parent instanceof $T) "
              + "&& (!(parent instanceof $T) || (($T) parent).wasInjectedByHilt())",
              ClassNames.GENERATED_COMPONENT_MANAGER,
              AndroidClassNames.INJECTED_BY_HILT,
              AndroidClassNames.INJECTED_BY_HILT)
          .build());
    }

    // Only add @Override if an ancestor extends a generated Hilt class.
    // When using bytecode injection, this isn't always guaranteed.
    if (metadata.overridesAndroidEntryPointClass()
        && ancestorExtendsGeneratedHiltClass(metadata)) {
      methodSpecBuilder.addAnnotation(Override.class);
    }
    typeSpecBuilder.addField(injectedField(metadata));

    switch (metadata.androidType()) {
      case ACTIVITY:
      case FRAGMENT:
      case VIEW:
      case SERVICE:
        methodSpecBuilder
            .beginControlFlow("if (!injected)")
            .addStatement("injected = true")
            .addStatement(
                "(($T) $L).$L($L)",
                metadata.injectorClassName(),
                generatedComponentCallBlock(metadata),
                metadata.injectMethodName(),
                unsafeCastThisTo(metadata.elementClassName()))
            .endControlFlow();
        break;
      case BROADCAST_RECEIVER:
        typeSpecBuilder.addField(injectedLockField());

        methodSpecBuilder
            .addParameter(ParameterSpec.builder(AndroidClassNames.CONTEXT, "context").build())
            .beginControlFlow("if (!injected)")
            .beginControlFlow("synchronized (injectedLock)")
            .beginControlFlow("if (!injected)")
            .addStatement(
                "(($T) $T.generatedComponent(context)).$L($L)",
                metadata.injectorClassName(),
                metadata.componentManager(),
                metadata.injectMethodName(),
                unsafeCastThisTo(metadata.elementClassName()))
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
        return CodeBlock.of("$T.getApplication(getApplicationContext())", ClassNames.CONTEXTS);
      case FRAGMENT:
        return CodeBlock.of("getHost()");
      case VIEW:
        return CodeBlock.of(
            "$L.maybeGetParentComponentManager()", componentManagerCallBlock(metadata));
      case BROADCAST_RECEIVER:
        // Broadcast receivers receive a "context" parameter that make it so this code block
        // isn't really usable anywhere
        throw new AssertionError("BroadcastReceiver types should not get here");
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns the call to {@code generatedComponent()} with casts if needed.
   *
   * <p>A cast is required when the root generated Hilt class uses bytecode injection because
   * subclasses won't have access to the {@code generatedComponent()} method in that case.
   */
  private static CodeBlock generatedComponentCallBlock(AndroidEntryPointMetadata metadata) {
    return CodeBlock.of(
        "$L.generatedComponent()",
        !metadata.isRootMetadata() && metadata.rootMetadata().requiresBytecodeInjection()
            ? unsafeCastThisTo(ClassNames.GENERATED_COMPONENT_MANAGER_HOLDER)
            : "this");
  }

  /**
   * Returns the call to {@code componentManager()} with casts if needed.
   *
   * <p>A cast is required when the root generated Hilt class uses bytecode injection because
   * subclasses won't have access to the {@code componentManager()} method in that case.
   */
  private static CodeBlock componentManagerCallBlock(AndroidEntryPointMetadata metadata) {
    return CodeBlock.of(
        "$L.componentManager()",
        !metadata.isRootMetadata() && metadata.rootMetadata().requiresBytecodeInjection()
            ? unsafeCastThisTo(ClassNames.GENERATED_COMPONENT_MANAGER_HOLDER)
            : "this");
  }

  static CodeBlock unsafeCastThisTo(ClassName castType) {
    return CodeBlock.of("$T.<$T>unsafeCast(this)", ClassNames.UNSAFE_CASTS, castType);
  }

  /** Returns {@code true} if the an ancestor annotated class extends the generated class */
  private static boolean ancestorExtendsGeneratedHiltClass(AndroidEntryPointMetadata metadata) {
    while (metadata.baseMetadata().isPresent()) {
      metadata = metadata.baseMetadata().get();
      if (!metadata.requiresBytecodeInjection()) {
        return true;
      }
    }
    return false;
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

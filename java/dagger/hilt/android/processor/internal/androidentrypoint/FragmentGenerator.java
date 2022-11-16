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

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.Processors;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

/** Generates an Hilt Fragment class for the @AndroidEntryPoint annotated class. */
public final class FragmentGenerator {
  private static final FieldSpec COMPONENT_CONTEXT_FIELD =
      FieldSpec.builder(AndroidClassNames.CONTEXT_WRAPPER, "componentContext")
          .addModifiers(Modifier.PRIVATE)
          .build();

  private static final FieldSpec DISABLE_GET_CONTEXT_FIX_FIELD =
      FieldSpec.builder(TypeName.BOOLEAN, "disableGetContextFix")
          .addModifiers(Modifier.PRIVATE)
          .build();

  private final ProcessingEnvironment env;
  private final AndroidEntryPointMetadata metadata;
  private final ClassName generatedClassName;

  public FragmentGenerator(
      ProcessingEnvironment env,
      AndroidEntryPointMetadata metadata ) {
    this.env = env;
    this.metadata = metadata;
    generatedClassName = metadata.generatedClassName();
  }

  public void generate() throws IOException {
    JavaFile.builder(generatedClassName.packageName(), createTypeSpec())
        .build()
        .writeTo(env.getFiler());
  }

  // @Generated("FragmentGenerator")
  // abstract class Hilt_$CLASS extends $BASE implements ComponentManager<?> {
  //   ...
  // }
  TypeSpec createTypeSpec() {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(generatedClassName.simpleName())
            .addOriginatingElement(metadata.element())
            .superclass(metadata.baseClassName())
            .addModifiers(metadata.generatedClassModifiers())
            .addField(COMPONENT_CONTEXT_FIELD)
            .addMethod(onAttachContextMethod())
            .addMethod(onAttachActivityMethod())
            .addMethod(initializeComponentContextMethod())
            .addMethod(getContextMethod())
            .addMethod(inflatorMethod())
            .addField(DISABLE_GET_CONTEXT_FIX_FIELD);

    Generators.addGeneratedBaseClassJavadoc(builder, AndroidClassNames.ANDROID_ENTRY_POINT);
    Processors.addGeneratedAnnotation(builder, env, getClass());
    Generators.copyLintAnnotations(metadata.element(), builder);
    Generators.copySuppressAnnotations(metadata.element(), builder);
    Generators.copyConstructors(metadata.baseElement(), builder);

    metadata.baseElement().getTypeParameters().stream()
        .map(TypeVariableName::get)
        .forEachOrdered(builder::addTypeVariable);

    Generators.addComponentOverride(metadata, builder);

    Generators.addInjectionMethods(metadata, builder);

    if (!metadata.overridesAndroidEntryPointClass() ) {
      builder.addMethod(getDefaultViewModelProviderFactory());
    }

    return builder.build();
  }

  // @CallSuper
  // @Override
  // public void onAttach(Context context) {
  //   super.onAttach(context);
  //   initializeComponentContext();
  //   inject();
  // }
  private static MethodSpec onAttachContextMethod() {
    return MethodSpec.methodBuilder("onAttach")
        .addAnnotation(Override.class)
        .addAnnotation(AndroidClassNames.CALL_SUPER)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(AndroidClassNames.CONTEXT, "context")
        .addStatement("super.onAttach(context)")
        .addStatement("initializeComponentContext()")
        // The inject method will internally check if injected already
        .addStatement("inject()")
        .build();
  }

  // @CallSuper
  // @Override
  // @SuppressWarnings("deprecation")
  // public void onAttach(Activity activity) {
  //   super.onAttach(activity);
  //   Preconditions.checkState(
  //       componentContext == null || FragmentComponentManager.findActivity(
  //           componentContext) == activity, "...");
  //   initializeComponentContext();
  //   inject();
  // }
  private static MethodSpec onAttachActivityMethod() {
    return MethodSpec.methodBuilder("onAttach")
        .addAnnotation(Override.class)
        .addAnnotation(
            AnnotationSpec.builder(ClassNames.SUPPRESS_WARNINGS)
                .addMember("value", "\"deprecation\"")
                .build())
        .addAnnotation(AndroidClassNames.CALL_SUPER)
        .addAnnotation(AndroidClassNames.MAIN_THREAD)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(AndroidClassNames.ACTIVITY, "activity")
        .addStatement("super.onAttach(activity)")
        .addStatement(
            "$T.checkState($N == null || $T.findActivity($N) == activity, $S)",
            ClassNames.PRECONDITIONS,
            COMPONENT_CONTEXT_FIELD,
            AndroidClassNames.FRAGMENT_COMPONENT_MANAGER,
            COMPONENT_CONTEXT_FIELD,
            "onAttach called multiple times with different Context! "
                + "Hilt Fragments should not be retained.")
        .addStatement("initializeComponentContext()")
        // The inject method will internally check if injected already
        .addStatement("inject()")
        .build();
  }

  // private void initializeComponentContext() {
  //   if (componentContext == null) {
  //     // Note: The LayoutInflater provided by this componentContext may be different from super
  //     // Fragment's because we are getting it from base context instead of cloning from super
  //     // Fragment's LayoutInflater.
  //     componentContext = FragmentComponentManager.createContextWrapper(super.getContext(), this);
  //     disableGetContextFix = FragmentGetContextFix.isFragmentGetContextFixDisabled(
  //         super.getContext());
  //   }
  // }
  private MethodSpec initializeComponentContextMethod() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("initializeComponentContext")
        .addModifiers(Modifier.PRIVATE)
        .beginControlFlow("if ($N == null)", COMPONENT_CONTEXT_FIELD)
        .addComment(
            "Note: The LayoutInflater provided by this componentContext may be different from"
                + " super Fragment's because we getting it from base context instead of cloning"
                + " from the super Fragment's LayoutInflater.")
        .addStatement(
            "$N = $T.createContextWrapper(super.getContext(), this)",
            COMPONENT_CONTEXT_FIELD,
            metadata.componentManager());
    if (metadata.allowsOptionalInjection()) {
      // When optionally injected, since the runtime flag is only available in Hilt, we need to
      // check that the parent uses Hilt first.
      builder.beginControlFlow("if (optionalInjectParentUsesHilt(optionalInjectGetParent()))");
    }

    builder
        .addStatement("$N = $T.isFragmentGetContextFixDisabled(super.getContext())",
            DISABLE_GET_CONTEXT_FIX_FIELD,
            AndroidClassNames.FRAGMENT_GET_CONTEXT_FIX);

    if (metadata.allowsOptionalInjection()) {
      // If not attached to a Hilt parent, just disable the fix for now since this is the current
      // default. There's not a good way to flip this at runtime without Hilt, so after we flip
      // the default we may just have to flip this and hope that the Hilt usage is already enough
      // coverage as this should be a fairly rare case.
      builder.nextControlFlow("else")
          .addStatement("$N = true", DISABLE_GET_CONTEXT_FIX_FIELD)
          .endControlFlow();
    }

    return builder
        .endControlFlow()
        .build();
  }

  // @Override
  // public Context getContext() {
  //   if (super.getContext() == null && !disableGetContextFix) {
  //     return null;
  //   }
  //   initializeComponentContext();
  //   return componentContext;
  // }
  private MethodSpec getContextMethod() {
    return MethodSpec.methodBuilder("getContext")
        .returns(AndroidClassNames.CONTEXT)
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        // Note that disableGetContext can only be true if componentContext is set, so if it is
        // true we don't need to check whether componentContext is set or not.
        .beginControlFlow(
            "if (super.getContext() == null && !$N)",
            DISABLE_GET_CONTEXT_FIX_FIELD)
        .addStatement("return null")
        .endControlFlow()
        .addStatement("initializeComponentContext()")
        .addStatement("return $N", COMPONENT_CONTEXT_FIELD)
        .build();
  }

  // @Override
  // public LayoutInflater onGetLayoutInflater(Bundle savedInstanceState) {
  //   LayoutInflater inflater = super.onGetLayoutInflater(savedInstanceState);
  //   return LayoutInflater.from(FragmentComponentManager.createContextWrapper(inflater, this));
  // }
  private MethodSpec inflatorMethod() {
    return MethodSpec.methodBuilder("onGetLayoutInflater")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(AndroidClassNames.BUNDLE, "savedInstanceState")
        .returns(AndroidClassNames.LAYOUT_INFLATER)
        .addStatement(
            "$T inflater = super.onGetLayoutInflater(savedInstanceState)",
            AndroidClassNames.LAYOUT_INFLATER)
        .addStatement(
            "return inflater.cloneInContext($T.createContextWrapper(inflater, this))",
            metadata.componentManager())
        .build();
  }

  // @Override
  // public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
  //   return DefaultViewModelFactories.getFragmentFactory(
  //       this, super.getDefaultViewModelProviderFactory());
  // }
  private MethodSpec getDefaultViewModelProviderFactory() {
    MethodSpec.Builder builder = MethodSpec.methodBuilder("getDefaultViewModelProviderFactory")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(AndroidClassNames.VIEW_MODEL_PROVIDER_FACTORY);

    if (metadata.allowsOptionalInjection()) {
      builder
          .beginControlFlow("if (!optionalInjectParentUsesHilt(optionalInjectGetParent()))")
          .addStatement("return super.getDefaultViewModelProviderFactory()")
          .endControlFlow();
    }

    return builder
        .addStatement(
            "return $T.getFragmentFactory(this, super.getDefaultViewModelProviderFactory())",
            AndroidClassNames.DEFAULT_VIEW_MODEL_FACTORIES)
        .build();
  }
}

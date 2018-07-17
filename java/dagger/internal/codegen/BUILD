# Copyright (C) 2017 The Dagger Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Description:
#   A JSR-330 compliant dependency injection system for android and java

package(default_visibility = ["//:src"])

load("//:build_defs.bzl", "DOCLINT_HTML_AND_SYNTAX", "DOCLINT_REFERENCES")
load("//tools:maven.bzl", "pom_file", "POM_VERSION")

EXPERIMENTAL_VISUALIZER_SRCS = ["BindingNetworkVisualizer.java"]

KYTHE_SRCS = [
    "KytheBindingGraphFactory.java",
    "DaggerKythePlugin.java",
]

CODEGEN_SRCS = glob(
    ["*.java"],
    exclude = EXPERIMENTAL_VISUALIZER_SRCS + KYTHE_SRCS,
)

CODEGEN_PLUGINS = [":bootstrap_compiler_plugin"]

CODEGEN_SHARED_DEPS = [
    "@google_bazel_common//third_party/java/auto:service",
    "@google_bazel_common//third_party/java/auto:value",
    "@google_bazel_common//third_party/java/auto:common",
    "@google_bazel_common//third_party/java/error_prone:annotations",
    "@google_bazel_common//third_party/java/google_java_format",
    "@google_bazel_common//third_party/java/javapoet",
    "@local_jdk//:lib/tools.jar",
    "@google_bazel_common//third_party/java/jsr250_annotations",
    "@google_bazel_common//third_party/java/jsr330_inject",
    "//java/dagger:core",
    "//java/dagger/producers",
    "//java/dagger/model",
    "//java/dagger/spi",
    "//java/dagger/model:internal-proxies",
]

CODEGEN_DEPS = CODEGEN_SHARED_DEPS + [
    "@google_bazel_common//third_party/java/guava",
    ":shared-with-spi",
]

# Code that is shared with the dagger.model and dagger.spi packages. This code is merged into both
# the dagger-compiler and dagger-spi artifacts that are sent to Maven
java_library(
    name = "shared-with-spi",
    srcs = ["DaggerStreams.java"],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = [
        "@google_bazel_common//third_party/java/guava",
    ],
)

# Common types needed across all of the codegen package
java_library(
    name = "base",
    srcs = [
        "Accessibility.java",
        "AnnotationSpecs.java",
        "CodeBlocks.java",
        "CompilerOptions.java",
        "ContributionType.java",
        "DaggerElements.java",
        "DaggerGraphs.java",
        "DaggerTypes.java",
        "DiagnosticFormatting.java",
        "Expression.java",
        "FeatureStatus.java",
        "Formatter.java",
        "FrameworkTypes.java",
        "InjectionAnnotations.java",
        "Keys.java",
        "MapKeyAccessibility.java",
        "MapType.java",
        "MoreAnnotationMirrors.java",
        "MoreAnnotationValues.java",
        "MultibindingAnnotations.java",
        "OptionalType.java",
        "Optionals.java",
        "RequestKinds.java",
        "Scopes.java",
        "SetType.java",
        "SimpleAnnotationMirror.java",
        "SimpleTypeAnnotationValue.java",
        "SourceFileGenerationException.java",  # Used in :writing and :processor
        "SourceFileGenerator.java",  # Needed by InjectBindingRegistry in :binding and also :writing
        "TypeNames.java",
        "TypeSpecs.java",
        "UniqueNameSet.java",
        "Util.java",
        "ValidationType.java",
        "package-info.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS,
)

# Classes that help to build a model of the binding graph
java_library(
    name = "binding",
    srcs = [
        "AnnotationExpression.java",
        "Binding.java",
        "BindingDeclaration.java",
        "BindingFactory.java",
        "BindingGraph.java",
        "BindingNodeImpl.java",
        "BindingType.java",
        "BindingTypeMapper.java",
        "BindingVariableNamer.java",  # needed by FrameworkField
        "BindsTypeChecker.java",
        "ComponentDescriptor.java",
        "ComponentNodeImpl.java",
        "ComponentRequirement.java",
        "ComponentTreeTraverser.java",
        "ConfigurationAnnotations.java",  # Uses ModuleDescriptors
        "ContributionBinding.java",
        "DelegateDeclaration.java",
        "DependencyRequestFactory.java",
        "DependencyVariableNamer.java",  # Used by SourceFiles
        "ErrorMessages.java",  # Consider splitting this up as it pulls in too much
        "FrameworkDependency.java",
        "FrameworkField.java",  # Used by SourceFiles
        "FrameworkType.java",
        "InjectBindingRegistry.java",
        "KeyFactory.java",
        "MapKeys.java",
        "MembersInjectionBinding.java",
        "MethodSignature.java",
        "ModuleDescriptor.java",
        "MultibindingDeclaration.java",
        "OptionalBindingDeclaration.java",
        "ProductionBinding.java",
        "ProvisionBinding.java",
        "ResolvedBindings.java",
        "SourceFiles.java",  # Consider splitting this up?
        "SubcomponentDeclaration.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [":base"],
)

# Code related to validating the user-written Dagger code
java_library(
    name = "validation",
    srcs = [
        "AnyBindingMethodValidator.java",
        "BindingDeclarationFormatter.java",
        "BindingMethodProcessingStep.java",
        "BindingMethodValidator.java",
        "BindsInstanceProcessingStep.java",
        "BindsMethodValidator.java",
        "BindsOptionalOfMethodValidator.java",
        "BuilderValidator.java",
        "CanReleaseReferencesValidator.java",
        "ComponentDescriptorValidator.java",
        "ComponentHierarchyValidator.java",
        "ComponentValidator.java",
        "DependencyRequestFormatter.java",
        "ForReleasableReferencesValidator.java",
        "InjectValidator.java",
        "MapKeyValidator.java",
        "MethodSignatureFormatter.java",
        "ModuleValidator.java",
        "MultibindingAnnotationsProcessingStep.java",
        "MultibindsMethodValidator.java",
        "ProducesMethodValidator.java",
        "ProvidesMethodValidator.java",
        "ValidationReport.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [
        ":base",
        ":binding",
    ],
)

java_library(
    name = "binding_graph_validation",
    srcs = [
        "BindingCycleValidation.java",
        "BindingGraphValidationModule.java",
        "DependsOnProductionExecutorValidator.java",
        "DuplicateBindingsValidation.java",
        "IncompatiblyScopedBindingsValidation.java",
        "InjectBindingValidation.java",
        "MapMultibindingValidation.java",
        "MembersInjectionBindingValidation.java",
        "MissingBindingValidation.java",
        "NonNullableRequestForNullableBindingValidation.java",
        "ProvisionDependencyOnProducerBindingValidation.java",
        "SubcomponentFactoryMethodValidation.java",
        "Validation.java",
    ],
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [
        ":base",
        ":binding",
        ":validation",
    ],
)

# Classes that assemble the model of the generated code and write to the Filer
java_library(
    name = "writing",
    srcs = [
        "AnnotationCreatorGenerator.java",
        "BindingExpression.java",
        "BindingMethodImplementation.java",
        "ComponentBindingExpressions.java",
        "ComponentBuilder.java",
        "ComponentGenerator.java",
        "ComponentInstanceBindingExpression.java",
        "ComponentMethodBindingExpression.java",
        "ComponentModelBuilder.java",
        "ComponentProvisionBindingExpression.java",
        "ComponentRequirementBindingExpression.java",
        "ComponentRequirementField.java",
        "ComponentRequirementFields.java",
        "DelegateBindingExpression.java",
        "DelegatingFrameworkInstanceCreationExpression.java",
        "DependencyMethodProducerCreationExpression.java",
        "DependencyMethodProviderCreationExpression.java",
        "DerivedFromProviderBindingExpression.java",
        "DoubleCheckedMethodImplementation.java",
        "FactoryGenerator.java",
        "FrameworkFieldInitializer.java",
        "FrameworkInstanceBindingExpression.java",
        "FrameworkInstanceSupplier.java",
        "GeneratedComponentModel.java",
        "GwtCompatibility.java",
        "ImmediateFutureBindingExpression.java",
        "InaccessibleMapKeyProxyGenerator.java",
        "InjectionMethods.java",
        "InjectionOrProvisionProviderCreationExpression.java",
        "InnerSwitchingProviders.java",
        "InstanceFactoryCreationExpression.java",
        "MapBindingExpression.java",
        "MapFactoryCreationExpression.java",
        "MemberSelect.java",
        "MembersInjectionBindingExpression.java",
        "MembersInjectionMethods.java",
        "MembersInjectorGenerator.java",
        "MembersInjectorProviderCreationExpression.java",
        "MethodBindingExpression.java",
        "MonitoringModuleGenerator.java",
        "MonitoringModuleProcessingStep.java",
        "OptionalBindingExpression.java",
        "OptionalFactories.java",
        "OptionalFactoryInstanceCreationExpression.java",
        "PrivateMethodBindingExpression.java",
        "ProducerCreationExpression.java",
        "ProducerFactoryGenerator.java",
        "ProducerFromProviderCreationExpression.java",
        "ProductionExecutorModuleGenerator.java",
        "ReferenceReleasingManagerFields.java",
        "ReleasableReferenceManagerProviderCreationExpression.java",
        "ReleasableReferenceManagerSetProviderCreationExpression.java",
        "SetBindingExpression.java",
        "SetFactoryCreationExpression.java",
        "SimpleInvocationBindingExpression.java",
        "SimpleMethodBindingExpression.java",
        "SingleCheckedMethodImplementation.java",
        "StaticSwitchingProviders.java",
        "SubcomponentBuilderBindingExpression.java",
        "SubcomponentBuilderProviderCreationExpression.java",
        "SubcomponentNames.java",
        "SwitchingProviders.java",
        "UnwrappedMapKeyGenerator.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [
        ":base",
        ":binding",
    ],
)

# The processor's "main", if you will
java_library(
    name = "processor",
    srcs = [
        "BindingGraphConverter.java",
        "BindingGraphFactory.java",
        "BindingGraphPlugins.java",
        "BindingGraphPluginsModule.java",
        "BindingMethodValidatorsModule.java",
        "CanReleaseReferencesProcessingStep.java",
        "ComponentHjarProcessingStep.java",
        "ComponentProcessingStep.java",
        "ComponentProcessor.java",
        "DiagnosticReporterFactory.java",
        "InjectBindingRegistryImpl.java",
        "InjectProcessingStep.java",
        "MapKeyProcessingStep.java",
        "ModuleProcessingStep.java",
        "ProcessingEnvironmentModule.java",
        "ProcessingOptions.java",
        "ProductionExecutorModuleProcessingStep.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven_coordinates=com.google.dagger:dagger-compiler:" + POM_VERSION],
    deps = CODEGEN_DEPS + [
        ":base",
        ":binding",
        ":binding_graph_validation",
        ":writing",
        ":validation",
    ],
)

pom_file(
    name = "pom",
    artifact_id = "dagger-compiler",
    artifact_name = "Dagger Compiler",
    targets = [
        ":processor",
        ":base",
        ":binding",
        ":binding_graph_validation",
        ":writing",
        ":validation",
    ],
)

java_library(
    name = "kythe",
    srcs = KYTHE_SRCS,
    plugins = [":component-codegen"],
    deps = [
        ":base",
        ":binding",
        ":kythe_plugin",
        ":processor",
        ":validation",
        "//java/dagger:core",
        "//java/dagger/model",
        "//java/dagger/producers",
        "@bazel_tools//third_party/java/jdk/langtools:javac",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/guava",
    ],
)

# A _deploy.jar consisting of the java_librarys in https://github.com/google/kythe needed to build a
# Kythe plugin
# TODO(ronshapiro): replace this with a http_archive of the next release in
# https://github.com/google/kythe/releases
java_import(
    name = "kythe_plugin",
    jars = ["kythe_plugin_deploy.jar"],
    neverlink = 1,
)

java_import(
    name = "bootstrap_compiler",
    jars = ["bootstrap_compiler_deploy.jar"],
    visibility = ["//visibility:private"],
)

java_plugin(
    name = "bootstrap_compiler_plugin",
    generates_api = 1,
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    visibility = ["//visibility:private"],
    deps = [":bootstrap_compiler"],
)

load("@google_bazel_common//tools/javadoc:javadoc.bzl", "javadoc_library")

javadoc_library(
    name = "codegen-javadoc",
    srcs = CODEGEN_SRCS,
    root_packages = ["dagger.internal.codegen"],
    deps = [":processor"],
)

java_library(
    name = "check-package-javadoc",
    testonly = 1,
    srcs = CODEGEN_SRCS,
    javacopts = DOCLINT_HTML_AND_SYNTAX + DOCLINT_REFERENCES,
    plugins = CODEGEN_PLUGINS,
    deps = CODEGEN_DEPS,
)

java_plugin(
    name = "component-codegen",
    generates_api = 1,
    output_licenses = ["unencumbered"],
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    tags = [
        "annotation=dagger.Component;" +
        "genclass=${package}.Dagger${outerclasses}${classname}",
        "annotation=dagger.producers.ProductionComponent;" +
        "genclass=${package}.Dagger${outerclasses}${classname}",
    ],
    deps = [":processor"],
)

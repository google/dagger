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
load("//tools:maven.bzl", "POM_VERSION", "pom_file")

EXPERIMENTAL_VISUALIZER_SRCS = ["BindingNetworkVisualizer.java"]

JAVAC_PLUGIN_MODULE_SRCS = ["JavacPluginModule.java"]

KYTHE_SRCS = ["DaggerKythePlugin.java"]

STATISTICS_COLLECTOR_SRCS = ["BindingGraphStatisticsCollector.java"]

CODEGEN_SRCS = glob(
    ["*.java"],
    exclude = EXPERIMENTAL_VISUALIZER_SRCS + KYTHE_SRCS + STATISTICS_COLLECTOR_SRCS +
              JAVAC_PLUGIN_MODULE_SRCS,
)

CODEGEN_PLUGINS = [":bootstrap_compiler_plugin"]

CODEGEN_SHARED_DEPS = [
    "@google_bazel_common//third_party/java/auto:service",
    "@google_bazel_common//third_party/java/auto:value",
    "@google_bazel_common//third_party/java/auto:common",
    "@google_bazel_common//third_party/java/checker_framework_annotations",
    "@google_bazel_common//third_party/java/error_prone:annotations",
    "@google_bazel_common//third_party/java/google_java_format",
    "@google_bazel_common//third_party/java/javapoet",
    "@bazel_tools//tools/jdk:langtools-neverlink",
    "@google_bazel_common//third_party/java/jsr250_annotations",
    "@google_bazel_common//third_party/java/jsr330_inject",
    "//java/dagger:core",
    "//java/dagger/producers",
    "//java/dagger/model",
    "//java/dagger/spi",
    "//java/dagger/model:internal-proxies",
]

CODEGEN_DEPS = CODEGEN_SHARED_DEPS + [
    ":jdk-and-guava-extras",
    "@google_bazel_common//third_party/java/guava",
]

# Extra features for the JDK and Guava. This code is merged into both
# the dagger-compiler and dagger-spi artifacts that are sent to Maven
java_library(
    name = "jdk-and-guava-extras",
    srcs = [
        "DaggerGraphs.java",
        "DaggerStreams.java",
        "Optionals.java",
    ],
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
        "ClearableCache.java",
        "CompilerOptions.java",
        "ComponentAnnotation.java",
        "ContributionType.java",
        "DaggerStatistics.java",
        "DaggerStatisticsCollectingProcessingStep.java",
        "DaggerStatisticsCollector.java",
        "DaggerStatisticsRecorder.java",
        "DiagnosticFormatting.java",
        "ElementFormatter.java",
        "FeatureStatus.java",
        "Formatter.java",
        "ForwardingCompilerOptions.java",
        "FrameworkTypes.java",
        "InjectionAnnotations.java",
        "Keys.java",
        "MapKeyAccessibility.java",
        "MapType.java",
        "ModuleAnnotation.java",
        "MoreAnnotationMirrors.java",
        "MoreAnnotationValues.java",
        "MultibindingAnnotations.java",
        "OptionalType.java",
        "ProcessingEnvironmentCompilerOptions.java",
        "ProcessingOptions.java",
        "RequestKinds.java",
        "Scopes.java",
        "SetType.java",
        "SimpleAnnotationMirror.java",
        "SimpleTypeAnnotationValue.java",
        "SourceFileGenerationException.java",  # Used in :writing and :processor
        "SourceFileGenerator.java",  # Needed by InjectBindingRegistry in :binding and also :writing
        "TypeCheckingProcessingStep.java",
        "UniqueNameSet.java",
        "Util.java",
        "ValidationType.java",
        "package-info.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/langmodel",
    ],
)

# Classes that help to build a model of the binding graph
java_library(
    name = "binding",
    srcs = [
        "AnnotationExpression.java",
        "Binding.java",
        "BindingDeclaration.java",
        "BindingDeclarationFormatter.java",
        "BindingFactory.java",
        "BindingGraph.java",
        "BindingGraphConverter.java",
        "BindingGraphFactory.java",
        "BindingNode.java",
        "BindingRequest.java",
        "BindingType.java",
        "BindsTypeChecker.java",
        "ChildFactoryMethodEdgeImpl.java",
        "ComponentCreatorAnnotation.java",
        "ComponentCreatorDescriptor.java",
        "ComponentCreatorKind.java",
        "ComponentDescriptor.java",
        "ComponentDescriptorFactory.java",
        "ComponentKind.java",
        "ComponentNodeImpl.java",
        "ComponentRequirement.java",
        "ComponentTreeTraverser.java",
        "ConfigurationAnnotations.java",  # Uses ModuleDescriptors
        "ContributionBinding.java",
        "DelegateDeclaration.java",
        "DependencyEdgeImpl.java",
        "DependencyRequestFactory.java",
        "DependencyRequestFormatter.java",
        "DependencyVariableNamer.java",  # Used by SourceFiles
        "ErrorMessages.java",  # Consider splitting this up as it pulls in too much
        "FrameworkDependency.java",
        "FrameworkField.java",  # Used by SourceFiles
        "FrameworkType.java",
        "FrameworkTypeMapper.java",
        "InjectBindingRegistry.java",
        "InjectionSiteFactory.java",
        "KeyFactory.java",
        "KeyVariableNamer.java",  # needs ConfigurationAnnotations, SourceFiles
        "MapKeys.java",
        "MembersInjectionBinding.java",
        "MethodSignature.java",
        "MethodSignatureFormatter.java",
        "ModuleDescriptor.java",
        "ModuleKind.java",
        "MultibindingDeclaration.java",
        "OptionalBindingDeclaration.java",
        "ProductionBinding.java",
        "ProvisionBinding.java",
        "ResolvedBindings.java",
        "SourceFiles.java",  # Consider splitting this up?
        "SubcomponentCreatorBindingEdgeImpl.java",
        "SubcomponentDeclaration.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [
        ":base",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/javapoet",
    ],
)

# Code related to validating the user-written Dagger code
java_library(
    name = "validation",
    srcs = [
        "AnyBindingMethodValidator.java",
        "BindingElementValidator.java",
        "BindingGraphPlugins.java",
        "BindingGraphValidator.java",
        "BindingMethodProcessingStep.java",
        "BindingMethodValidator.java",
        "BindsInstanceElementValidator.java",
        "BindsInstanceMethodValidator.java",
        "BindsInstanceParameterValidator.java",
        "BindsInstanceProcessingStep.java",
        "BindsMethodValidator.java",
        "BindsOptionalOfMethodValidator.java",
        "ComponentCreatorValidator.java",
        "ComponentDescriptorValidator.java",
        "ComponentHierarchyValidator.java",
        "ComponentValidator.java",
        "DependencyRequestValidator.java",
        "DiagnosticReporterFactory.java",
        "InjectValidator.java",
        "MapKeyValidator.java",
        "MembersInjectionValidator.java",
        "ModuleValidator.java",
        "MultibindingAnnotationsProcessingStep.java",
        "MultibindsMethodValidator.java",
        "ProducesMethodValidator.java",
        "ProvidesMethodValidator.java",
        "Validation.java",
        "ValidationReport.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [
        ":base",
        ":binding",
        "//java/dagger/internal/codegen/langmodel",
    ],
)

java_library(
    name = "binding_graph_validation",
    srcs = [
        "DependencyCycleValidator.java",
        "DependsOnProductionExecutorValidator.java",
        "DuplicateBindingsValidator.java",
        "IncompatiblyScopedBindingsValidator.java",
        "InjectBindingValidator.java",
        "MapMultibindingValidator.java",
        "MissingBindingValidator.java",
        "NullableBindingValidator.java",
        "ProvisionDependencyOnProducerBindingValidator.java",
        "SubcomponentFactoryMethodValidator.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [
        ":base",
        ":binding",
        ":validation",
        "//java/dagger/internal/codegen/langmodel",
    ],
)

# Classes that assemble the model of the generated code and write to the Filer
java_library(
    name = "writing",
    srcs = [
        "AnnotationCreatorGenerator.java",
        "AnonymousProviderCreationExpression.java",
        "BindingExpression.java",
        "ComponentBindingExpressions.java",
        "ComponentCreatorImplementation.java",
        "ComponentImplementation.java",
        "ComponentInstanceBindingExpression.java",
        "ComponentMethodBindingExpression.java",
        "ComponentProvisionBindingExpression.java",
        "ComponentRequirementBindingExpression.java",
        "ComponentRequirementExpression.java",
        "ComponentRequirementExpressions.java",
        "DelegateBindingExpression.java",
        "DelegatingFrameworkInstanceCreationExpression.java",
        "DependencyMethodProducerCreationExpression.java",
        "DependencyMethodProviderCreationExpression.java",
        "DerivedFromFrameworkInstanceBindingExpression.java",
        "FactoryGenerator.java",
        "FrameworkFieldInitializer.java",
        "FrameworkInstanceBindingExpression.java",
        "FrameworkInstanceSupplier.java",
        "GwtCompatibility.java",
        "HjarSourceFileGenerator.java",
        "ImmediateFutureBindingExpression.java",
        "InaccessibleMapKeyProxyGenerator.java",
        "InjectionMethod.java",
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
        "ModuleConstructorProxyGenerator.java",
        "ModuleGenerator.java",
        "ModuleProxies.java",
        "MonitoringModuleGenerator.java",
        "MonitoringModuleProcessingStep.java",
        "MultibindingFactoryCreationExpression.java",
        "OptionalBindingExpression.java",
        "OptionalFactories.java",
        "OptionalFactoryInstanceCreationExpression.java",
        "ParentComponent.java",
        "PerComponentImplementation.java",
        "PerGeneratedFile.java",
        "PrivateMethodBindingExpression.java",
        "ProducerCreationExpression.java",
        "ProducerEntryPointView.java",
        "ProducerFactoryGenerator.java",
        "ProducerFromProviderCreationExpression.java",
        "ProducerNodeInstanceBindingExpression.java",
        "ProviderInstanceBindingExpression.java",
        "SetBindingExpression.java",
        "SetFactoryCreationExpression.java",
        "SimpleInvocationBindingExpression.java",
        "SimpleMethodBindingExpression.java",
        "SubcomponentCreatorBindingExpression.java",
        "SubcomponentNames.java",
        "SwitchingProviders.java",
        "TopLevel.java",
        "UnwrappedMapKeyGenerator.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven:merged"],
    deps = CODEGEN_DEPS + [
        ":base",
        ":binding",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/langmodel",
    ],
)

# The processor's "main", if you will
java_library(
    name = "processor",
    srcs = [
        "BindingGraphValidationModule.java",
        "BindingMethodValidatorsModule.java",
        "ComponentCreatorImplementationFactory.java",
        "ComponentGenerator.java",
        "ComponentHjarProcessingStep.java",
        "ComponentImplementationBuilder.java",
        "ComponentImplementationFactory.java",
        "ComponentProcessingStep.java",
        "ComponentProcessor.java",
        "CurrentImplementationSubcomponent.java",
        "InjectBindingRegistryImpl.java",
        "InjectBindingRegistryModule.java",
        "InjectProcessingStep.java",
        "MapKeyProcessingStep.java",
        "ModuleProcessingStep.java",
        "ProcessingEnvironmentModule.java",
        "ProcessingRoundCacheModule.java",
        "SourceFileGeneratorsModule.java",
        "SpiModule.java",
        "SystemComponentsModule.java",
        "TopLevelImplementationComponent.java",
    ],
    plugins = CODEGEN_PLUGINS,
    tags = ["maven_coordinates=com.google.dagger:dagger-compiler:" + POM_VERSION],
    deps = CODEGEN_DEPS + [
        ":base",
        ":binding",
        ":binding_graph_validation",
        ":writing",
        ":validation",
        "//java/dagger/internal/codegen/javapoet",
        "//java/dagger/internal/codegen/langmodel",
        "@google_bazel_common//third_party/java/incap",
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
        "//java/dagger/internal/codegen/javapoet",
    ],
)

java_library(
    name = "javac-plugin-module",
    srcs = JAVAC_PLUGIN_MODULE_SRCS,
    plugins = [":component-codegen"],
    visibility = ["//visibility:private"],
    deps = [
        ":base",
        ":javac",
        ":processor",
        "//java/dagger:core",
        "//java/dagger/internal/codegen/langmodel",
    ],
)

java_library(
    name = "kythe",
    srcs = KYTHE_SRCS,
    plugins = [":component-codegen"],
    deps = [
        ":base",
        ":binding",
        ":javac",
        ":javac-plugin-module",
        ":kythe_plugin",
        ":processor",
        "//java/dagger:core",
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/model",
        "//java/dagger/producers",
        "@google_bazel_common//third_party/java/auto:common",
        "@google_bazel_common//third_party/java/auto:service",
        "@google_bazel_common//third_party/java/guava",
    ],
)

# Replacement for @bazel_tools//third_party/java/jdk/langtools:javac, which seems to have gone away?
java_import(
    name = "javac",
    jars = ["@bazel_tools//third_party/java/jdk/langtools:javac_jar"],
)

# A _deploy.jar consisting of the java_librarys in https://github.com/kythe/kythe needed to build a
# Kythe plugin
# TODO(ronshapiro): replace this with a http_archive of the next release in
# https://github.com/kythe/kythe/releases
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
    deps = CODEGEN_DEPS + [
        "//java/dagger/internal/codegen/langmodel",
        "//java/dagger/internal/codegen/javapoet",
        "@google_bazel_common//third_party/java/incap",
    ],
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

java_library(
    name = "statistics",
    srcs = STATISTICS_COLLECTOR_SRCS,
    plugins = [":component-codegen"],
    deps = [
        ":base",
        ":binding",
        ":javac",
        ":javac-plugin-module",
        ":processor",
        "//java/dagger:core",
        "//java/dagger/model",
        "@google_bazel_common//third_party/java/error_prone:check_api",
    ],
)

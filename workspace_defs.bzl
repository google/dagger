# Copyright (C) 2020 The Google Bazel Common Authors.
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

"""A macro to configure Dagger deps within a workspace"""

load("//:build_defs.bzl", "POM_VERSION")

# For tagged releases, the POM_VERSION will be set to the version of the release.
# However, for CI testing the POM_VERSION will not be set, so we use the
# HEAD-SNAPSHOT artifacts instead.
# TODO(bcorso): Ideally, we would use the LOCAL-SNAPSHOT artifacts for CI testing;
# however, maven_install doesn't work with local maven repositories
# (See issue: https://github.com/bazelbuild/rules_jvm_external/issues/305).
_VERSION = POM_VERSION if POM_VERSION != "${project.version}" else "HEAD-SNAPSHOT"

DAGGER_ARTIFACTS = [
    "com.google.dagger:dagger:" + _VERSION,
    "com.google.dagger:dagger-compiler:" + _VERSION,
    "com.google.dagger:dagger-producers:" + _VERSION,
    "com.google.dagger:dagger-spi:" + _VERSION,
]

DAGGER_ANDROID_ARTIFACTS = [
    "com.google.dagger:dagger-android-processor:" + _VERSION,
    "com.google.dagger:dagger-android-support:" + _VERSION,
    "com.google.dagger:dagger-android:" + _VERSION,
]

HILT_ANDROID_ARTIFACTS = [
    "androidx.test:core:1.1.0",  # Export for ApplicationProvider
    "javax.annotation:javax.annotation-api:1.3.2",  # Export for @Generated
    "androidx.annotation:annotation:1.1.0",  # Export for @CallSuper/@Nullable
    "com.google.dagger:dagger:" + _VERSION,
    "com.google.dagger:dagger-compiler:" + _VERSION,
    "com.google.dagger:hilt-android:" + _VERSION,
    "com.google.dagger:hilt-android-testing:" + _VERSION,
    "com.google.dagger:hilt-android-compiler:" + _VERSION,
    "com.google.dagger:hilt-core:" + _VERSION,
]

DAGGER_REPOSITORIES = [
    "https://maven.google.com",
    "https://repo1.maven.org/maven2",
    "https://oss.sonatype.org/content/repositories/snapshots",
]

DAGGER_ANDROID_REPOSITORIES = DAGGER_REPOSITORIES

HILT_ANDROID_REPOSITORIES = DAGGER_REPOSITORIES

# https://github.com/bazelbuild/buildtools/blob/master/WARNINGS.md#unnamed-macro
# buildifier: disable=unnamed-macro
def dagger_rules(repo_name = "@maven"):
    """Defines the Dagger targets with proper exported dependencies and plugins.

    The targets will be of the form ":<artifact-id>".

    Args:
      repo_name: The name of the dependency repository (default is "@maven").
    """
    native.java_library(
        name = "dagger",
        exported_plugins = [":dagger-compiler"],
        visibility = ["//visibility:public"],
        exports = [
            "%s//:com_google_dagger_dagger" % repo_name,
            "%s//:javax_inject_javax_inject" % repo_name,
        ],
    )

    native.java_plugin(
        name = "dagger-compiler",
        generates_api = 1,
        processor_class = "dagger.internal.codegen.ComponentProcessor",
        deps = [
            "%s//:com_google_dagger_dagger_compiler" % repo_name,
        ],
    )

    native.java_library(
        name = "dagger-producers",
        visibility = ["//visibility:public"],
        exports = [
            ":dagger",
            "%s//:com_google_dagger_dagger_producers" % repo_name,
        ],
    )

    native.java_library(
        name = "dagger-spi",
        visibility = ["//visibility:public"],
        exports = [
            "%s//:com_google_dagger_dagger_spi" % repo_name,
        ],
    )

# https://github.com/bazelbuild/buildtools/blob/master/WARNINGS.md#unnamed-macro
# buildifier: disable=unnamed-macro
def dagger_android_rules(repo_name = "@maven"):
    """Defines the Dagger Android targets with proper exported dependencies and plugins.

    The targets will be of the form ":<artifact-id>".

    Args:
      repo_name: The name of the dependency repository (default is "@maven").
    """

    # https://github.com/bazelbuild/buildtools/blob/master/WARNINGS.md#native-android
    # buildifier: disable=native-android
    native.android_library(
        name = "dagger-android",
        exported_plugins = [":dagger-android-processor"],
        visibility = ["//visibility:public"],
        exports = [
            "%s//:com_google_dagger_dagger_android" % repo_name,
        ],
    )

    # https://github.com/bazelbuild/buildtools/blob/master/WARNINGS.md#native-android
    # buildifier: disable=native-android
    native.android_library(
        name = "dagger-android-support",
        exported_plugins = [":dagger-android-processor"],
        visibility = ["//visibility:public"],
        exports = [
            ":dagger-android",
            "%s//:com_google_dagger_dagger_android_support" % repo_name,
        ],
    )

    native.java_plugin(
        name = "dagger-android-processor",
        generates_api = 1,
        processor_class = "dagger.android.processor.AndroidProcessor",
        deps = [
            "%s//:com_google_dagger_dagger_android_processor" % repo_name,
        ],
    )

# https://github.com/bazelbuild/buildtools/blob/master/WARNINGS.md#unnamed-macro
# buildifier: disable=unnamed-macro
def hilt_android_rules(repo_name = "@maven"):
    """Defines the Hilt Android targets with proper exported dependencies and plugins.

    The targets will be of the form ":<artifact-id>".

    Args:
      repo_name: The name of the dependency repository (default is "@maven").
    """

    # https://github.com/bazelbuild/buildtools/blob/master/WARNINGS.md#native-android
    # buildifier: disable=native-android
    native.android_library(
        name = "hilt-android",
        exported_plugins = [
            ":hilt_dagger_compiler",
            ":hilt_android_entry_point_processor",
            ":hilt_aggregated_deps_processor",
            ":hilt_alias_of_processor",
            ":hilt_define_component_processor",
            ":hilt_early_entry_points_processor",
            ":hilt_generates_root_input_processor",
            ":hilt_originating_element_processor",
            ":hilt_root_processor",
            ":hilt_component_tree_deps_processor",
            ":hilt_view_model_processor",
        ],
        visibility = ["//visibility:public"],
        exports = [
            "%s//:com_google_dagger_dagger" % repo_name,  # For Dagger APIs
            "%s//:javax_inject_javax_inject" % repo_name,  # For @Inject
            "%s//:androidx_annotation_annotation" % repo_name,  # For @CallSuper
            "%s//:com_google_dagger_hilt_android" % repo_name,
            "%s//:com_google_dagger_hilt_core" % repo_name,
            "%s//:javax_annotation_javax_annotation_api" % repo_name,  # For @Generated
        ],
    )

    # This target is same as dagger-compiler, but we're redefining it here
    # so that users don't have to call dagger_rules() first.
    native.java_plugin(
        name = "hilt_dagger_compiler",
        generates_api = 1,
        processor_class = "dagger.internal.codegen.ComponentProcessor",
        deps = [
            "%s//:com_google_dagger_dagger_compiler" % repo_name,
        ],
    )

    native.java_plugin(
        name = "hilt_android_entry_point_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.android.processor.internal.androidentrypoint.AndroidEntryPointProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_aggregated_deps_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.aggregateddeps.AggregatedDepsProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_alias_of_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.aliasof.AliasOfProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_define_component_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.definecomponent.DefineComponentProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_early_entry_points_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.earlyentrypoint.EarlyEntryPointProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_generates_root_input_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.generatesrootinput.GeneratesRootInputProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_originating_element_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.originatingelement.OriginatingElementProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_root_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.root.RootProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_component_tree_deps_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.root.ComponentTreeDepsProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_view_model_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.android.processor.internal.viewmodel.ViewModelProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    # https://github.com/bazelbuild/buildtools/blob/master/WARNINGS.md#native-android
    # buildifier: disable=native-android
    native.android_library(
        name = "hilt-android-testing",
        testonly = 1,
        exported_plugins = [
            ":hilt_bind_value_processor",
            ":hilt_custom_test_application_processor",
            ":hilt_uninstall_modules_processor",
        ],
        visibility = ["//visibility:public"],
        exports = [
            ":hilt-android",
            "%s//:androidx_test_core" % repo_name,  # For ApplicationProvider
            "%s//:com_google_dagger_hilt_android_testing" % repo_name,
        ],
    )

    native.java_plugin(
        name = "hilt_bind_value_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.android.processor.internal.bindvalue.BindValueProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_custom_test_application_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.android.processor.internal.customtestapplication.CustomTestApplicationProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

    native.java_plugin(
        name = "hilt_uninstall_modules_processor",
        generates_api = 1,
        processor_class = "dagger.hilt.processor.internal.uninstallmodules.UninstallModulesProcessor",
        deps = ["%s//:com_google_dagger_hilt_android_compiler" % repo_name],
    )

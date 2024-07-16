# Copyright (C) 2024 The Dagger Authors.
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

# Starlark rules for using jarjar

load("@rules_java//java:defs.bzl", "java_binary")

package(default_visibility = ["//:src"])

java_binary(
    name = "jarjar",
    main_class = "org.pantsbuild.jarjar.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//third_party/java/asm",
        "//third_party/java/asm:asm-commons",
        "//third_party/java/asm:asm-tree",
        "//third_party/java/jsr250_annotations",
        "//third_party/java/jsr330_inject",
        "@maven//:javax_enterprise_cdi_api",
        "@maven//:org_apache_ant_ant",
        "@maven//:org_apache_ant_ant_launcher",
        "@maven//:org_apache_maven_maven_artifact",
        "@maven//:org_apache_maven_maven_model",
        "@maven//:org_apache_maven_maven_plugin_api",
        "@maven//:org_codehaus_plexus_plexus_classworlds",
        "@maven//:org_codehaus_plexus_plexus_component_annotations",
        "@maven//:org_codehaus_plexus_plexus_utils",
        "@maven//:org_eclipse_sisu_org_eclipse_sisu_inject",
        "@maven//:org_eclipse_sisu_org_eclipse_sisu_plexus",
        "@maven//:org_pantsbuild_jarjar",
    ],
)

sh_binary(
    name = "jarjar_runner",
    srcs = ["jarjar_runner.sh"],
    visibility = ["//visibility:public"],
)

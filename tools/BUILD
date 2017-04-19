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
#   Tools for Dagger

package(default_visibility = ["//:src"])

sh_binary(
    name = "jarjar_library_impl",
    srcs = ["jarjar_library_impl.sh"],
)

sh_test(
    name = "jarjar_library_impl_test",
    srcs = ["jarjar_library_impl_test.sh"],
    args = ["$(JAVABASE)"],
    data = [
        ":jarjar_deploy.jar",
        ":jarjar_library_impl",
        "//tools/testdata:bar",
        "//tools/testdata:bar_with_dupe_file",
        "//tools/testdata:foo",
        "//tools/testdata:foo_with_dupe_file",
        "@local_jdk//:bin/jar",
        "@local_jdk//:bin/java",
        "@local_jdk//:jre",
    ],
)

java_binary(
    name = "jarjar",
    main_class = "org.pantsbuild.jarjar.Main",
    runtime_deps = [
        "@javax_annotation_jsr250_api//jar",
        "@javax_enterprise_cdi_api//jar",
        "@javax_inject_javax_inject//jar",
        "@org_apache_ant_ant//jar",
        "@org_apache_ant_ant_launcher//jar",
        "@org_apache_maven_maven_artifact//jar",
        "@org_apache_maven_maven_model//jar",
        "@org_apache_maven_maven_plugin_api//jar",
        "@org_codehaus_plexus_plexus_classworlds//jar",
        "@org_codehaus_plexus_plexus_component_annotations//jar",
        "@org_codehaus_plexus_plexus_utils//jar",
        "@org_eclipse_sisu_org_eclipse_sisu_inject//jar",
        "@org_eclipse_sisu_org_eclipse_sisu_plexus//jar",
        "@org_ow2_asm_asm//jar",
        "@org_ow2_asm_asm_commons//jar",
        "@org_ow2_asm_asm_tree//jar",
        "@org_pantsbuild_jarjar//jar",
    ],
)

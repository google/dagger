# Copyright (C) 2021 The Dagger Authors.
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
#   A library containing intermediate representations of the various Hilt
#   aggregating annotations along with logic to process them.

load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_visibility = ["//:src"])

kt_jvm_library(
    name = "ir",
    srcs = glob(["*.kt"]),
    # Dependencies here should be kept to a minimum since this library is
    # shadowed into the Hilt Gradle Plugin artifact.
    deps = [
        "//third_party/java/javapoet",
    ],
)

# Current `kt_jvm_library` does not output source jars and gen_maven_artifact expects one.
# See: https://github.com/bazelbuild/rules_kotlin/issues/324
genrule(
    name = "ir-sources",
    srcs = glob(["*.kt"]),
    outs = ["libir-src.jar"],
    cmd = """
        TEMP="$$(mktemp -d)"
        for file in $(SRCS); do
            filename="$$TEMP/$${file#java/}"
            mkdir -p `dirname $$filename` && cp $$file $$filename
        done
        jar cf $@ -C $$TEMP .
    """,
)

filegroup(
    name = "srcs_filegroup",
    srcs = glob(["*"]),
)

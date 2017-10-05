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

# Defines a set of build variants and the list of extra javacopts to build with.
# The key will be appended to the generated test names to ensure uniqueness.
BUILD_VARIANTS = {
    "ExperimentalAndroidMode": ["-Adagger.experimentalAndroidMode=enabled"],
}

# TODO(user): split into two functions for functional vs non-functional tests?
def GenJavaTests(name, srcs, deps, test_only_deps=None, plugins=None, javacopts=None,
                 lib_javacopts=None, test_javacopts=None, functional=True):
  _GenTests(native.java_library, native.java_test, name, srcs, deps, test_only_deps,
            plugins, javacopts, lib_javacopts, test_javacopts, functional)

def GenRobolectricTests(name, srcs, deps, test_only_deps=None, plugins=None, javacopts=None,
                        lib_javacopts=None, test_javacopts=None):
  # TODO(ronshapiro): enable these when Bazel supports robolectric tests
  pass

def _GenTests(library_rule_type, test_rule_type, name, srcs, deps, test_only_deps=None,
              plugins=None, javacopts=None, lib_javacopts=None, test_javacopts=None,
              functional=True):
  _gen_tests(library_rule_type, test_rule_type, name, srcs, deps, test_only_deps,
             plugins, javacopts, lib_javacopts, test_javacopts)

  if functional:
    for (variant_name, extra_javacopts) in BUILD_VARIANTS.items():
      _gen_tests(library_rule_type, test_rule_type, name, srcs, deps, test_only_deps,
                 plugins, javacopts, lib_javacopts, test_javacopts, variant_name,
                 extra_javacopts)

def _gen_tests(library_rule_type, test_rule_type, name, srcs, deps, test_only_deps,
               plugins, javacopts, lib_javacopts, test_javacopts, variant_name=None,
               extra_javacopts=None):
  if variant_name:
    suffix = "_" + variant_name
    tags = [variant_name]
    # Add jvm_flags so that the mode can be accessed from within tests.
    jvm_flags = ["-Ddagger.mode=" + variant_name]
  else:
    suffix = ""
    tags = []
    jvm_flags = []

  test_files = []
  supporting_files = []

  for src in srcs:
    if src.endswith("Test.java"):
      test_files.append(src)
    else:
      supporting_files.append(src)

  if not test_only_deps:
    test_only_deps = []

  if not extra_javacopts:
    extra_javacopts = []

  test_deps = test_only_deps + deps
  if supporting_files:
    supporting_files_name = name + suffix + "_lib"
    test_deps.append(":" + supporting_files_name)
    library_rule_type(
        name = supporting_files_name,
        deps = deps,
        srcs = supporting_files,
        plugins = plugins,
        javacopts = extra_javacopts + (javacopts or []) + (lib_javacopts or []),
        tags = tags,
        testonly = 1,
    )

  for test_file in test_files:
    test_name = test_file.replace(".java", "")
    prefix_path = "src/test/java/"
    if PACKAGE_NAME.find("javatests/") != -1:
      prefix_path = "javatests/"
    test_class = (PACKAGE_NAME + "/" + test_name).rpartition(prefix_path)[2].replace("/",".")
    test_rule_type(
        name = test_name + suffix,
        deps = test_deps,
        srcs = [test_file],
        plugins = plugins,
        javacopts = extra_javacopts + (javacopts or []) + (test_javacopts or []),
        jvm_flags = jvm_flags,
        tags = tags,
        test_class = test_class,
    )

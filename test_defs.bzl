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

"""This file defines constants useful across the Dagger tests."""

load("@rules_java//java:defs.bzl", "java_library", "java_test")
load("//:build_defs.bzl", "JAVA_RELEASE_MIN")
load(
    "@io_bazel_rules_kotlin//kotlin:jvm.bzl",
    "kt_jvm_library",
    "kt_jvm_test",
)

# Defines a set of build variants and the list of extra javacopts to build with.
# The key will be appended to the generated test names to ensure uniqueness.
_NON_FUNCTIONAL_BUILD_VARIANTS = {None: []}
_FUNCTIONAL_BUILD_VARIANTS = {
    None: [],  # The default build variant (no javacopts).
    "Shards": ["-Adagger.keysPerComponentShard=2"],
    "FastInit": ["-Adagger.fastInit=enabled"],
    "FastInit_Shards": ["-Adagger.fastInit=enabled", "-Adagger.keysPerComponentShard=2"],
}

def GenKtLibrary(
        name,
        srcs,
        deps = None,
        gen_library_deps = None,
        plugins = None,
        javacopts = None,
        functional = True,
        require_jdk7_syntax = True):
    _GenTestsWithVariants(
        library_rule_type = kt_jvm_library,
        test_rule_type = None,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = gen_library_deps,
        test_only_deps = None,
        shard_count = None,
        plugins = plugins,
        javacopts = javacopts,
        functional = functional,
        require_jdk7_syntax = require_jdk7_syntax,
    )

def GenKtTests(
        name,
        srcs,
        deps = None,
        gen_library_deps = None,
        test_only_deps = None,
        plugins = None,
        javacopts = None,
        shard_count = None,
        functional = True,
        require_jdk7_syntax = True):
    _GenTestsWithVariants(
        library_rule_type = kt_jvm_library,
        test_rule_type = kt_jvm_test,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = gen_library_deps,
        test_only_deps = test_only_deps,
        plugins = plugins,
        javacopts = javacopts,
        shard_count = shard_count,
        functional = functional,
        require_jdk7_syntax = require_jdk7_syntax,
    )

def GenJavaLibrary(
        name,
        srcs,
        deps = None,
        gen_library_deps = None,
        plugins = None,
        javacopts = None,
        functional = True,
        require_jdk7_syntax = True):
    if any([src for src in srcs if src.endswith(".kt")]):
        fail("GenJavaLibrary ':{0}' should not contain kotlin sources.".format(name))
    _GenTestsWithVariants(
        library_rule_type = java_library,
        test_rule_type = None,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = gen_library_deps,
        test_only_deps = None,
        plugins = plugins,
        javacopts = javacopts,
        shard_count = None,
        functional = functional,
        require_jdk7_syntax = require_jdk7_syntax,
    )

def GenJavaTests(
        name,
        srcs,
        deps = None,
        gen_library_deps = None,
        test_only_deps = None,
        plugins = None,
        javacopts = None,
        shard_count = None,
        functional = True,
        require_jdk7_syntax = True):
    if any([src for src in srcs if src.endswith(".kt")]):
        fail("GenJavaTests ':{0}' should not contain kotlin sources.".format(name))
    _GenTestsWithVariants(
        library_rule_type = java_library,
        test_rule_type = java_test,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = gen_library_deps,
        test_only_deps = test_only_deps,
        plugins = plugins,
        javacopts = javacopts,
        shard_count = shard_count,
        functional = functional,
        require_jdk7_syntax = require_jdk7_syntax,
    )

def GenRobolectricTests(
        name,
        srcs,
        deps = None,
        test_only_deps = None,
        plugins = None,
        javacopts = None,
        shard_count = None,
        functional = True,
        require_jdk7_syntax = True,
        manifest_values = None):
    deps = (deps or []) + ["//:android_local_test_exports"]
    _GenTestsWithVariants(
        library_rule_type = native.android_library,
        test_rule_type = native.android_local_test,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = None,
        test_only_deps = test_only_deps,
        plugins = plugins,
        javacopts = javacopts,
        shard_count = shard_count,
        functional = functional,
        require_jdk7_syntax = require_jdk7_syntax,
        test_kwargs = {"manifest_values": manifest_values},
    )

def _GenTestsWithVariants(
        library_rule_type,
        test_rule_type,
        name,
        srcs,
        deps,
        gen_library_deps,
        test_only_deps,
        plugins,
        javacopts,
        shard_count,
        functional,
        require_jdk7_syntax,
        test_kwargs = None):
    test_files = [src for src in srcs if _is_test(src)]
    supporting_files = [src for src in srcs if not _is_test(src)]

    if test_rule_type and not test_files:
        fail("':{0}' should contain at least 1 test source.".format(name))

    if not test_rule_type and test_files:
        fail("':{0}' should not contain any test sources.".format(name))

    if test_kwargs == None:
        test_kwargs = {}

    if deps == None:
        deps = []

    if gen_library_deps == None:
        gen_library_deps = []

    if test_only_deps == None:
        test_only_deps = []

    if plugins == None:
        plugins = []

    if javacopts == None:
        javacopts = []

    build_variants = _FUNCTIONAL_BUILD_VARIANTS if functional else _NON_FUNCTIONAL_BUILD_VARIANTS
    for (variant_name, variant_javacopts) in build_variants.items():
        for is_ksp in (True, False):
            if variant_name:
                suffix = "_" + variant_name
                tags = [variant_name]

                # Add jvm_flags so that the mode can be accessed from within tests.
                jvm_flags = ["-Ddagger.mode=" + variant_name]
            else:
                suffix = ""
                tags = []
                jvm_flags = []

            if is_ksp:
                continue # KSP not yet supported in Bazel

            variant_deps = [canonical_dep_name(dep) + suffix for dep in gen_library_deps]
            test_deps = deps + test_only_deps
            if supporting_files:
                supporting_files_name = name + suffix + ("_lib" if test_files else "")
                _GenLibraryWithVariant(
                    library_rule_type = library_rule_type,
                    name = supporting_files_name,
                    srcs = supporting_files,
                    tags = tags,
                    deps = deps + variant_deps,
                    plugins = plugins,
                    javacopts = javacopts + variant_javacopts,
                    functional = functional,
                    require_jdk7_syntax = require_jdk7_syntax,
                )
                test_deps.append(supporting_files_name)

            for test_file in test_files:
                test_name = test_file.rsplit(".", 1)[0]
                _GenTestWithVariant(
                    library_rule_type = library_rule_type,
                    test_rule_type = test_rule_type,
                    name = test_name + suffix,
                    srcs = [test_file],
                    tags = tags,
                    deps = test_deps + variant_deps,
                    plugins = plugins,
                    javacopts = javacopts + variant_javacopts,
                    shard_count = shard_count,
                    jvm_flags = jvm_flags,
                    functional = functional,
                    test_kwargs = test_kwargs,
                )

def _GenLibraryWithVariant(
        library_rule_type,
        name,
        srcs,
        tags,
        deps,
        plugins,
        javacopts,
        functional,
        require_jdk7_syntax):
    if functional and require_jdk7_syntax:
        # TODO(b/261894425): Decide if we still want to apply JAVA_RELEASE_MIN by default.
        # Note: Technically, we should also apply JAVA_RELEASE_MIN to tests too, since we have
        # Dagger code in there as well, but we keep it only on libraries for legacy reasons, and
        # fixing tests to be jdk7 compatible would require a bit of work. We should decide on
        # b/261894425 before committing to that work.
        library_javacopts_kwargs = {"javacopts": javacopts + JAVA_RELEASE_MIN}
    else:
        library_javacopts_kwargs = {"javacopts": javacopts}

    # TODO(bcorso): Add javacopts explicitly once kt_jvm_test supports them.
    if library_rule_type in [kt_jvm_library]:
       library_javacopts_kwargs = {}
    library_rule_type(
        name = name,
        testonly = 1,
        srcs = srcs,
        plugins = plugins,
        tags = tags,
        deps = deps,
        **library_javacopts_kwargs
    )
    if functional and _is_hjar_test_supported(library_rule_type):
        _hjar_test(name, tags)

def _GenTestWithVariant(
        library_rule_type,
        test_rule_type,
        name,
        srcs,
        tags,
        deps,
        plugins,
        javacopts,
        shard_count,
        jvm_flags,
        functional,
        test_kwargs):
    test_files = [src for src in srcs if _is_test(src)]
    if len(test_files) != 1:
        fail("Expected 1 test source but found multiples: {0}".format(test_files))

    should_add_goldens = not functional and (test_rule_type == java_test)
    test_name = test_files[0].rsplit(".", 1)[0]
    prefix_path = "src/test/java/"
    package_name = native.package_name()
    if package_name.find("javatests/") != -1:
        prefix_path = "javatests/"
    if should_add_goldens:
        test_kwargs["resources"] = native.glob(["goldens/%s_*" % test_name])
    test_class = (package_name + "/" + test_name).rpartition(prefix_path)[2].replace("/", ".")
    test_kwargs_with_javacopts = {"javacopts": javacopts}

    # TODO(bcorso): Add javacopts explicitly once kt_jvm_test supports them.
    if test_rule_type == kt_jvm_test:
       test_kwargs_with_javacopts = {}
    test_kwargs_with_javacopts.update(test_kwargs)
    test_rule_type(
        name = name,
        srcs = srcs,
        jvm_flags = jvm_flags,
        plugins = plugins,
        tags = tags,
        shard_count = shard_count,
        test_class = test_class,
        deps = deps,
        **test_kwargs_with_javacopts
    )

def _is_hjar_test_supported(bazel_rule):
    return bazel_rule not in (
        kt_jvm_library,
        kt_jvm_test,
        native.android_library,
        native.android_local_test,
    )

def _hjar_test(name, tags):
    pass

def _is_test(src):
    return src.endswith("Test.java") or src.endswith("Test.kt")

def canonical_dep_name(dep):
    if dep.startswith(":"):
        dep = "//" + native.package_name() + dep
    dep_label = Label(dep)
    return "//" + dep_label.package + ":" + dep_label.name

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
load("//:build_defs.bzl", "JAVA_RELEASE_MIN", "TEST_MANIFEST_VALUES")
load("@rules_android//rules:rules.bzl", "android_library", "android_local_test")
load(
    "@io_bazel_rules_kotlin//kotlin:jvm.bzl",
    "kt_jvm_library",
    "kt_jvm_test",
)

_JAVACOPTS = {
    "Shards": "-Adagger.keysPerComponentShard=2",
    "FastInit": "-Adagger.fastInit=enabled",
    "Javac": "-Adagger.useKspInFunctionalTests=disabled",
    "JavaCodegen": "-Adagger.useKotlinCodegen=disabled",
}

_VARIANTS = [
    # Javac with Java codegen
    struct(backend = "Javac", codegen = "JavaCodegen", flavors = []),
    struct(backend = "Javac", codegen = "JavaCodegen", flavors = ["Shards"]),
    struct(backend = "Javac", codegen = "JavaCodegen", flavors = ["FastInit"]),
    struct(backend = "Javac", codegen = "JavaCodegen", flavors = ["FastInit", "Shards"]),
]

def GenCompilerTests(name, srcs, **kwargs):
    """Generates a java_test or kt_jvm_test for each test source in srcs.

    In addition, this macro will append any golden files associated with the test in the form
    'goldens/{test_name}/**' to the resources attribute of the generated test rule.

    Args:
        name: name of the target
        srcs: list of test sources.
        **kwargs: additional arguments to pass to each test rule.
    """
    non_test_srcs = [src for src in srcs if not _is_test(src)]
    if non_test_srcs:
        fail("GenCompilerTests should only contain test sources. Found: {0}".format(non_test_srcs))
    if not srcs:
        fail("':{0}' should contain at least 1 test source.".format(name))
    for src in srcs:
        test_name = src.rsplit(".", 1)[0]
        test_rule_type = kt_jvm_test if src.endswith(".kt") else java_test
        test_rule_type(
            name = test_name,
            srcs = [src],
            resources = native.glob(["goldens/%s/**" % test_name]),
            **kwargs
        )

def GenKtLibrary(
        name,
        srcs,
        deps = None,
        gen_library_deps = None,
        plugins = None,
        javacopts = None):
    _GenTestsWithVariants(
        library_rule_type = kt_jvm_library,
        test_rule_type = None,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = gen_library_deps,
        shard_count = None,
        plugins = plugins,
        javacopts = javacopts,
    )

def GenKtTests(
        name,
        srcs,
        deps = None,
        gen_library_deps = None,
        plugins = None,
        javacopts = None,
        shard_count = None):
    _GenTestsWithVariants(
        library_rule_type = kt_jvm_library,
        test_rule_type = kt_jvm_test,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = gen_library_deps,
        plugins = plugins,
        javacopts = javacopts,
        shard_count = shard_count,
    )

def GenJavaLibrary(
        name,
        srcs,
        deps = None,
        gen_library_deps = None,
        plugins = None,
        javacopts = None):
    if any([src for src in srcs if src.endswith(".kt")]):
        fail("GenJavaLibrary ':{0}' should not contain kotlin sources.".format(name))
    _GenTestsWithVariants(
        library_rule_type = java_library,
        test_rule_type = None,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = gen_library_deps,
        plugins = plugins,
        javacopts = javacopts,
        shard_count = None,
    )

def GenJavaTests(
        name,
        srcs,
        deps = None,
        gen_library_deps = None,
        plugins = None,
        javacopts = None,
        shard_count = None):
    if any([src for src in srcs if src.endswith(".kt")]):
        fail("GenJavaTests ':{0}' should not contain kotlin sources.".format(name))
    _GenTestsWithVariants(
        library_rule_type = java_library,
        test_rule_type = java_test,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = gen_library_deps,
        plugins = plugins,
        javacopts = javacopts,
        shard_count = shard_count,
    )

def GenRobolectricTests(
        name,
        srcs,
        deps = None,
        plugins = None,
        javacopts = None,
        shard_count = None,
        manifest_values = TEST_MANIFEST_VALUES):
    deps = (deps or []) + ["//:android_local_test_exports"]
    _GenTestsWithVariants(
        library_rule_type = android_library,
        test_rule_type = android_local_test,
        name = name,
        srcs = srcs,
        deps = deps,
        gen_library_deps = None,
        plugins = plugins,
        javacopts = javacopts,
        shard_count = shard_count,
        test_kwargs = {"manifest_values": manifest_values},
    )

def _GenTestsWithVariants(
        library_rule_type,
        test_rule_type,
        name,
        srcs,
        deps,
        gen_library_deps,
        plugins,
        javacopts,
        shard_count,
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

    if plugins == None:
        plugins = []

    if javacopts == None:
        javacopts = []

    # Ensure that the source code is compatible with the minimum supported Java version.
    javacopts = javacopts + JAVA_RELEASE_MIN

    for variant in _VARIANTS:
        suffix = "_" + variant.backend + "_" + variant.codegen
        variant_javacopts = [_JAVACOPTS[variant.backend], _JAVACOPTS[variant.codegen]]
        variant_library_rule_type = library_rule_type
        variant_test_rule_type = test_rule_type
        tags = []
        jvm_flags = []

        if variant.flavors:
            flavor_name = "_".join(variant.flavors)
            suffix += "_" + flavor_name
            variant_javacopts += [_JAVACOPTS[flavor] for flavor in variant.flavors]
            tags.append(flavor_name)

            # Add jvm_flags so that the mode can be accessed from within tests.
            jvm_flags.append("-Ddagger.mode=" + flavor_name)

        variant_deps = [canonical_dep_name(dep) + suffix for dep in gen_library_deps]
        test_deps = list(deps)
        if supporting_files:
            supporting_files_name = name + suffix + ("_lib" if test_files else "")
            _GenLibraryWithVariant(
                library_rule_type = variant_library_rule_type,
                name = supporting_files_name,
                srcs = supporting_files,
                tags = tags,
                deps = deps + variant_deps,
                plugins = plugins,
                javacopts = javacopts + variant_javacopts,
            )
            test_deps.append(supporting_files_name)

        for test_file in test_files:
            test_name = test_file.rsplit(".", 1)[0]
            test_srcs = [test_file]

            _GenTestWithVariant(
                library_rule_type = variant_library_rule_type,
                test_rule_type = variant_test_rule_type,
                name = test_name + suffix,
                srcs = test_srcs,
                tags = tags,
                deps = test_deps + variant_deps,
                plugins = plugins,
                javacopts = javacopts + variant_javacopts,
                shard_count = shard_count,
                jvm_flags = jvm_flags,
                test_kwargs = test_kwargs,
            )

def _GenLibraryWithVariant(
        library_rule_type,
        name,
        srcs,
        tags,
        deps,
        plugins,
        javacopts):
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
    if _is_hjar_test_supported(library_rule_type):
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
        test_kwargs):
    test_files = [src for src in srcs if _is_test(src)]
    if len(test_files) != 1:
        fail("Expected 1 test source but found multiples: {0}".format(test_files))

    test_name = test_files[0].rsplit(".", 1)[0]
    prefix_path = "src/test/java/"
    package_name = native.package_name()
    if package_name.find("javatests/") != -1:
        prefix_path = "javatests/"
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
        # TODO(ronshapiro): figure out why android_library has a different set up of compile jars
        android_library,
        android_local_test,
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

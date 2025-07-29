/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android.plugin.task

import com.squareup.javapoet.ClassName
import dagger.hilt.android.plugin.root.AggregatedAnnotation
import dagger.hilt.android.plugin.util.forEachZipEntry
import dagger.hilt.android.plugin.util.isClassFile
import dagger.hilt.android.plugin.util.isJarFile
import dagger.hilt.processor.internal.root.ir.AggregatedDepsIr
import dagger.hilt.processor.internal.root.ir.AggregatedEarlyEntryPointIr
import dagger.hilt.processor.internal.root.ir.AggregatedElementProxyIr
import dagger.hilt.processor.internal.root.ir.AggregatedRootIr
import dagger.hilt.processor.internal.root.ir.AggregatedUninstallModulesIr
import dagger.hilt.processor.internal.root.ir.AliasOfPropagatedDataIr
import dagger.hilt.processor.internal.root.ir.DefineComponentClassesIr
import dagger.hilt.processor.internal.root.ir.ProcessedRootSentinelIr
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.slf4j.Logger

/** Aggregates Hilt dependencies. */
internal class Aggregator
private constructor(
  private val logger: Logger,
  private val asmApiVersion: Int,
) {
  private val classVisitor = AggregatedDepClassVisitor(logger, asmApiVersion)

  val aggregatedRoots: Set<AggregatedRootIr>
    get() = classVisitor.aggregatedRoots

  val processedRoots: Set<ProcessedRootSentinelIr>
    get() = classVisitor.processedRoots

  val defineComponentDeps: Set<DefineComponentClassesIr>
    get() = classVisitor.defineComponentDeps

  val aliasOfDeps: Set<AliasOfPropagatedDataIr>
    get() = classVisitor.aliasOfDeps

  val aggregatedDeps: Set<AggregatedDepsIr>
    get() = classVisitor.aggregatedDeps

  val aggregatedDepProxies: Set<AggregatedElementProxyIr>
    get() = classVisitor.aggregatedDepProxies

  val allAggregatedDepProxies: Set<AggregatedElementProxyIr>
    get() = classVisitor.allAggregatedDepProxies

  val uninstallModulesDeps: Set<AggregatedUninstallModulesIr>
    get() = classVisitor.uninstallModulesDeps

  val earlyEntryPointDeps: Set<AggregatedEarlyEntryPointIr>
    get() = classVisitor.earlyEntryPointDeps

  private class AggregatedDepClassVisitor(
    private val logger: Logger,
    private val asmApiVersion: Int,
  ) : ClassVisitor(asmApiVersion) {

    val aggregatedRoots = mutableSetOf<AggregatedRootIr>()
    val processedRoots = mutableSetOf<ProcessedRootSentinelIr>()
    val defineComponentDeps = mutableSetOf<DefineComponentClassesIr>()
    val aliasOfDeps = mutableSetOf<AliasOfPropagatedDataIr>()
    val aggregatedDeps = mutableSetOf<AggregatedDepsIr>()
    val aggregatedDepProxies = mutableSetOf<AggregatedElementProxyIr>()
    val allAggregatedDepProxies = mutableSetOf<AggregatedElementProxyIr>()
    val uninstallModulesDeps = mutableSetOf<AggregatedUninstallModulesIr>()
    val earlyEntryPointDeps = mutableSetOf<AggregatedEarlyEntryPointIr>()

    var accessCode: Int = Opcodes.ACC_PUBLIC
    lateinit var annotatedClassName: ClassName

    override fun visit(
      version: Int,
      access: Int,
      name: String,
      signature: String?,
      superName: String?,
      interfaces: Array<out String>?
    ) {
      accessCode = access
      annotatedClassName = Type.getObjectType(name).toClassName()
      super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
      val nextAnnotationVisitor = super.visitAnnotation(descriptor, visible)
      val aggregatedAnnotation = AggregatedAnnotation.fromString(descriptor)
      val isHiltAnnotated = aggregatedAnnotation != AggregatedAnnotation.NONE
      // For non-public deps, a proxy might be needed, make a note of it.
      if (isHiltAnnotated && (accessCode and Opcodes.ACC_PUBLIC) != Opcodes.ACC_PUBLIC) {
        allAggregatedDepProxies.add(
          AggregatedElementProxyIr(
            fqName = annotatedClassName.peerClass("_" + annotatedClassName.simpleName()),
            value = annotatedClassName
          )
        )
      }
      when (aggregatedAnnotation) {
        AggregatedAnnotation.AGGREGATED_ROOT -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            lateinit var rootClass: String
            var rootPackage: String? = null
            val rootSimpleNames = mutableListOf<String>()
            lateinit var originatingRootClass: String
            var originatingRootPackage: String? = null
            val originatingRootSimpleNames = mutableListOf<String>()
            lateinit var rootAnnotationClassName: Type
            lateinit var rootComponentPackage: String
            val rootComponentSimpleNames = mutableListOf<String>()

            override fun visit(name: String, value: Any?) {
              when (name) {
                "root" -> rootClass = value as String
                "rootPackage" -> rootPackage = value as String
                "originatingRoot" -> originatingRootClass = value as String
                "originatingRootPackage" -> originatingRootPackage = value as String
                "rootAnnotation" -> rootAnnotationClassName = (value as Type)
                "rootComponentPackage" -> rootComponentPackage = value as String
                else -> error("Unexpected annotation value: $name")
              }
              super.visit(name, value)
            }

            override fun visitArray(name: String): AnnotationVisitor {
              return object : AnnotationVisitor(asmApiVersion, super.visitArray(name)) {
                override fun visit(passThroughValueName: String?, value: Any?) {
                  // Note that passThroughValueName should usually be null since the real name
                  // is the name passed to visitArray.
                  when (name) {
                    "rootSimpleNames" -> rootSimpleNames.add(value as String)
                    "originatingRootSimpleNames" -> originatingRootSimpleNames.add(value as String)
                    "rootComponentSimpleNames" -> rootComponentSimpleNames.add(value as String)
                    else -> error("Unexpected annotation value: $name")
                  }
                  super.visit(passThroughValueName, value)
                }
              }
            }

            override fun visitEnd() {
              val rootClassName = parseClassNameWithFallback(
                rootPackage, rootSimpleNames, rootClass)
              val originatingRootClassName =
                parseClassNameWithFallback(
                  originatingRootPackage,
                  originatingRootSimpleNames,
                  originatingRootClass
                )
              val rootComponentName = parseClassName(rootComponentPackage, rootComponentSimpleNames)

              aggregatedRoots.add(
                AggregatedRootIr(
                  fqName = annotatedClassName,
                  root = rootClassName,
                  originatingRoot = originatingRootClassName,
                  rootAnnotation = rootAnnotationClassName.toClassName(),
                  rootComponentName = rootComponentName,
                )
              )
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.PROCESSED_ROOT_SENTINEL -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            val rootClasses = mutableListOf<String>()

            override fun visitArray(name: String): AnnotationVisitor? {
              return when (name) {
                "roots" -> visitValue { value -> rootClasses.add(value as String) }
                else -> super.visitArray(name)
              }
            }

            override fun visitEnd() {
              processedRoots.add(
                ProcessedRootSentinelIr(fqName = annotatedClassName, roots = rootClasses)
              )
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.DEFINE_COMPONENT -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            lateinit var componentClass: String

            override fun visit(name: String, value: Any?) {
              when (name) {
                "component", "builder" -> componentClass = value as String
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              defineComponentDeps.add(
                DefineComponentClassesIr(fqName = annotatedClassName, component = componentClass)
              )
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.ALIAS_OF -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            val defineComponentScopeClassNames = mutableSetOf<Type>()
            lateinit var aliasClassName: Type

            // visit() handles both array and non-array values.
            // For array values, each value in the array will be visited individually.
            override fun visit(name: String, value: Any?) {
              when (name) {
                // Older versions of AliasOfPropagatedData only passed a single defineComponentScope
                // class value. Fall back on reading the single value if we get old propagated data.
                "defineComponentScope",
                "defineComponentScopes" -> defineComponentScopeClassNames.add(value as Type)
                "alias" -> aliasClassName = (value as Type)
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              aliasOfDeps.add(
                AliasOfPropagatedDataIr(
                  fqName = annotatedClassName,
                  defineComponentScopes =
                    defineComponentScopeClassNames.map { it.toClassName() }.toList(),
                  alias = aliasClassName.toClassName(),
                )
              )
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.AGGREGATED_DEP -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            val componentClasses = mutableListOf<String>()
            var testClass: String? = null
            val replacesClasses = mutableListOf<String>()
            var moduleClass: String? = null
            var entryPoint: String? = null
            var componentEntryPoint: String? = null

            override fun visit(name: String, value: Any?) {
              when (name) {
                "test" -> testClass = value as String
              }
              super.visit(name, value)
            }

            override fun visitArray(name: String): AnnotationVisitor? {
              return when (name) {
                "components" -> visitValue { value -> componentClasses.add(value as String) }
                "replaces" -> visitValue { value -> replacesClasses.add(value as String) }
                "modules" -> visitValue { value -> moduleClass = value as String }
                "entryPoints" -> visitValue { value -> entryPoint = value as String }
                "componentEntryPoints" ->
                  visitValue { value -> componentEntryPoint = value as String }
                else -> super.visitArray(name)
              }
            }

            override fun visitEnd() {
              aggregatedDeps.add(
                AggregatedDepsIr(
                  fqName = annotatedClassName,
                  components = componentClasses,
                  test = testClass,
                  replaces = replacesClasses,
                  module = moduleClass,
                  entryPoint = entryPoint,
                  componentEntryPoint = componentEntryPoint
                )
              )
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.AGGREGATED_DEP_PROXY -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            lateinit var valueClassName: Type

            override fun visit(name: String, value: Any?) {
              when (name) {
                "value" -> valueClassName = (value as Type)
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              aggregatedDepProxies.add(
                AggregatedElementProxyIr(
                  fqName = annotatedClassName,
                  value = valueClassName.toClassName(),
                )
              )
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.AGGREGATED_UNINSTALL_MODULES -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            lateinit var testClass: String
            val uninstallModulesClasses = mutableListOf<String>()

            override fun visit(name: String, value: Any?) {
              when (name) {
                "test" -> testClass = value as String
              }
              super.visit(name, value)
            }

            override fun visitArray(name: String): AnnotationVisitor? {
              return when (name) {
                "uninstallModules" ->
                  visitValue { value -> uninstallModulesClasses.add(value as String) }
                else -> super.visitArray(name)
              }
            }

            override fun visitEnd() {
              uninstallModulesDeps.add(
                AggregatedUninstallModulesIr(
                  fqName = annotatedClassName,
                  test = testClass,
                  uninstallModules = uninstallModulesClasses
                )
              )
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.AGGREGATED_EARLY_ENTRY_POINT -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            lateinit var earlyEntryPointClass: String

            override fun visit(name: String, value: Any?) {
              when (name) {
                "earlyEntryPoint" -> earlyEntryPointClass = value as String
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              earlyEntryPointDeps.add(
                AggregatedEarlyEntryPointIr(
                  fqName = annotatedClassName,
                  earlyEntryPoint = earlyEntryPointClass
                )
              )
              super.visitEnd()
            }
          }
        }
        else -> {
          logger.warn("Found an unknown annotation in Hilt aggregated packages: $descriptor")
        }
      }
      return nextAnnotationVisitor
    }

    fun visitValue(block: (value: Any) -> Unit) =
      object : AnnotationVisitor(asmApiVersion) {
        override fun visit(nullName: String?, value: Any) {
          block(value)
        }
      }
  }

  private fun process(files: Iterable<File>) {
    files.forEach { file ->
      when {
        file.isFile -> visitFile(file)
        file.isDirectory -> file.walkTopDown().filter { it.isFile }.forEach { visitFile(it) }
        else -> logger.warn("Can't process file/directory that doesn't exist: $file")
      }
    }
  }

  private fun visitFile(file: File) {
    when {
      file.isJarFile() ->
        ZipInputStream(file.inputStream()).forEachZipEntry { inputStream, entry ->
          if (entry.isClassFile()) {
            visitClass(inputStream)
          }
        }
      file.isClassFile() -> file.inputStream().use { visitClass(it) }
      else -> logger.debug("Don't know how to process file: $file")
    }
  }

  private fun visitClass(classFileInputStream: InputStream) {
    ClassReader(classFileInputStream)
      .accept(
        classVisitor,
        ClassReader.SKIP_CODE and ClassReader.SKIP_DEBUG and ClassReader.SKIP_FRAMES
      )
  }

  companion object {
    fun from(logger: Logger, asmApiVersion: Int, input: Iterable<File>) =
      Aggregator(logger, asmApiVersion).apply { process(input) }

    // Converts this Type to a ClassName, used instead of ClassName.bestGuess() because ASM class
    // names are based off descriptors and uses 'reflection' naming, i.e. inner classes are split
    // by '$' instead of '.'
    fun Type.toClassName(): ClassName {
      val binaryName = this.className
      val packageNameEndIndex = binaryName.lastIndexOf('.')
      val packageName =
        if (packageNameEndIndex != -1) {
          binaryName.substring(0, packageNameEndIndex)
        } else {
          ""
        }
      val shortNames = binaryName.substring(packageNameEndIndex + 1).split('$')
      return ClassName.get(packageName, shortNames.first(), *shortNames.drop(1).toTypedArray())
    }

    fun parseClassNameWithFallback(
      packageName: String?,
      simpleNames: List<String>,
      fallbackCanonicalName: String,
    ): ClassName {
      if (packageName != null) {
        return parseClassName(packageName, simpleNames)
      } else {
        // This is very unlikely, but if somehow an aggregated root is coming from a jar build with
        // a previous Dagger version before the package name attribute was introduced, we should
        // fallback to the old behavior of trying to guess at the name.
        return ClassName.bestGuess(fallbackCanonicalName)
      }
    }

    fun parseClassName(packageName: String, simpleNames: List<String>): ClassName {
      check(simpleNames.isNotEmpty())
        return ClassName.get(
          packageName,
          simpleNames.first(),
          *simpleNames.subList(1, simpleNames.size).toTypedArray()
        )
    }
  }
}

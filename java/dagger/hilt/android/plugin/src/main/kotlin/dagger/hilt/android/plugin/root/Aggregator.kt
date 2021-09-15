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
internal class Aggregator private constructor(
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
            lateinit var originatingRootClass: String
            lateinit var rootAnnotationClassName: Type

            override fun visit(name: String, value: Any?) {
              when (name) {
                "root" -> rootClass = value as String
                "originatingRoot" -> originatingRootClass = value as String
                "rootAnnotation" -> rootAnnotationClassName = (value as Type)
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              aggregatedRoots.add(
                AggregatedRootIr(
                  fqName = annotatedClassName,
                  root = rootClass.toClassName(),
                  originatingRoot = originatingRootClass.toClassName(),
                  rootAnnotation = rootAnnotationClassName.toClassName()
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
                ProcessedRootSentinelIr(
                  fqName = annotatedClassName,
                  roots = rootClasses.map { it.toClassName() }
                )
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
                DefineComponentClassesIr(
                  fqName = annotatedClassName,
                  component = componentClass.toClassName()
                )
              )
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.ALIAS_OF -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            lateinit var defineComponentScopeClassName: Type
            lateinit var aliasClassName: Type

            override fun visit(name: String, value: Any?) {
              when (name) {
                "defineComponentScope" -> defineComponentScopeClassName = (value as Type)
                "alias" -> aliasClassName = (value as Type)
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              aliasOfDeps.add(
                AliasOfPropagatedDataIr(
                  fqName = annotatedClassName,
                  defineComponentScope = defineComponentScopeClassName.toClassName(),
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
                "components" ->
                  visitValue { value -> componentClasses.add(value as String) }
                "replaces" ->
                  visitValue { value -> replacesClasses.add(value as String) }
                "modules" ->
                  visitValue { value -> moduleClass = value as String }
                "entryPoints" ->
                  visitValue { value -> entryPoint = value as String }
                "componentEntryPoints" ->
                  visitValue { value -> componentEntryPoint = value as String }
                else -> super.visitArray(name)
              }
            }

            override fun visitEnd() {
              aggregatedDeps.add(
                AggregatedDepsIr(
                  fqName = annotatedClassName,
                  components = componentClasses.map { it.toClassName() },
                  test = testClass?.toClassName(),
                  replaces = replacesClasses.map { it.toClassName() },
                  module = moduleClass?.toClassName(),
                  entryPoint = entryPoint?.toClassName(),
                  componentEntryPoint = componentEntryPoint?.toClassName()
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
                  test = testClass.toClassName(),
                  uninstallModules = uninstallModulesClasses.map { it.toClassName() }
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
                  earlyEntryPoint = earlyEntryPointClass.toClassName()
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
      file.isJarFile() -> ZipInputStream(file.inputStream()).forEachZipEntry { inputStream, entry ->
        if (entry.isClassFile()) {
          visitClass(inputStream)
        }
      }
      file.isClassFile() -> file.inputStream().use { visitClass(it) }
      else -> logger.debug("Don't know how to process file: $file")
    }
  }

  private fun visitClass(classFileInputStream: InputStream) {
    ClassReader(classFileInputStream).accept(
      classVisitor,
      ClassReader.SKIP_CODE and ClassReader.SKIP_DEBUG and ClassReader.SKIP_FRAMES
    )
  }

  companion object {
    fun from(
      logger: Logger,
      asmApiVersion: Int,
      input: Iterable<File>
    ) = Aggregator(logger, asmApiVersion).apply { process(input) }

    // Converts this Type to a ClassName, used instead of ClassName.bestGuess() because ASM class
    // names are based off descriptors and uses 'reflection' naming, i.e. inner classes are split
    // by '$' instead of '.'
    fun Type.toClassName(): ClassName {
      val binaryName = this.className
      val packageNameEndIndex = binaryName.lastIndexOf('.')
      val packageName = if (packageNameEndIndex != -1) {
        binaryName.substring(0, packageNameEndIndex)
      } else {
        ""
      }
      val shortNames = binaryName.substring(packageNameEndIndex + 1).split('$')
      return ClassName.get(packageName, shortNames.first(), *shortNames.drop(1).toTypedArray())
    }

    // Converts this String representing the canonical name of a class to a ClassName.
    fun String.toClassName(): ClassName {
      return ClassName.bestGuess(this)
    }
  }
}

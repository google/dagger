package dagger.hilt.android.plugin.task

import com.squareup.javapoet.ClassName
import dagger.hilt.android.plugin.util.forEachZipEntry
import dagger.hilt.android.plugin.util.isClassFile
import dagger.hilt.android.plugin.util.isJarFile
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.gradle.api.logging.Logger
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Type

/**
 * Aggregates Hilt dependencies.
 */
// TODO(danysantiago): Replace by shared logic in RootProcessor
internal class ComponentTreeDepsAggregator(
  private val logger: Logger,
  private val asmApiVersion: Int,
  private val isTestEnvironment: Boolean,
) {

  private data class RootDep(
    val fqName: String,
    val rootName: String,
    val isTestRoot: Boolean
  ) {
    // TODO: Disambiguate names.
    val className: ClassName by lazy {
      val packageName = rootName.substringBeforeLast('.')
      val simpleName = rootName.substringAfterLast('.') + "_ComponentTreeDeps"
      ClassName.get(packageName, simpleName)
    }

    companion object {
      val DEFAULT_TEST_ROOT = RootDep(
        fqName = "",
        rootName = "dagger.hilt.android.internal.testing.root.Default",
        isTestRoot = true
      )
    }
  }

  // Represents an `@AggregatedDeps`
  private data class Dep(
    val fqName: String,
    val isModule: Boolean, // @Module
    val isTestInstall: Boolean // @TestInstallIn
  )

  // All roots and dependencies classes are represented by their fully qualified name.
  private val roots = mutableSetOf<RootDep>()
  private val processedRoots = mutableSetOf<String>()
  private val defineComponentDeps = mutableSetOf<String>()
  private val aliasOfDeps = mutableSetOf<String>()
  private val earlyEntryPointDeps = mutableSetOf<String>()

  private val defaultRootDeps = mutableSetOf<Dep>()
  private val testRootDepsMap = mutableMapOf<String, MutableSet<Dep>>()
  private val testRootUninstallModulesDepsMap = mutableMapOf<String, String>()

  private val classVisitor = object : ClassVisitor(asmApiVersion) {
    lateinit var annotatedClassName: String

    override fun visit(
      version: Int,
      access: Int,
      name: String,
      signature: String?,
      superName: String?,
      interfaces: Array<out String>?
    ) {
      this.annotatedClassName = Type.getObjectType(name).className
      super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
      val nextAnnotationVisitor = super.visitAnnotation(descriptor, visible)
      // TODO: Handle @PublicProxy
      when (AggregatedAnnotation.fromString(descriptor)) {
        AggregatedAnnotation.AGGREGATED_ROOT -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            var rootClass: String = ""
            var isTestRoot: Boolean = false

            override fun visit(name: String, value: Any?) {
              when (name) {
                "root" -> rootClass = value as String
                "rootAnnotation" -> isTestRoot =
                  (value as Type).descriptor == "Ldagger/hilt/android/testing/HiltAndroidTest;"
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              roots.add(RootDep(annotatedClassName, rootClass, isTestRoot))
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.AGGREGATED_ROOT_SENTINEL -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            var roots = mutableSetOf<String>()

            override fun visit(name: String, value: Any?) {
              when (name) {
                "roots" -> roots.add(value as String)
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              processedRoots.addAll(roots)
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.DEFINE_COMPONENT ->
          defineComponentDeps.add(annotatedClassName)
        AggregatedAnnotation.ALIAS_OF ->
          aliasOfDeps.add(annotatedClassName)
        AggregatedAnnotation.AGGREGATED_DEP -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            var testClass: String = ""
            var isModuleDep: Boolean = false
            var isTestInstall: Boolean = false

            override fun visit(name: String, value: Any?) {
              when (name) {
                "test" -> testClass = value as String
              }
              super.visit(name, value)
            }

            override fun visitArray(name: String): AnnotationVisitor? {
              // Dependencies that affect installed modules also affect the shareability of the
              // component tree.
              when (name) {
                "modules" -> isModuleDep = true
                "replaces" -> isTestInstall = true
              }
              return super.visitArray(name)
            }

            override fun visitEnd() {
              if (testClass.isEmpty()) {
                defaultRootDeps.add(Dep(annotatedClassName, isModuleDep, isTestInstall))
              } else {
                testRootDepsMap.getOrPut(testClass) { mutableSetOf() }
                  .add(Dep(annotatedClassName, isModuleDep, false))
              }
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.AGGREGATED_UNINSTALL_MODULES -> {
          return object : AnnotationVisitor(asmApiVersion, nextAnnotationVisitor) {
            var testClass: String = ""

            override fun visit(name: String, value: Any?) {
              when (name) {
                "test" -> testClass = value as String
              }
              super.visit(name, value)
            }

            override fun visitEnd() {
              testRootUninstallModulesDepsMap[testClass] = annotatedClassName
              super.visitEnd()
            }
          }
        }
        AggregatedAnnotation.AGGREGATED_EARLY_ENTRY_POINT ->
          earlyEntryPointDeps.add(annotatedClassName)
        else -> logger.debug("Found an unknown annotation in Hilt aggregated packages: $descriptor")
      }
      return nextAnnotationVisitor
    }
  }

  fun process(files: Iterable<File>): List<ComponentTreeDepsMetadata> {
    files.forEach { file ->
      when {
        file.isFile -> visitFile(file)
        file.isDirectory -> file.walkTopDown().filter { it.isFile }.forEach { visitFile(it) }
        else -> logger.warn("Can't process file/directory that doesn't exist: $file")
      }
    }
    // TODO(danysantiago): Add 3 checks:
    //   * No new roots when test roots are already processed
    //   * No app and test roots to process in the same task
    //   * No multiple app roots
    val rootsToProcess =
      roots.filterNot { processedRoots.contains(it.rootName) }.sortedBy { it.rootName }
    if (rootsToProcess.isNotEmpty() && !isTestEnvironment) {
      return listOf(
        ComponentTreeDepsMetadata(
          className = rootsToProcess.first().className,
          rootDeps = setOf(rootsToProcess.first().fqName),
          defineComponentDeps = defineComponentDeps,
          aliasOfDeps = aliasOfDeps,
          aggregatedDeps = defaultRootDeps.filter { !it.isTestInstall }.map { it.fqName }.toSet(),
          uninstallModulesDeps = emptySet(),
          earlyEntryPointDeps = earlyEntryPointDeps
        )
      )
    }

    val usesSharedComponent = rootsToProcess.filter { root ->
      root.isTestRoot &&
        testRootDepsMap.getOrElse(root.rootName) { emptySet() }.none { it.isModule } &&
        !testRootUninstallModulesDepsMap.containsKey(root.rootName)
    }
    val sharedDeps = (defaultRootDeps + usesSharedComponent.flatMap { root ->
      testRootDepsMap.getOrElse(root.rootName) { emptyList() }
    }).map { it.fqName }.toSet()
    return if (usesSharedComponent.isNotEmpty() || earlyEntryPointDeps.isNotEmpty()) {
      // Default shared test component
      listOf(
        ComponentTreeDepsMetadata(
          className = RootDep.DEFAULT_TEST_ROOT.className,
          rootDeps = usesSharedComponent.map { it.fqName }.toSet(),
          defineComponentDeps = defineComponentDeps,
          aliasOfDeps = aliasOfDeps,
          aggregatedDeps = sharedDeps,
          uninstallModulesDeps = emptySet(),
          earlyEntryPointDeps = earlyEntryPointDeps
        )
      )
    } else {
      // No default shared test component
      emptyList()
    } + rootsToProcess.filter {
      it.isTestRoot && !usesSharedComponent.contains(it)
    }.map { testRoot ->
      // Non-shared test components
      val testTreeDeps =
        (defaultRootDeps + testRootDepsMap.getOrElse(testRoot.rootName) { emptySet() })
          .map { it.fqName }
          .toSet()
      ComponentTreeDepsMetadata(
        className = RootDep.DEFAULT_TEST_ROOT.className.peerClass(testRoot.className.simpleName()),
        rootDeps = setOf(testRoot.fqName),
        defineComponentDeps = defineComponentDeps,
        aliasOfDeps = aliasOfDeps,
        aggregatedDeps = testTreeDeps,
        uninstallModulesDeps =
        testRootUninstallModulesDepsMap[testRoot.rootName]?.let { setOf(it) } ?: emptySet(),
        earlyEntryPointDeps = emptySet()
      )
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
}

// Annotations used for aggregating dependencies by the annotation processors.
private enum class AggregatedAnnotation(
  val descriptor: String
) {
  AGGREGATED_ROOT(
    "Ldagger/hilt/internal/aggregatedroot/AggregatedRoot;"
  ),
  AGGREGATED_ROOT_SENTINEL(
    "Ldagger/hilt/internal/aggregatedrootsentinel/AggregatedRootSentinel;"
  ),
  DEFINE_COMPONENT(
    "Ldagger/hilt/internal/definecomponent/DefineComponentClasses;"
  ),
  ALIAS_OF(
    "Ldagger/hilt/internal/aliasof/AliasOfPropagatedData;"
  ),
  AGGREGATED_DEP(
    "Ldagger/hilt/processor/internal/aggregateddeps/AggregatedDeps;"
  ),
  AGGREGATED_UNINSTALL_MODULES(
    "Ldagger/hilt/android/internal/uninstallmodules/AggregatedUninstallModules;"
  ),
  AGGREGATED_EARLY_ENTRY_POINT(
    "Ldagger/hilt/android/internal/earlyentrypoint/AggregatedEarlyEntryPoint;"
  ),
  NONE("");

  companion object {
    fun fromString(str: String) = values().firstOrNull { it.descriptor == str } ?: NONE
  }
}

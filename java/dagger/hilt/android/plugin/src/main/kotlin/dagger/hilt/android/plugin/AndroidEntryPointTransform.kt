/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.plugin

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import java.io.File
import java.util.zip.ZipEntry
import javassist.ClassPool
import javassist.CtClass
import org.slf4j.LoggerFactory

/**
 * Bytecode transformation to make @AndroidEntryPoint annotated classes extend the Hilt
 * generated android classes.
 *
 * A transform receives input as a collection [TransformInput], which is composed of [JarInput]s and
 * [DirectoryInput]s. The resulting files must be placed in the
 * [TransformInvocation.getOutputProvider]. The bytecode transformation can be done with any library
 * (in our case Javaassit). The [QualifiedContent.Scope] defined in a transform defines the input
 * the transform will receive and if it can be applied to only the Android application projects or
 * Android libraries too.
 *
 * See: [TransformPublic Docs](https://google.github.io/android-gradle-dsl/javadoc/current/com/android/build/api/transform/Transform.html)
 */
class AndroidEntryPointTransform : Transform() {

  private val logger = LoggerFactory.getLogger(javaClass)

  // The name of the transform. This name appears as a gradle task.
  override fun getName() = "AndroidEntryPointTransform"

  // The type of input this transform will handle.
  override fun getInputTypes() = setOf(QualifiedContent.DefaultContentType.CLASSES)

  override fun isIncremental() = true

  // The project scope this transform is applied to.
  override fun getScopes() = mutableSetOf(QualifiedContent.Scope.PROJECT)

  /**
   * Performs the transformation of the bytecode.
   *
   * The inputs will be available in the [TransformInvocation] along with referenced inputs that
   * should not be transformed. The inputs received along with the referenced inputs depend on the
   * scope of the transform.
   *
   * The invocation will also indicate if an incremental transform has to be applied or not. Even
   * though a transform might return true in its [isIncremental] function, the invocation might
   * return false in [TransformInvocation.isIncremental], therefore both cases must be handled.
   */
  override fun transform(invocation: TransformInvocation) {
    if (!invocation.isIncremental) {
      // Remove any lingering files on a non-incremental invocation since everything has to be
      // transformed.
      invocation.outputProvider.deleteAll()
    }

    // Create a ClassPool with the given input and references, this allows us to use the higher
    // level Javaassit APIs, but requires class parsing/loading, note that since this is a PROJECT
    // scoped transform we can only load classes in the project and not its dependencies.
    val pool = createClassPool(invocation.inputs, invocation.referencedInputs)

    invocation.inputs.forEach { transformInput ->
      transformInput.jarInputs.forEach { jarInput ->
        val outputJar =
          invocation.outputProvider.getContentLocation(
            jarInput.name,
            jarInput.contentTypes,
            jarInput.scopes,
            Format.JAR
          )
        if (invocation.isIncremental) {
          when (jarInput.status) {
            Status.ADDED, Status.CHANGED -> copyJar(jarInput.file, outputJar, pool)
            Status.REMOVED -> outputJar.delete()
            Status.NOTCHANGED -> {
              // No need to transform.
            }
            else -> {
              error("Unknown status: ${jarInput.status}")
            }
          }
        } else {
          copyJar(jarInput.file, outputJar, pool)
        }
      }
      transformInput.directoryInputs.forEach { directoryInput ->
        val outputDir = invocation.outputProvider.getContentLocation(
          directoryInput.name,
          directoryInput.contentTypes,
          directoryInput.scopes,
          Format.DIRECTORY
        )
        if (invocation.isIncremental) {
          directoryInput.changedFiles.forEach { (file, status) ->
            val outputFile = toOutputFile(outputDir, directoryInput.file, file)
            when (status) {
              Status.ADDED, Status.CHANGED -> transformFile(file, outputFile.parentFile, pool)
              Status.REMOVED -> outputFile.delete()
              Status.NOTCHANGED -> {
                // No need to transform.
              }
              else -> {
                error("Unknown status: $status")
              }
            }
          }
        } else {
          directoryInput.file.walkTopDown().forEach { file ->
            val outputFile = toOutputFile(outputDir, directoryInput.file, file)
            transformFile(file, outputFile.parentFile, pool)
          }
        }
      }
    }
  }

  // Create class pool using invocation inputs as classpath.
  private fun createClassPool(
    inputs: Collection<TransformInput>,
    referencedInputs: Collection<TransformInput>
  ) = ClassPool.getDefault().apply {
    (inputs + referencedInputs).flatMap { input ->
      (input.directoryInputs + input.jarInputs).map { it.file.path }
    }.forEach {
      appendClassPath(it)
    }
  }

  // We are only interested in project compiled classes but we have to copy received jars to the
  // output.
  private fun copyJar(inputJar: File, outputJar: File, pool: ClassPool) {
    outputJar.parentFile?.mkdirs()
    inputJar.copyTo(target = outputJar, overwrite = true)
  }

  // Transform a single class file.
  private fun transformFile(inputFile: File, outputDir: File, pool: ClassPool) {
    outputDir.mkdirs()
    if (inputFile.isClassFile()) {
      val clazz = inputFile.inputStream().use { pool.makeClass(it, false) }
      transformClass(clazz, pool)
      clazz.writeFile(outputDir.path)
    } else if (inputFile.isFile) {
      // Copy all non .class files to the output.
      val outputFile = File(outputDir, inputFile.name)
      inputFile.copyTo(target = outputFile, overwrite = true)
    }
  }

  // Transform a parsed class file.
  private fun transformClass(clazz: CtClass, pool: ClassPool) {
    if (!clazz.hasAnnotation("dagger.hilt.android.AndroidEntryPoint")) {
      // Not a AndroidEntryPoint annotated class, don't do anything.
      return
    }

    // TODO(danysantiago): Handle classes with '$' in their name if they do become an issue.
    val superclassName = clazz.classFile.superclass
    val entryPointSuperclassName =
      clazz.packageName + ".Hilt_" + clazz.simpleName.replace("$", "_")
    logger.info(
      "[$name] Transforming ${clazz.name} to extend $entryPointSuperclassName instead of " +
        "$superclassName."
    )
    clazz.superclass = pool.get(entryPointSuperclassName)
  }

  private fun toOutputFile(outputDir: File, inputDir: File, inputFile: File) =
    File(outputDir, inputFile.relativeTo(inputDir).path)

  private fun ZipEntry.isClassFile() =
    !this.isDirectory && this.name.endsWith(SdkConstants.DOT_CLASS)

  private fun File.isClassFile() = this.isFile && this.name.endsWith(SdkConstants.DOT_CLASS)
}

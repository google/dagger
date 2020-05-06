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

import dagger.hilt.android.plugin.util.isClassFile
import dagger.hilt.android.plugin.util.isJarFile
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import javassist.ClassPool
import javassist.CtClass
import org.slf4j.LoggerFactory

/**
 * A helper class for performing the transform.
 *
 * Create it with the list of all available source directories along with the root output directory
 * and use [AndroidEntryPointClassTransformer.transformFile] or
 * [AndroidEntryPointClassTransformer.transformJarContents] to perform the actual transformation.
 */
internal class AndroidEntryPointClassTransformer(
  val taskName: String,
  allInputs: List<File>,
  private val sourceRootOutputDir: File,
  private val copyNonTransformed: Boolean
) {
  private val logger = LoggerFactory.getLogger(AndroidEntryPointClassTransformer::class.java)

  // A ClassPool created from the given input files, this allows us to use the higher
  // level Javaassit APIs, but requires class parsing/loading.
  private val classPool: ClassPool = ClassPool(true).also { pool ->
    allInputs.forEach {
      pool.appendClassPath(it.path)
    }
  }

  init {
    sourceRootOutputDir.mkdirs()
  }

  /**
   * Transforms the classes inside the jar and copies re-written class files if and only if they are
   * transformed.
   *
   * @param inputFile The jar file to transform, must be a jar.
   * @return true if at least one class within the jar was transformed.
   */
  fun transformJarContents(inputFile: File): Boolean {
    require(inputFile.isJarFile()) {
      "Invalid file, '$inputFile' is not a jar."
    }
    // Validate transform is not applied to a jar when copying is enabled, meaning the transformer
    // is being used in the Android transform API pipeline which does not need to transform jars
    // and handles copying them.
    check(!copyNonTransformed) {
      "Transforming a jar is not supported with 'copyNonTransformed'."
    }
    var transformed = false
    ZipInputStream(FileInputStream(inputFile)).use { input ->
      var entry = input.nextEntry
      while (entry != null) {
        if (entry.isClassFile()) {
          val clazz = classPool.makeClass(input, false)
          transformed = transformClassToOutput(clazz) || transformed
        }
        entry = input.nextEntry
      }
    }
    return transformed
  }

  /**
   * Transform a single class file.
   *
   * @param inputFile The file to transform, must be a class file.
   * @return true if the class file was transformed.
   */
  fun transformFile(inputFile: File): Boolean {
    check(inputFile.isClassFile()) {
      "Invalid file, '$inputFile' is not a class."
    }
    val clazz = inputFile.inputStream().use { classPool.makeClass(it, false) }
    return transformClassToOutput(clazz)
  }

  private fun transformClassToOutput(clazz: CtClass): Boolean {
    val transformed = transformClass(clazz)
    if (transformed || copyNonTransformed) {
      clazz.writeFile(sourceRootOutputDir.path)
    }
    return transformed
  }

  private fun transformClass(clazz: CtClass): Boolean {
    if (!clazz.hasAnnotation("dagger.hilt.android.AndroidEntryPoint")) {
      // Not a AndroidEntryPoint annotated class, don't do anything.
      return false
    }

    // TODO(danysantiago): Handle classes with '$' in their name if they do become an issue.
    val superclassName = clazz.classFile.superclass
    val entryPointSuperclassName =
      clazz.packageName + ".Hilt_" + clazz.simpleName.replace("$", "_")
    logger.info(
      "[$taskName] Transforming ${clazz.name} to extend $entryPointSuperclassName instead of " +
        "$superclassName."
    )
    clazz.superclass = classPool.get(entryPointSuperclassName)
    return true
  }
}

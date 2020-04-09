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
import javassist.ClassPool
import javassist.CtClass
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Simplified transformer that can transform class files.
 *
 * Create it with the list of all available source directories and use [HiltClassTransformer.transformFile] method to
 * transform each file.
 */
class HiltClassTransformer(
  val taskName: String,
  allInputs: List<File>,
  private val copyNonTransformed : Boolean
) {
  private val logger = LoggerFactory.getLogger(HiltClassTransformer::class.java)
  private val classPool: ClassPool = ClassPool.getDefault().also { pool ->
    allInputs.forEach {
      pool.appendClassPath(it.path)
    }
  }

  /**
   * Transforms the classes inside the jar and copies re-written class files if and only if they are
   * transformed.
   */
  fun transformJarContents(
    inputFile: File,
    outputSourceRootDir: File
  ) {
    check(!copyNonTransformed) {
      "Jar copying is not supported here"
    }
    outputSourceRootDir.mkdirs()
    require(inputFile.extension == SdkConstants.EXT_JAR) {
      "invalid file, $inputFile is not a jar"
    }
    ZipInputStream(FileInputStream(inputFile)).use { input ->
      var entry = input.nextEntry
      while (entry != null) {
        if (entry.isClassFile()) {
          val clazz = classPool.makeClass(input, false)
          transformClassToOutput(clazz, outputSourceRootDir)
        }
        entry = input.nextEntry
      }
    }
  }

  // Transform a single class file.
  /**
   * @param inputFile The file to transform
   * @param outputDir The parent directory where the file will be written. This should be the parent
   * file that includes the folders for the package name.
   * @param outputSourceRootDir The target source root for the file. This should be the root package
   * name folder.
   */
  fun transformFile(
    inputFile: File,
    outputDir: File,
    outputSourceRootDir: File
  ) {
    outputSourceRootDir.mkdirs()
    if (inputFile.isClassFile()) {
      transformClassFileToOutput(
        inputFile = inputFile,
        outputSourceRootDir = outputSourceRootDir
      )
    } else if (inputFile.isFile) {
      // Copy all non .class files to the output.
      outputDir.mkdirs()
      val outputFile = File(outputDir, inputFile.name)
      inputFile.copyTo(target = outputFile, overwrite = true)
    }
  }

  private fun transformClassFileToOutput(
    inputFile: File,
    outputSourceRootDir : File
  ) {
    val clazz = inputFile.inputStream().use { classPool.makeClass(it, false) }
    transformClassToOutput(clazz, outputSourceRootDir)
  }

  private fun transformClassToOutput(clazz: CtClass, outputSourceRootDir: File) {
    val transformed = transformClass(clazz)
    if (transformed || copyNonTransformed) {
      clazz.writeFile(outputSourceRootDir.path)
    }
  }

  // Transform a parsed class file.
  private fun transformClass(clazz: CtClass) : Boolean {
    if (!clazz.hasAnnotation("dagger.hilt.android.AndroidEntryPoint")) {
      // Not a AndroidEntryPoint annotated class, don't do anything.
      return false
    }

    // TODO(danysantiago): Handle classes with '$' in their name if they do become an issue.
    val superclassName = clazz.classFile.superclass
    val entryPointSuperclassName =
      clazz.packageName + ".Hilt_" + clazz.simpleName.replace("$", "_")
    logger.info(
      "[${taskName}] Transforming ${clazz.name} to extend $entryPointSuperclassName instead of " +
        "$superclassName."
    )
    clazz.superclass = classPool.get(entryPointSuperclassName)
    return true
  }

  private fun File.isClassFile() = this.isFile && this.extension == SdkConstants.EXT_CLASS

  private fun ZipEntry.isClassFile() =
    !this.isDirectory && this.name.endsWith(SdkConstants.DOT_CLASS)
}

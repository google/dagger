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
import javassist.Modifier
import javassist.bytecode.Bytecode
import javassist.bytecode.CodeIterator
import javassist.bytecode.Opcode
import org.slf4j.LoggerFactory

typealias CodeArray = javassist.bytecode.ByteArray // Avoids conflict with Kotlin's stdlib ByteArray

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
          clazz.detach()
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
    val transformed = transformClassToOutput(clazz)
    clazz.detach()
    return transformed
  }

  private fun transformClassToOutput(clazz: CtClass): Boolean {
    val transformed = transformClass(clazz)
    if (transformed || copyNonTransformed) {
      clazz.writeFile(sourceRootOutputDir.path)
    }
    return transformed
  }

  private fun transformClass(clazz: CtClass): Boolean {
    if (ANDROID_ENTRY_POINT_ANNOTATIONS.none { clazz.hasAnnotation(it) }) {
      // Not a Android entry point annotated class, don't do anything.
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
    val entryPointSuperclass = classPool.get(entryPointSuperclassName)
    clazz.superclass = entryPointSuperclass
    transformSuperMethodCalls(clazz, superclassName, entryPointSuperclassName)

    // Check if Hilt generated class is a BroadcastReceiver with the marker field which means
    // a super.onReceive invocation has to be inserted in the implementation.
    if (entryPointSuperclass.declaredFields.any { it.name == "onReceiveBytecodeInjectionMarker" }) {
      transformOnReceive(clazz, entryPointSuperclassName)
    }

    return true
  }

  /**
   * Iterates over each declared method, finding in its bodies super calls. (e.g. super.onCreate())
   * and rewrites the method reference of the invokespecial instruction to one that uses the new
   * superclass.
   *
   * The invokespecial instruction is emitted for code that between other things also invokes a
   * method of a superclass of the current class. The opcode invokespecial takes two operands, each
   * of 8 bit, that together represent an address in the constant pool to a method reference. The
   * method reference is computed at compile-time by looking the direct superclass declaration, but
   * at runtime the code behaves like invokevirtual, where as the actual method invoked is looked up
   * based on the class hierarchy.
   *
   * However, it has been observed that on APIs 19 to 22 the Android Runtime (ART) jumps over the
   * direct superclass and into the method reference class, causing unexpected behaviours.
   * Therefore, this method performs the additional transformation to rewrite direct super call
   * invocations to use a method reference whose class in the pool is the new superclass. Note that
   * this is not necessary for constructor calls since the Javassist library takes care of those.
   *
   * @see: https://docs.oracle.com/javase/specs/jvms/se11/html/jvms-6.html#jvms-6.5.invokespecial
   * @see: https://source.android.com/devices/tech/dalvik/dalvik-bytecode
   */
  private fun transformSuperMethodCalls(
    clazz: CtClass,
    oldSuperclassName: String,
    newSuperclassName: String
  ) {
    val constantPool = clazz.classFile.constPool
    clazz.declaredMethods
      .filter {
        it.methodInfo.isMethod &&
          !Modifier.isStatic(it.modifiers) &&
          !Modifier.isAbstract(it.modifiers) &&
          !Modifier.isNative(it.modifiers)
      }
      .forEach { method ->
        val codeAttr = method.methodInfo.codeAttribute
        val code = codeAttr.code
        codeAttr.iterator().forEachInstruction { index, opcode ->
          // We are only interested in 'invokespecial' instructions.
          if (opcode != Opcode.INVOKESPECIAL) {
            return@forEachInstruction
          }
          // If the method reference of the instruction is not using the old superclass then we
          // should not rewrite it.
          val methodRef = CodeArray.readU16bit(code, index + 1)
          val currentClassRef = constantPool.getMethodrefClassName(methodRef)
          if (currentClassRef != oldSuperclassName) {
            return@forEachInstruction
          }
          // If the method reference of the instruction is a constructor, then we should not
          // rewrite it since its an instantiation and not a `super()` call.
          val methodRefName = constantPool.getMethodrefName(methodRef)
          if (methodRefName == "<init>") {
            return@forEachInstruction
          }
          val nameAndTypeRef = constantPool.getMethodrefNameAndType(methodRef)
          val newSuperclassRef = constantPool.addClassInfo(newSuperclassName)
          val newMethodRef = constantPool.addMethodrefInfo(newSuperclassRef, nameAndTypeRef)
          logger.info(
            "[$taskName] Redirecting an invokespecial in " +
              "${clazz.name}.${method.name}:${method.signature} at code index $index from " +
              "method ref #$methodRef to #$newMethodRef."
          )
          CodeArray.write16bit(newMethodRef, code, index + 1)
        }
      }
  }

  // Iterate over each instruction in a CodeIterator.
  private fun CodeIterator.forEachInstruction(body: CodeIterator.(Int, Int) -> Unit) {
    while (hasNext()) {
      val index = next()
      this.body(index, byteAt(index))
    }
  }

  /**
   * For a BroadcastReceiver insert a super call in the onReceive method implementation since
   * after the class is transformed onReceive will no longer be abstract (it is implemented by
   * Hilt generated receiver).
   */
  private fun transformOnReceive(clazz: CtClass, entryPointSuperclassName: String) {
    val method = clazz.declaredMethods.first {
      it.name + it.signature == ON_RECEIVE_METHOD_NAME + ON_RECEIVE_METHOD_SIGNATURE
    }
    val constantPool = clazz.classFile.constPool
    val newCode = Bytecode(constantPool).apply {
      addAload(0) // Loads 'this'
      addAload(1) // Loads method param 1 (Context)
      addAload(2) // Loads method param 2 (Intent)
      addInvokespecial(
        entryPointSuperclassName, ON_RECEIVE_METHOD_NAME, ON_RECEIVE_METHOD_SIGNATURE
      )
    }
    val newCodeAttribute = newCode.toCodeAttribute()
    val currentCodeAttribute = method.methodInfo.codeAttribute
    currentCodeAttribute.maxStack =
      maxOf(newCodeAttribute.maxStack, currentCodeAttribute.maxStack)
    currentCodeAttribute.maxLocals =
      maxOf(newCodeAttribute.maxLocals, currentCodeAttribute.maxLocals)
    val codeIterator = currentCodeAttribute.iterator()
    val pos = codeIterator.insertEx(newCode.get()) // insert new code
    codeIterator.insert(newCodeAttribute.exceptionTable, pos) // offset exception table
    method.methodInfo.rebuildStackMap(clazz.classPool) // update stack table
  }

  companion object {
    val ANDROID_ENTRY_POINT_ANNOTATIONS = setOf(
      "dagger.hilt.android.AndroidEntryPoint",
      "dagger.hilt.android.HiltAndroidApp"
    )
    val ON_RECEIVE_METHOD_NAME = "onReceive"
    val ON_RECEIVE_METHOD_SIGNATURE =
      "(Landroid/content/Context;Landroid/content/Intent;)V"
  }
}

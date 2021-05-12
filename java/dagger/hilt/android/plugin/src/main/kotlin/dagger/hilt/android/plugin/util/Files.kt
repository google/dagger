package dagger.hilt.android.plugin.util

import com.android.SdkConstants
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipEntry

/* Checks if a file is a .class file. */
fun File.isClassFile() = this.isFile && this.extension == SdkConstants.EXT_CLASS

/* Checks if a Zip entry is a .class file. */
fun ZipEntry.isClassFile() = !this.isDirectory && this.name.endsWith(SdkConstants.DOT_CLASS)

/* Checks if a file is a .jar file. */
fun File.isJarFile() = this.isFile && this.extension == SdkConstants.EXT_JAR

/* Copy a JarEntry to output directory. */
fun JarEntry.copyTo(jarFile: JarFile, target: File, overwrite: Boolean = false, bufferSize: Int = DEFAULT_BUFFER_SIZE): File {
    if (target.exists()) {
        if (!overwrite)
            throw FileAlreadyExistsException(file = File(jarFile.name), other = target, reason = "The destination file already exists.")
        else if (!target.delete())
            throw FileAlreadyExistsException(file = File(jarFile.name), other = target, reason = "Tried to overwrite the destination, but failed to delete it.")
    }

    if (this.isDirectory) {
        if (!target.mkdirs())
            throw FileSystemException(file = File(jarFile.name), other = target, reason = "Failed to create target directory.")
    } else {
        target.parentFile?.mkdirs()

        jarFile.getInputStream(this).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, bufferSize)
            }
        }
    }

    return target
}

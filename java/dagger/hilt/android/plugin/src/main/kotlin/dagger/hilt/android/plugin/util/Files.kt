package dagger.hilt.android.plugin.util

import com.android.SdkConstants
import java.io.File
import java.util.zip.ZipEntry

/* Checks if a file is a .class file. */
fun File.isClassFile() = this.isFile && this.extension == SdkConstants.EXT_CLASS

/* Checks if a Zip entry is a .class file. */
fun ZipEntry.isClassFile() = !this.isDirectory && this.name.endsWith(SdkConstants.DOT_CLASS)

/* CHecks if a file is a .jar file. */
fun File.isJarFile() = this.isFile && this.extension == SdkConstants.EXT_JAR

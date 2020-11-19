package dagger.hilt.android.processor.internal.viewmodelinject

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects

internal val GENERATED_TYPE = try {
  Class.forName("javax.annotation.processing.Generated")
  "javax.annotation.processing.Generated"
} catch (_: ClassNotFoundException) {
  "javax.annotation.Generated"
}

internal val GENERATED_ANNOTATION =
  "@Generated(\"dagger.hilt.android.processor.internal.viewmodelinject.ViewModelInjectProcessor\")"

internal fun String.toJFO(qName: String) = JavaFileObjects.forSourceString(qName, this.trimIndent())

internal fun compiler(): Compiler = Compiler.javac().withProcessors(ViewModelInjectProcessor())

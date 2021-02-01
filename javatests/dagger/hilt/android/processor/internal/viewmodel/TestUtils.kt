package dagger.hilt.android.processor.internal.viewmodel

import com.google.testing.compile.JavaFileObjects

val GENERATED_TYPE = try {
  Class.forName("javax.annotation.processing.Generated")
  "javax.annotation.processing.Generated"
} catch (_: ClassNotFoundException) {
  "javax.annotation.Generated"
}

const val GENERATED_ANNOTATION =
  "@Generated(\"dagger.hilt.android.processor.internal.viewmodel.ViewModelProcessor\")"

fun String.toJFO(qName: String) = JavaFileObjects.forSourceString(qName, this.trimIndent())

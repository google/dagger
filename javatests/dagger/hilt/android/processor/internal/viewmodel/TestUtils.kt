package dagger.hilt.android.processor.internal.viewmodel

import dagger.hilt.android.testing.compile.HiltCompilerTests

val GENERATED_TYPE =
  try {
    Class.forName("javax.annotation.processing.Generated")
    "javax.annotation.processing.Generated"
  } catch (_: ClassNotFoundException) {
    "javax.annotation.Generated"
  }

const val GENERATED_ANNOTATION =
  "@Generated(\"dagger.hilt.android.processor.internal.viewmodel.ViewModelProcessor\")"

fun String.toJFO(qName: String) = HiltCompilerTests.javaSource(qName, this.trimIndent())

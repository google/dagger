package dagger.hilt.android.simpleKotlin.lib

import dagger.hilt.android.simpleKotlin.deep.DeepLib

object KotlinLibraryEntryPoints {
  fun invokeEntryPoints(component: Any) {
    DeepLib.getInstance(component)
  }
}

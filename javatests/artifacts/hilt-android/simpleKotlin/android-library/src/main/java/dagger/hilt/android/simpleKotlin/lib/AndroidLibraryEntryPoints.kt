package dagger.hilt.android.simpleKotlin.lib

import android.content.Context
import dagger.hilt.android.simpleKotlin.deep.DeepAndroidLib
import dagger.hilt.android.simpleKotlin.deep.DeepLib

object AndroidLibraryEntryPoints {
  fun invokeEntryPoints(context: Context) {
    DeepAndroidLib.getInstance(context)
    DeepLib.getInstance(context)
  }
}

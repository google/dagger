package dagger.hilt.android.testing

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class BindValueInKotlinValTest {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface BindValueEntryPoint {
    fun bindValueString1(): String

    @Named(TEST_QUALIFIER)
    fun bindValueString2(): String
  }

  @get:Rule
  val rule = HiltAndroidRule(this)

  @BindValue
  val bindValueString1 = BIND_VALUE_STRING1

  @BindValue
  @Named(TEST_QUALIFIER)
  val bindValueString2 = BIND_VALUE_STRING2

  @Test
  fun testBindValueFieldIsProvided() {
    assertThat(bindValueString1).isEqualTo(BIND_VALUE_STRING1)
    assertThat(getBinding1()).isEqualTo(BIND_VALUE_STRING1)
    assertThat(bindValueString2).isEqualTo(BIND_VALUE_STRING2)
    assertThat(getBinding2()).isEqualTo(BIND_VALUE_STRING2)
  }

  companion object {
    private const val BIND_VALUE_STRING1 = "BIND_VALUE_STRING1"
    private const val BIND_VALUE_STRING2 = "BIND_VALUE_STRING2"
    private const val TEST_QUALIFIER = "TEST_QUALIFIER"

    private fun getBinding1() =
      EntryPoints.get(getApplicationContext(), BindValueEntryPoint::class.java).bindValueString1()

    private fun getBinding2() =
      EntryPoints.get(getApplicationContext(), BindValueEntryPoint::class.java).bindValueString2()
  }
}

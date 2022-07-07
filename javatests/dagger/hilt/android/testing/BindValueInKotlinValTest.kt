package dagger.hilt.android.testing

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.MapKey;
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Named
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class BindValueInKotlinValTest {

  @MapKey
  annotation class MyMapKey(val value: String)

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
  var bindValueString1 = BIND_VALUE_STRING1

  @BindValue
  @Named(TEST_QUALIFIER)
  val bindValueString2 = BIND_VALUE_STRING2

  @BindValueIntoMap
  @MyMapKey(BIND_VALUE_MAP_KEY_STRING)
  val mapContribution = BIND_VALUE_MAP_VALUE_STRING

  @Inject
  lateinit var string1: String

  @Inject
  @Named(TEST_QUALIFIER)
  lateinit var string2: String

  @Inject
  lateinit var map: Map<String, String>

  @Test
  fun testBindValueFieldsAreProvided() {
    rule.inject()
    assertThat(string1).isEqualTo(BIND_VALUE_STRING1)
    assertThat(string2).isEqualTo(BIND_VALUE_STRING2)
    assertThat(map).containsExactlyEntriesIn(
        mapOf(BIND_VALUE_MAP_KEY_STRING to BIND_VALUE_MAP_VALUE_STRING))
  }

  companion object {
    private const val BIND_VALUE_STRING1 = "BIND_VALUE_STRING1"
    private const val BIND_VALUE_STRING2 = "BIND_VALUE_STRING2"
    private const val BIND_VALUE_MAP_KEY_STRING = "BIND_VALUE_MAP_KEY_STRING"
    private const val BIND_VALUE_MAP_VALUE_STRING = "BIND_VALUE_MAP_VALUE_STRING"
    private const val TEST_QUALIFIER = "TEST_QUALIFIER"
  }
}

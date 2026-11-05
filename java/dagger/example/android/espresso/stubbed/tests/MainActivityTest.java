package dagger.example.android.espresso.stubbed.tests;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import dagger.example.android.espresso.main.MainActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import dagger.example.android.espresso.stubbed.R;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

  @Rule
  public ActivityTestRule<MainActivity> activityActivityTestRule = new ActivityTestRule<>(
      MainActivity.class);

  @Test
  public void activityDisplaysRemoteResource() throws Exception {
    onView(withId(R.id.textView))
        .check(matches(withText("stub remote resource")));
  }
}

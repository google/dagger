package dagger.hilt.android.internal.managers;

/**
 * This class does nothing in production or in tests when running under Hilt testing framework. However, the calls
 * to TestInjectInterceptor.injectForTesting() are done in a few strategic places just before Hilt does the injection
 * into Android Components.
 *
 * As a result this class enables non-Hilt based frameworks to take over the injection process.
 */
public class TestInjectInterceptor {
    /**
     * This method always returns false by default. However, if this method is intercepted during testing
     * by frameworks like Robolectric, the intercepting code can take over the injection process and
     * instruct Hilt to skip doing anything extra for this instance.
     *
     * Return false if no custom injection was done and Hilt should continue as normal. Return true
     * if the testing framework has takes over the injection process and Hilt should skip any extra
     * work.
     */
    public static boolean injectForTesting(Object injectTo) {
        return false;
    }
}
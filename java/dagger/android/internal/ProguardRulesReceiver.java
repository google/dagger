package dagger.android.internal;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Our proguard rules generator needs one annotation to hook into for it to run, so we use this
 * internally on {@link AndroidInjectionKeys} as a throwaway for it to run. It has no other purpose.
 */
@Target(TYPE)
@Retention(SOURCE)
@interface ProguardRulesReceiver {

}

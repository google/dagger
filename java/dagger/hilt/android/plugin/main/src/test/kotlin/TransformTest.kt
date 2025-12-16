/*
 * Copyright (C) 2020 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.truth.Truth.assertThat
import java.io.DataInputStream
import java.io.FileInputStream
import javassist.bytecode.ByteArray
import javassist.bytecode.ClassFile
import javassist.bytecode.SignatureAttribute
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransformTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()

  lateinit var gradleRunner: GradleTestRunner

  @Before
  fun setup() {
    gradleRunner = GradleTestRunner(testProjectDir)
    gradleRunner.addSrc(
      srcPath = "minimal/MainActivity.java",
      srcContent =
        """
        package minimal;

        import android.os.Bundle;
        import androidx.appcompat.app.AppCompatActivity;

        @dagger.hilt.android.AndroidEntryPoint
        public class MainActivity extends AppCompatActivity {
          @Override
          public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
          }
        }
        """.trimIndent()
    )
  }

  // Simple functional test to verify transformation.
  @Test
  fun testAssemble() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'"
    )
    gradleRunner.addActivities(
      "<activity android:name=\".MainActivity\"/>"
    )

    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    Assert.assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)

    val transformedClass = result.getTransformedFile("minimal/MainActivity.class")
    FileInputStream(transformedClass).use { fileInput ->
      ClassFile(DataInputStream(fileInput)).let { classFile ->
        // Verify superclass is updated
        Assert.assertEquals("minimal.Hilt_MainActivity", classFile.superclass)
        // Verify super call is also updated
        val constPool = classFile.constPool
        classFile.methods.first { it.name == "onCreate" }.let { methodInfo ->
          // bytecode of MainActivity.onCreate() is:
          // 0 - aload_0
          // 1 - aload_1
          // 2 - invokespecial
          // 5 - return
          val invokeIndex = 2
          val methodRef = ByteArray.readU16bit(methodInfo.codeAttribute.code, invokeIndex + 1)
          val classRef = constPool.getMethodrefClassName(methodRef)
          Assert.assertEquals("minimal.Hilt_MainActivity", classRef)
        }
      }
    }
  }

  // Verify correct transformation is done on nested classes.
  @Test
  fun testAssemble_nestedClass() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'"
    )

    gradleRunner.addSrc(
      srcPath = "minimal/TopClass.java",
      srcContent =
        """
        package minimal;

        import androidx.appcompat.app.AppCompatActivity;

        public class TopClass {
            @dagger.hilt.android.AndroidEntryPoint
            public static class NestedActivity extends AppCompatActivity { }
        }
        """.trimIndent()
    )

    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    Assert.assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)

    val transformedClass = result.getTransformedFile("minimal/TopClass\$NestedActivity.class")
    FileInputStream(transformedClass).use { fileInput ->
      ClassFile(DataInputStream(fileInput)).let { classFile ->
        Assert.assertEquals("minimal.Hilt_TopClass_NestedActivity", classFile.superclass)
      }
    }
  }

  // Verify transformation ignores abstract methods.
  @Test
  fun testAssemble_abstractMethod() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'"
    )

    gradleRunner.addSrc(
      srcPath = "minimal/AbstractActivity.java",
      srcContent =
        """
        package minimal;

        import androidx.appcompat.app.AppCompatActivity;

        @dagger.hilt.android.AndroidEntryPoint
        public abstract class AbstractActivity extends AppCompatActivity {
            public abstract void method();
        }
        """.trimIndent()
    )

    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    Assert.assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)

    val transformedClass = result.getTransformedFile("minimal/AbstractActivity.class")
    FileInputStream(transformedClass).use { fileInput ->
      ClassFile(DataInputStream(fileInput)).let { classFile ->
        Assert.assertEquals("minimal.Hilt_AbstractActivity", classFile.superclass)
      }
    }
  }

  // Verify transformation ignores native methods.
  @Test
  fun testAssemble_nativeMethod() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'"
    )

    gradleRunner.addSrc(
      srcPath = "minimal/SimpleActivity.java",
      srcContent =
        """
        package minimal;

        import androidx.appcompat.app.AppCompatActivity;

        @dagger.hilt.android.AndroidEntryPoint
        public class SimpleActivity extends AppCompatActivity {
            public native void method();
        }
        """.trimIndent()
    )

    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    Assert.assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)

    val transformedClass = result.getTransformedFile("minimal/SimpleActivity.class")
    FileInputStream(transformedClass).use { fileInput ->
      ClassFile(DataInputStream(fileInput)).let { classFile ->
        Assert.assertEquals("minimal.Hilt_SimpleActivity", classFile.superclass)
      }
    }
  }

  // Verifies the transformation is applied incrementally when a class to be transformed is updated.
  @Test
  fun testTransform_incrementalClass() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'"
    )

    val srcFile = gradleRunner.addSrc(
      srcPath = "minimal/OtherActivity.java",
      srcContent =
        """
        package minimal;

        import androidx.appcompat.app.AppCompatActivity;

        @dagger.hilt.android.AndroidEntryPoint
        public class OtherActivity extends AppCompatActivity {

        }
        """.trimIndent()
    )

    gradleRunner.build().let {
      val assembleTask = it.getTask(TRANSFORM_TASK_NAME)
      Assert.assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)
    }

    gradleRunner.build().let {
      val assembleTask = it.getTask(TRANSFORM_TASK_NAME)
      Assert.assertEquals(TaskOutcome.UP_TO_DATE, assembleTask.outcome)
    }

    srcFile.delete()
    gradleRunner.addSrc(
      srcPath = "minimal/OtherActivity.java",
      srcContent =
        """
        package minimal;

        import androidx.fragment.app.FragmentActivity;

        @dagger.hilt.android.AndroidEntryPoint
        public class OtherActivity extends FragmentActivity {

        }
        """.trimIndent()
    )

    val result = gradleRunner.build()
    val assembleTask = result.getTask(TRANSFORM_TASK_NAME)
    Assert.assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)

    val transformedClass = result.getTransformedFile("minimal/OtherActivity.class")
    FileInputStream(transformedClass).use { fileInput ->
      ClassFile(DataInputStream(fileInput)).let { classFile ->
        Assert.assertEquals("minimal.Hilt_OtherActivity", classFile.superclass)
      }
    }
  }

  // Verifies the transformation is applied incrementally when a new class is added to an existing
  // directory.
  @Test
  fun testTransform_incrementalDir() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'"
    )

    gradleRunner.addSrcPackage("ui/")

    gradleRunner.build().let {
      val assembleTask = it.getTask(TRANSFORM_TASK_NAME)
      assertThat(assembleTask.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    gradleRunner.build().let {
      val assembleTask = it.getTask(TRANSFORM_TASK_NAME)
      assertThat(assembleTask.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    gradleRunner.addSrc(
      srcPath = "ui/OtherActivity.java",
      srcContent =
        """
        package ui;

        import androidx.appcompat.app.AppCompatActivity;

        @dagger.hilt.android.AndroidEntryPoint
        public class OtherActivity extends AppCompatActivity {

        }
        """.trimIndent()
    )

    val result = gradleRunner.build()
    val assembleTask = result.getTask(TRANSFORM_TASK_NAME)
    assertThat(assembleTask.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  // Verifies the Signature attribute in the ClassFile is updated when the superclass uses type
  // variables or parameterized types.
  // See: https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.9
  @Test
  fun testTransform_genericSuperclass() {
    gradleRunner.addDependencies(
      "implementation 'androidx.appcompat:appcompat:1.1.0'",
      "implementation 'com.google.dagger:hilt-android:LOCAL-SNAPSHOT'",
      "annotationProcessor 'com.google.dagger:hilt-compiler:LOCAL-SNAPSHOT'"
    )

    gradleRunner.addSrc(
      srcPath = "minimal/BaseActivity.java",
      srcContent =
      """
        package minimal;

        import androidx.appcompat.app.AppCompatActivity;

        public abstract class BaseActivity<T> extends AppCompatActivity {
        }
        """.trimIndent()
    )

    gradleRunner.addSrc(
      srcPath = "minimal/SimpleActivity.java",
      srcContent =
      """
        package minimal;

        @dagger.hilt.android.AndroidEntryPoint
        public class SimpleActivity extends BaseActivity<String> {
        }
        """.trimIndent()
    )

    gradleRunner.addSrc(
      srcPath = "minimal/BasicActivityThing.java",
      srcContent =
      """
        package minimal;

        public class BasicActivityThing {
        }
        """.trimIndent()
    )

    gradleRunner.addSrc(
      srcPath = "minimal/BasicActivity.java",
      srcContent =
      """
        package minimal;

        @dagger.hilt.android.AndroidEntryPoint
        public class BasicActivity extends BaseActivity<BasicActivityThing> {
        }
        """.trimIndent()
    )

    val result = gradleRunner.build()
    val assembleTask = result.getTask(":assembleDebug")
    Assert.assertEquals(TaskOutcome.SUCCESS, assembleTask.outcome)

    val transformedClass1 = result.getTransformedFile("minimal/SimpleActivity.class")
    FileInputStream(transformedClass1).use { fileInput ->
      ClassFile(DataInputStream(fileInput)).let { classFile ->
        Assert.assertEquals("minimal.Hilt_SimpleActivity", classFile.superclass)
        val signatureAttr = classFile.getAttribute(SignatureAttribute.tag) as SignatureAttribute
        Assert.assertEquals(
          "Lminimal/Hilt_SimpleActivity<Ljava/lang/String;>;",
          signatureAttr.signature
        )
      }
    }

    val transformedClass2 = result.getTransformedFile("minimal/BasicActivity.class")
    FileInputStream(transformedClass2).use { fileInput ->
      ClassFile(DataInputStream(fileInput)).let { classFile ->
        val signatureAttr = classFile.getAttribute(SignatureAttribute.tag) as SignatureAttribute
        Assert.assertEquals(
          "Lminimal/Hilt_BasicActivity<Lminimal/BasicActivityThing;>;",
          signatureAttr.signature
        )
      }
    }
  }

  companion object {
    const val TRANSFORM_TASK_NAME =
      ":transformDebugClassesWithAsm"
  }
}

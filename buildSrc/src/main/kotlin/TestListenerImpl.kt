import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

class TestListenerImpl : TestListener {

    override fun beforeSuite(suite: TestDescriptor?) {
        // none
    }

    override fun afterSuite(suite: TestDescriptor?, result: TestResult?) {
        // none
    }

    override fun beforeTest(testDescriptor: TestDescriptor?) {
        // none
    }

    override fun afterTest(testDescriptor: TestDescriptor?, result: TestResult?) {
        println(
            "Executing test ${testDescriptor?.name} " +
                "[${testDescriptor?.className}] " +
                "with result: ${result?.resultType}",
        )
    }
}

import org.gradle.api.Project
import java.io.File

object Util {

    /**
     * Dir for build-cache from settings.gradle.kts
     */
    fun Project.getBuildCacheDir() = File(layout.projectDirectory.asFile, "build-cache")
}

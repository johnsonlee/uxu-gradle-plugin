package io.johnsonlee.uxu

import com.didiglobal.booster.cha.ClassSet
import com.didiglobal.booster.transform.asm.isPublic
import com.didiglobal.booster.transform.asm.isStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import java.io.File

@Suppress("DEPRECATION")
class UxuPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.tasks.register("uxu", Jar::class.java) { task ->
            task.dependsOn(project.configurations.named("runtimeClasspath"))
            task.dependsOn(project.tasks.named("jar"))
            task.baseName = "${project.name ?: project.projectDir.name}-uxu"
            task.from(project.files(project.sourceSets.main.output))
            task.from(project.configurations.runtimeClasspath.asFileTree.files.map {
                if (it.isDirectory) {
                    project.fileTree(it)
                } else {
                    project.zipTree(it)
                }
            })
            task.manifest.apply {
                val projectOnly = project.files(project.sourceSets.main.output).filter(File::exists)
                val externalLibs = project.configurations.runtimeClasspath.asFileTree.files.filter(File::exists)
                attributes += "Main-Class" to findMainClass(projectOnly + externalLibs)
            }
            task.doLast {
                val jar = task.archiveFile.get().asFile
                val sh = File(jar.parentFile, jar.nameWithoutExtension + ".sh").apply {
                    if (exists()) {
                        delete()
                    }
                    createNewFile()
                    setReadable(true)
                    setWritable(true)
                    setExecutable(true)
                }

                sh.outputStream().buffered().use { output ->
                    """
                    #!/bin/sh
                    
                    exec java -jar $0 "$@"
                    
                    
                    """.trimIndent().byteInputStream().copyTo(output)
                    jar.inputStream().buffered().use { input ->
                        input.copyTo(output)
                    }
                }

                jar.delete()
                sh.setWritable(false)
            }
        }
    }

}

val Project.sourceSets: SourceSetContainer
    get() = (project as HasConvention).convention.getByName("sourceSets") as SourceSetContainer

val SourceSetContainer.main: SourceSet
    get() = named("main").get()

val ConfigurationContainer.runtimeClasspath: Configuration
    get() = named("runtimeClasspath").get()

fun findMainClass(classpath: Iterable<File>): String {
    val classSet = ClassSet.of(classpath.map(ClassSet.Companion::from)).load()
    return classSet.find { klass ->
        klass.methods.any { method ->
            method.isStatic && method.isPublic && method.name == "main"
        }
    }?.name?.replace("/", ".") ?: throw RuntimeException("Main class not found")
}
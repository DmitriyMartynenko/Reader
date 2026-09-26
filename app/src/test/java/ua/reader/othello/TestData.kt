package ua.reader.othello

import java.io.File

/** The real app data, loaded once for all tests (Gradle runs tests from the module directory). */
object TestData {
    val play: Play by lazy {
        Play.parse(
            File("src/main/assets/play.json").readText(),
            File("src/main/assets/characters.json").readText(),
            File("src/main/assets/pyramid.json").readText(),
        )
    }
}

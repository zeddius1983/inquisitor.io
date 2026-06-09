plugins {
    id("inquisitor.java-conventions")
    id("io.spring.dependency-management")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom(libs.spring.ai.bom.get().toString())
    }
}

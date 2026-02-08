plugins {
    id("java")
}

group = "at.tuwien"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(files("$projectDir/3rd-party-software/sge-1.0.4-dq-exe.jar", "$projectDir/3rd-party-software/Schnapsen-1.0-SNAPSHOT.jar"))
}

tasks.test {
    useJUnitPlatform()
}

var agent = "Random_Agent_Schnapsen"
var package_path = "random_agent"

tasks.jar {
    manifest {
        attributes(
            "Sge-Type" to "agent",
            "Agent-Class" to package_path + "." + agent,
            "Agent-Name" to agent,
        )
    }
}
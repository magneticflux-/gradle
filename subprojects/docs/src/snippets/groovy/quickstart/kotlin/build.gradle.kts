// tag::all[]
// tag::use-plugin[]
plugins {
    // end::use-plugin[]
    // end::all[]
    eclipse
// tag::all[]
// tag::use-plugin[]
    groovy
}
// end::use-plugin[]

// tag::groovy-dependency[]
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.codehaus.groovy:groovy-all:2.4.15")
// end::groovy-dependency[]
// end::all[]
    testImplementation("junit:junit:4.12")
// tag::all[]
// tag::groovy-dependency[]
}
// end::groovy-dependency[]
// end::all[]

/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package org.gradle.integtests.resolve.caching

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.ivy.IvyFileRepository

class CachingDependencyMetadataInMemoryIntegrationTest extends AbstractDependencyResolutionTest {

    def "version list, descriptor and artifact is cached in memory"() {
        given:
        mavenRepo.module("org", "lib").publish()

        file("build.gradle") << """
            configurations {
                one
                two
            }
            repositories {
                ivy { url "${mavenRepo.uri}" }
            }
            dependencies {
                one 'org:lib:1.+'
                two 'org:lib:1.+'
            }
            //runs first and resolves
            task resolveOne {
                doLast {
                    configurations.one.files
                }
            }
            //runs second, purges repo
            task purgeRepo(type: Delete, dependsOn: resolveOne) {
                delete "${mavenRepo.uri}"
            }
            //runs last, still works even thoug local repo is empty
            task resolveTwo(dependsOn: purgeRepo) {
                doLast {
                    println "Resolved " + configurations.two.files*.name
                }
            }
        """

        when:
        run "resolveTwo"

        then:
        output.contains 'Resolved [lib-1.0.jar]'
    }

    def "descriptors and artifacts are cached across projects and repositories"() {
        given:
        ivyRepo.module("org", "lib").publish()

        file("settings.gradle") << "include 'impl'"

        file("build.gradle") << """
            allprojects {
                configurations { conf }
                repositories { ivy { url "${ivyRepo.uri}" } }
                dependencies { conf 'org:lib:1.0' }
                task resolveConf { doLast { println path + " " + configurations.conf.files*.name } }
            }
            task purgeRepo(type: Delete, dependsOn: ':impl:resolveConf') {
                delete "${ivyRepo.uri}"
            }
            resolveConf.dependsOn purgeRepo
        """

        when:
        run "resolveConf"

        then:
        output.contains ':impl:resolveConf [lib-1.0.jar]'
        output.contains ':resolveConf [lib-1.0.jar]'
    }

    def "descriptors and artifacts are separated for different repositories"() {
        given:
        ivyRepo.module("org", "lib").publish()
        def ivyRepo2 = new IvyFileRepository(file("ivy-repo2"))
        ivyRepo2.module("org", "lib", "2.0").publish() //different version of lib

        file("settings.gradle") << "include 'impl'"

        file("build.gradle") << """
            allprojects {
                configurations { conf }
                dependencies { conf 'org:lib:1.0' }
                task resolveConf { doLast { println "\$path " + configurations.conf.files*.name } }
            }
            repositories { ivy { url "${ivyRepo.uri}" } }
            project(":impl") {
                repositories { ivy { url "${ivyRepo2.uri}" } }
                tasks.resolveConf.dependsOn(":resolveConf")
            }
        """

        when:
        runAndFail ":impl:resolveConf"

        then:
        output.contains ':resolveConf [lib-1.0.jar]'
        //uses different repo that does not contain this dependency
        failure.assertResolutionFailure(":impl:conf").assertHasCause("Could not find org:lib:1.0")
    }

    def "cache expires at the end of build"() {
        given:
        ivyRepo.module("org", "dependency").publish()
        ivyRepo.module("org", "lib").publish()

        file("build.gradle") << """
            configurations { conf }
            repositories { ivy { url "${ivyRepo.uri}" } }
            dependencies { conf 'org:lib:1.0' }
        """

        when:
        run "dependencies", "--configuration", "conf"

        then:
        !output.contains("org:dependency:1.0")

        when:
        ivyRepo.module("org", "lib").dependsOn("org", "dependency", "1.0").publish()
        run "dependencies", "--configuration", "conf"

        then:
        output.contains("org:dependency:1.0")
    }
}

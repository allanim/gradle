/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r212
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.model.eclipse.EclipseProject

class MultiProjectCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    def "can create composite of a two multi-project builds"() {
        given:
        def multiBuild1 = populate("multi-build-1") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'a1', 'b1', 'c1'
"""
        }

        def multiBuild2 = populate("multi-build-2") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'a2', 'b2', 'c2'
"""
        }

        expect:
        withCompositeConnection([ multiBuild1, multiBuild2 ]) { connection ->
            def models = connection.getModels(EclipseProject)
            assert models.size() == 8
            containsProjects(models, [':', ':a1', ':b1', ':c1', ':', ':a2', ':b2', ':c2'])
            assert rootProjects(models).size() == 2
        }
    }

    def "can create composite of a two single-project builds"() {
        given:
        def singleBuild1 = populate("single-build-1") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
"""
        }

        def singleBuild2 = populate("single-build-2") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
"""
        }

        expect:
        withCompositeConnection([ singleBuild1, singleBuild2 ]) { connection ->
            def models = connection.getModels(EclipseProject)
            assert models.size() == 2
            containsProjects(models, [':', ':'])
            assert rootProjects(models).size() == 2
        }
    }

    def "can create composite of a single-project and multi-project builds"() {
        given:
        def singleBuild = populate("single-build-1") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
"""
        }

        def multiBuild = populate("multi-build-1") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'a1', 'b1', 'c1'
"""
        }

        expect:
        withCompositeConnection([ singleBuild, multiBuild ]) { connection ->
            def models = connection.getModels(EclipseProject)
            assert models.size() == 5
            containsProjects(models, [':', ':', ':a1', ':b1', ':c1'])
            assert rootProjects(models).size() == 2
        }
    }

    def "sees changes to composite build when projects are added"() {
        given:
        def singleBuild = populate("single-build") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
"""
        }
        def multiBuild = populate("multi-build-1") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'group'
                    version = '1.0'
                }
"""
            settingsFile << """
                rootProject.name = '${rootProjectName}'
                include 'a1', 'b1', 'c1'
"""
        }
        def composite = createComposite(singleBuild, multiBuild)

        when:
        def firstRetrieval = composite.getModels(EclipseProject)

        then:
        firstRetrieval.size() == 5
        assert rootProjects(firstRetrieval).size() == 2
        containsProjects(firstRetrieval, [':', ':', ':a1', ':b1', ':c1'])

        when:
        // make single-project a multi-project build
        populate("single-build") {
            settingsFile << """
                include 'a2'
"""
        }
        and:
        def secondRetrieval = composite.getModels(EclipseProject)

        then:
        secondRetrieval.size() == 6
        assert rootProjects(secondRetrieval).size() == 2
        containsProjects(secondRetrieval, [':', ':a2', ':', ':a1', ':b1', ':c1'])

        when:
        // adding more projects to multi-project build
        populate("single-build") {
            file("settings.gradle") << "include 'b2', 'c2'"
        }
        and:
        def thirdRetrieval = composite.getModels(EclipseProject)

        then:
        thirdRetrieval.size() == 8
        assert rootProjects(thirdRetrieval).size() == 2
        containsProjects(thirdRetrieval, [':', ':a2', ':b2', ':c2', ':', ':a1', ':b1', ':c1'])

        when:
        // remove one participant
        singleBuild.deleteDir()

        and:
        def fourthRetrieval = composite.getModels(EclipseProject)

        then:
        def e = thrown(BuildException)
        e.getMessage().contains("Could not fetch model of type 'EclipseProject'")
        def underlyingCause = e.getCause().getCause()
        // TODO: Should get a GradleCompositeException
        underlyingCause.getMessage().contains("single-build' does not exist")

        cleanup:
        composite?.close()
    }

    Set<EclipseProject> rootProjects(Set<EclipseProject> projects) {
        projects.findAll { it.parent == null }
    }

    void containsProjects(models, projects) {
        def projectsFoundByPath = models.collect { it.gradleProject.path }
        assert projectsFoundByPath.containsAll(projects)
    }
}

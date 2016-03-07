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

package org.gradle.plugin.devel.plugins.internal.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GUtil

import static PluginUnderTestMetadata.IMPLEMENTATION_CLASSPATH_PROP_KEY
import static PluginUnderTestMetadata.METADATA_FILE_NAME

class PluginUnderTestMetadataIntegrationTest extends AbstractIntegrationSpec {

    private static final String TASK_NAME = 'pluginClasspathManifest'

    def setup() {
        buildFile << """
            apply plugin: 'java'
        """
    }

    def "fails the task for null plugin classpath"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
                pluginClasspath = null
            }
        """

        when:
        fails TASK_NAME

        then:
        failure.assertHasCause("No value has been specified for property 'pluginClasspath'.")
    }

    def "fails the task for null output directory"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
                outputDirectory = null
            }
        """

        when:
        fails TASK_NAME

        then:
        failure.assertHasCause("No value has been specified for property 'outputDirectory'.")
    }

    def "fails the task if main source set does not exist"() {
        given:
        buildFile << """
            sourceSets.remove(sourceSets.main)

            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()})
        """

        when:
        fails TASK_NAME

        then:
        failure.assertHasCause("No value has been specified for property 'pluginClasspath'.")
    }

    def "implementation-classpath entry in metadata is empty if there is no classpath"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
                pluginClasspath = files()
            }
        """

        when:
        succeeds TASK_NAME

        then:
        def classpathManifest = file("build/$TASK_NAME/$METADATA_FILE_NAME")
        classpathManifest.exists() && classpathManifest.isFile()
        !GUtil.loadProperties(classpathManifest).containsKey(IMPLEMENTATION_CLASSPATH_PROP_KEY)
    }

    def "can reconfigure output directory for metadata file"() {
        given:
        buildFile << """
            task $TASK_NAME(type: ${PluginUnderTestMetadata.class.getName()}) {
                outputDirectory = file('build/some/other')
            }
        """

        when:
        succeeds TASK_NAME

        then:
        file("build/some/other/$METADATA_FILE_NAME").exists()
    }

}
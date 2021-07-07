/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


pipeline {
    agent any
    triggers {
        pollSCM('H/5 * * * * ')
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '3'))
        disableConcurrentBuilds() 
    }
    stages{
        stage("Build with jdk 8 ") {
            agent { node { label 'ubuntu' } }
            options { timeout(time: 120, unit: 'MINUTES') }                
            steps{
                mavenBuild( 'jdk_1.8_latest', 'clean install deploy', 'maven_3.6.3', [artifactsPublisher(disabled: false)])        
            }
        }
    }
    post {
        always {
            cleanWs() // deleteDirs: true, notFailBuild: true, patterns: [[pattern: '**/.repository/**', type: 'INCLUDE']]
        }
        unstable {
            script{
                notifyBuild( "Unstable Build ")
            }
        }
        failure {
            script{
                notifyBuild( "Error in build ")
            }
        }
        success {
            script {
                def previousResult = currentBuild.previousBuild?.result
                if (previousResult && !currentBuild.resultIsWorseOrEqualTo( previousResult ) ) {
                    notifyBuild( "Fixed" )
                }
            }
        }
    }
}


def mavenBuild(jdk, cmdline, mvnName, publishers) {
    def localRepo = "../.maven_repositories/${env.EXECUTOR_NUMBER}" // ".repository" //
    //def settingsName = 'archiva-uid-jenkins'
    def mavenOpts = '-Xms1g -Xmx4g -Djava.awt.headless=true'

    withMaven(
        maven: mvnName,
        jdk: "$jdk",
        options: publishers,
        publisherStrategy: 'EXPLICIT',
        //globalMavenSettingsConfig: settingsName,
        mavenOpts: mavenOpts,
        mavenLocalRepo: localRepo) {
        // Some common Maven command line + provided command line
        sh "mvn -V -B -U -e -DskipBrowserTests -Dmaven.test.failure.ignore=true $cmdline "
   }
}

def notifyBuild(String buildStatus) {
    // default the value
    buildStatus = buildStatus ?: "UNKNOWN"
    def color
    if (buildStatus == 'STARTED') {
        color = '#F0F0F0'
    } else if (buildStatus == 'SUCCESS' || buildStatus=='Fixed') {
        color = '#00FF00'
    } else if (buildStatus == 'UNSTABLE') {
        color = '#ffff50'
    } else if (buildStatus == 'UNKNOWN') {
        color = '#a0a0a0'
    }else {
        color = '#FF0000'
    }
    slackSend (channel:'#netbeans-builds', message:"${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL}) ",color: color)
     

     
}

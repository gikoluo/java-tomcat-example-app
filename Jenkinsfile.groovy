#!groovy 

// Jenkinsfile
def projectName = env.PROJECT_NAME   //Project name, Usually it is the name of jenkins project folder name.
def serviceName = env.SERVICE_NAME   //Service name. Usually it is the process name running in the server.
def archiveFile = env.ARCHIVE_FILE
//def branchName = env.BRANCH_NAME     //Branch name. And the project must be multibranch pipeline, Or set the env in config
def branchName
def hubCredential=env.HUB_CREDENTIAL


def namespace = "swr.cn-east-2.myhuaweicloud.com"
def org = "greenland"
def imageName
def version 
def tag
def archiveFlatName 

pipeline {
  agent {
    kubernetes {
      // this label will be the prefix of the generated pod's name
      //label '${projectName}-${serviceName}'
      defaultContainer 'jnlp'
      yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    component: ci
spec:
  containers:
  - name: jnlp
    image: 'jenkins/jnlp-slave:3.27-1-alpine'
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
  - name: docker
    image: docker
    command:
    - cat
    tty: true
    volumeMounts:
      - mountPath: /var/run/docker.sock
        name: docker-sock
  - name: maven
    image: maven:3-jdk-8
    command:
    - cat
    tty: true
  - name: kubectl
    image: lachlanevenson/k8s-kubectl:v1.14.6
    command:
      - cat
    tty: true
  volumes:
    - name: docker-sock
      hostPath:
        path: /var/run/docker.sock
"""
    }
  }

  stages {
    stage('Build image') {
      steps {
        script {
          sh 'git rev-parse HEAD > commit'

          imageName = "${projectName}-${serviceName}"
          version = readFile('commit').trim()
          tag = "${namespace}/${org}/${imageName}:${version}"

          archiveFlatName = sh (
              script: "basename ${archiveFile}",
              returnStdout: true
          ).trim()
        }
        

        container('docker') {
          withCredentials([[$class: 'UsernamePasswordMultiBinding',
            credentialsId: "${hubCredential}",
            usernameVariable: 'DOCKER_HUB_USER',
            passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
            sh """
              docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD} ${namespace}

              docker build -t ${tag} . && \
              docker push ${tag}
              """

            echo "Extract the Archive File : ${archiveFile} to ${archiveFlatName}"

            script {
              def image = docker.image("${tag}")
              image.inside {
                 sh 'date > /tmp/test.txt'
                 sh "cp /tmp/test.txt ${WORKSPACE}"
                 archiveArtifacts 'test.txt'

                sh "ls -l ${archiveFile}"
                archiveArtifacts "${archiveFile}"

              }
            }

            // sh """
            //   #docker run -v /tmp:/Archive --rm --entrypoint cp ${tag} ${archiveFile} /Archive/${archiveFlatName}

            //   docker run --rm --entrypoint cat ${tag} ${archiveFile} > /tmp/${archiveFlatName}

            //   ls -l /tmp
            //   """

            // archiveArtifacts artifacts: "/tmp/${archiveFlatName}"
          }

          
        }
      }
    }

    stage('Deploy') {
      steps {
        container('kubectl') {
            sh "kubectl version"
          // sh "kubectl delete -f ./kubernetes/deployment.yaml"
          // sh "kubectl apply -f ./kubernetes/deployment.yaml"
          // sh "kubectl apply -f ./kubernetes/service.yaml"
        }
      }
    }
  }
}

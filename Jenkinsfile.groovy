#!groovy 

// Jenkinsfile
def projectName = env.PROJECT_NAME   //Project name, Usually it is the name of jenkins project folder name.
def serviceName = env.SERVICE_NAME   //Service name. Usually it is the process name running in the server.
def archiveFile = env.ARCHIVE_FILE
//def branchName = env.BRANCH_NAME     //Branch name. And the project must be multibranch pipeline, Or set the env in config
def branchName
def hubCredential=env.HUB_CREDENTIAL
def k8sNS=env.K8S_NAMESPACE

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
    stage('Init') {
      steps {
        container('docker') {
          withCredentials([[$class: 'UsernamePasswordMultiBinding',
            credentialsId: "${hubCredential}",
            usernameVariable: 'DOCKER_HUB_USER',
            passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
            sh """
              docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD} ${namespace}
              """
          }
        }

        container('kubectl') {
          withKubeConfig([credentialsId: 'kubeconfig-uat']) {
            sh 'kubectl get namespaces'
            sh "kubectl config set-context --current --namespace=${k8sNS}-uat"
          }
        }

        script {
          sh 'git rev-parse HEAD > commit'

          imageName = "${projectName}-${serviceName}"
          version = readFile('commit').trim()
          tag = "${namespace}/${org}/${imageName}"

          archiveFlatName = sh (
              script: "basename ${archiveFile}",
              returnStdout: true
          ).trim()
        }
      }
    }

    stage('Build image') {
      steps {
        container('docker') {
          script {
            def customImage = docker.build("${tag}:${version}")
            customImage.push()
          }
          // sh """
          //   docker build -t ${tag}:${version} . && \
          //   docker push ${tag}:${version}
          //   """
        }
      }
    }

    stage('QA') {
      steps {
        container('docker') {
          echo "Run Sonar Analytics"

          sh """
            docker build --target sonarqube -t ${tag}:sonarqube . 
            """

          script {
            docker.image("${tag}:sonarqube"){

            }
          }
        }
      }
    }

    stage('Archive File') {
      steps {
        container('docker') {
          echo "Extract the Archive File : ${archiveFile} to ${archiveFlatName}"

          script {
            def image = docker.image("${tag}:${version}")
            image.inside {
              sh "cp ${archiveFile} ${WORKSPACE}/${archiveFlatName}"
              archiveArtifacts "${archiveFlatName}"
            }
          }
        }
      }
    }

    stage('Deploy To UAT') {
      steps {
        container('docker') {
          sh """
              docker tag ${tag}:${version} ${tag}:uat
              docker push ${tag}:uat
              """
          // withCredentials([[$class: 'UsernamePasswordMultiBinding',
          //   credentialsId: "${hubCredential}",
          //   usernameVariable: 'DOCKER_HUB_USER',
          //   passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
          //   sh """
          //     docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD} ${namespace}
          //     docker tag ${tag} ${tag_uat}
          //     docker push ${tag_uat}
          //     """

          //   script {
          //     tag_uat = "${namespace}/${org}/${imageName}:uat"

          //     def image = docker.image("${tag}")
          //     image.inside {
          //       sh "cp ${archiveFile} ${WORKSPACE}/${archiveFlatName}"
          //       archiveArtifacts "${archiveFlatName}"
          //     }
          //   }
          // }
        }

        container('kubectl') {
          sh 'kubectl apply -f ./kubernetes/deployment.yaml'
          //  sh "kubectl version"
          // sh "kubectl delete -f ./kubernetes/deployment.yaml"
          // sh "kubectl apply -f ./kubernetes/deployment.yaml"
          // sh "kubectl apply -f ./kubernetes/service.yaml"
        }
      }
    }


    stage('Deploy To Production') {
      steps {
        script {
          tag_prod = "${namespace}/${org}/${imageName}:prod"
        }

        container('docker') {
          sh """
              docker tag ${tag_uat} ${tag_prod}
              docker push ${tag_prod}
              """
          // withCredentials([[$class: 'UsernamePasswordMultiBinding',
          //   credentialsId: "${hubCredential}",
          //   usernameVariable: 'DOCKER_HUB_USER',
          //   passwordVariable: 'DOCKER_HUB_PASSWORD']]) {
          //   sh """
          //     docker login -u ${DOCKER_HUB_USER} -p ${DOCKER_HUB_PASSWORD} ${namespace}
          //     docker tag ${tag} ${tag_uat}
          //     docker push ${tag_uat}
          //     """

          //   script {
          //     tag_uat = "${namespace}/${org}/${imageName}:uat"

          //     def image = docker.image("${tag}")
          //     image.inside {
          //       sh "cp ${archiveFile} ${WORKSPACE}/${archiveFlatName}"
          //       archiveArtifacts "${archiveFlatName}"
          //     }
          //   }
          // }
        }

        // container('kubectl') {
        //   withKubeConfig([credentialsId: 'kubeconfig-prod']) {
        //     sh 'kubectl get namespaces'
        //     sh 'kubectl apply -f ./kubernetes/deployment.yaml'
        //   }
        //   //  sh "kubectl version"
        //   // sh "kubectl delete -f ./kubernetes/deployment.yaml"
        //   // sh "kubectl apply -f ./kubernetes/deployment.yaml"
        //   // sh "kubectl apply -f ./kubernetes/service.yaml"
        // }
      }
    }
  }
}
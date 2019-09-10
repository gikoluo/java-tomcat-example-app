#!groovy 

// Jenkinsfile
def projectName = env.PROJECT_NAME   //Project name, Usually it is the name of jenkins project folder name.
def serviceName = env.SERVICE_NAME   //Service name. Usually it is the process name running in the server.
def branchName = env.BRANCH_NAME     //Branch name. And the project must be multibranch pipeline, Or set the env in config


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
        container('jnlp') {
          sh "docker build -t ${projectName}-${serviceName}:${branchName} ."
          sh "docker push ${projectName}-${serviceName}:${branchName}"
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

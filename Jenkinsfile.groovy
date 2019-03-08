#!groovy

@Library('luochunhui')
import com.luochunhui.lib.Utilities
import com.luochunhui.lib.Remote

def utils = new Utilities(steps)

targetFile="target/hellowar.war"
playbook= "samples/hellowar"
remoteUser=""
test_url = ""
uat_url = ""
prod_url = ""


milestone 1
stage('Dev') {
    node {
        checkout scm
        utils.maven 'compile war:war'

        dir(".") {
            archiveArtifacts artifacts:targetFile, fingerprint: true
            stash name:'targetArchive', includes:targetFile
        }
    }
}


/*
milestone 2
stage('Quick Test') {
    node {
        deploy( targetFile, playbook, 'uat' )
    }
}
*/


// NO test enviroment now
// milestone 2
// stage('Deploy Test') {
//     node {
//         remote = new Remote(steps, 'test', remoteUser)
//         remote.deployAnsible( targetFile )
//     }
// }

milestone 3
stage('UAT') {
    lock(resource: "${playbook}-UAT", inversePrecedence: true) {
        milestone 3
        timeout(time:1, unit:'DAYS') {
            input id: 'pushToUAT', message: "Test环境正常了么？可以提交 UAT 了吗?", ok: '准备好了，发布！', submitter: 'sa'
        }

        node {
            echo 'UAT deploy start'
            remote = new Remote(steps, 'uat', remoteUser)
            remote.deployAnsible( targetFile )
            echo "UAT deployed"
        }
        
        timeout(time:1, unit:'DAYS') {
            input message: " UAT 通过了吗? ${uat_url} ", ok: '通过！', submitter: 'sa'
        }
    }
}


milestone 4
stage ('Production') {
    lock(resource: "${playbook}-Production", inversePrecedence: true) {
        timeout(time:1, unit:'DAYS') {
            input message: "可以提交 Prod 了吗?", ok: '准备好了，发布！', submitter: 'sa'
        }
        
        node {
            echo 'Production deploy status'
            remote = new Remote(steps, 'prod', remoteUser)
            remote.deployAnsible( targetFile )
            echo "Production deployed"
        }
        
        timeout(time:1, unit:'DAYS') {
            input message: "Prod测试完成了吗? ${prod_url} ", ok: '通过！下班，困觉！', submitter: 'sa'
        }
    }
}








def call(body) {

    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    if (config.directory == null) {
        config.directory = '.'
    }

    if (config.dockerBuilds == null) {
        config.dockerBuilds = [
                (config.imageName): config.directory
        ]
    }

    node {
        properties([
            pipelineTriggers([
                pollSCM('*/5 * * * *')
            ])
        ])

        try {

            stage('Checkout SCM') {
                checkout scm
            }

            dir("${config.directory}") {

                mavenBuild {
                    directory = config.directory
                }

                echo "Build Result is: ${currentBuild.result}"
                if (currentBuild.result == null) {
                    def builds = [:]
                    for (x in config.dockerBuilds.keySet()) {
                        def image = x
                        builds[image] = {
                            echo "Image Name: ${image}"
                            dockerBuild {
                                directory = config.dockerBuilds[image]
                                imageName = image
                            }
                        }
                    }

                    parallel builds

                    if (!isPullRequest() && config.testEnvironment != null) {
                        //Deploy to CI for automated testing
                        deployStack {
                            composeFiles = config.testEnvironment
                            dockerHost = env.CI_DOCKER_SWARM_MANAGER
                        }

                        //TODO Launch Test cases here

                        undeployStack {}
                    }
                }
            }
        } finally {
            //Send build notifications if needed
            notifyBuild(currentBuild.result)
        }
    }

    // mavenRelease {
    //     directory = config.directory
    // }

}
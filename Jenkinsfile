pipeline {
  agent any

  environment {
    DEPLOY_USER = 'jenkins'
    DEPLOY_HOST = '192.168.1.81'
    APP_DIR = '/home/jenkins/webhostingservice'
  }

  stages {

    stage('Fetch code') {
      steps {
        git(
          url: 'https://github.com/mohrnd/WebHostingService.git',
          branch: 'main',
          credentialsId: 'github-api-token'
        )
      }
    }

    stage('Build JAR') {
      steps {
        sh 'mvn clean package -DskipTests'
      }
    }

    stage('Copy files to Docker LXC') {
      steps {
        sh '''
          ssh $DEPLOY_USER@$DEPLOY_HOST "mkdir -p $APP_DIR"
          scp target/webhostingservice-1.0.0.jar $DEPLOY_USER@$DEPLOY_HOST:$APP_DIR/
          scp Dockerfile $DEPLOY_USER@$DEPLOY_HOST:$APP_DIR/
        '''
      }
    }

    stage('Build & Run Docker remotely') {
      steps {
        withCredentials(bindings: [
          string(credentialsId: 'DB_URL', variable: 'DB_URL'),
          string(credentialsId: 'DB_USER', variable: 'DB_USER'),
          string(credentialsId: 'DB_PASSWORD', variable: 'DB_PASSWORD'),
          string(credentialsId: 'JWT_SECRET', variable: 'JWT_SECRET'),
          string(credentialsId: 'MONGO_URI', variable: 'MONGO_URI')
        ]) {
          sh '''
            ssh $DEPLOY_USER@$DEPLOY_HOST "
              cd $APP_DIR &&
              docker rm -f webhostingservice || true &&
              docker build -t webhostingservice:latest . &&
              docker run -d --name webhostingservice -p 8080:8080 \\
                -v /var/run/docker.sock:/var/run/docker.sock \\
                -e DB_URL=\\"$DB_URL\\" \\
                -e DB_USER=\\"$DB_USER\\" \\
                -e DB_PASSWORD=\\"$DB_PASSWORD\\" \\
                -e JWT_SECRET=\\"$JWT_SECRET\\" \\
                -e MONGO_URI=\\"$MONGO_URI\\" \\
                webhostingservice:latest
            "
          '''
        }
      }
    }

stage('Run Integration Tests') {
  steps {
    withCredentials([
      string(credentialsId: 'ADMIN_EMAIL', variable: 'ADMIN_EMAIL'),
      string(credentialsId: 'ADMIN_PASSWORD', variable: 'ADMIN_PASSWORD')
    ]) {
      sh '''#!/usr/bin/env bash
        set -euo pipefail
        echo "Running integration tests..."

        chmod +x scripts/webhosting_test.sh

        BASE_URL="http://$DEPLOY_HOST:8080" \
        ADMIN_EMAIL="$ADMIN_EMAIL" \
        ADMIN_PASSWORD="$ADMIN_PASSWORD" \
        scripts/webhosting_test.sh

        echo "✅ All integration tests passed successfully."
      '''
    }
  }
}
stage('Deploy Web Frontend') {
  steps {
    sh '''
      echo "Deploying frontend web app..."
      
      # Create web directory on remote host
      ssh $DEPLOY_USER@$DEPLOY_HOST "mkdir -p $APP_DIR/web"
      
      # Copy web files to remote host
      scp -r web/* $DEPLOY_USER@$DEPLOY_HOST:$APP_DIR/web/
      
      # Build and run frontend container
      ssh $DEPLOY_USER@$DEPLOY_HOST "
        cd $APP_DIR/web &&
        docker rm -f webhosting_frontend || true &&
        docker build -t webhosting_frontend:latest . &&
        docker run -d --name webhosting_frontend -p 80:80 --restart unless-stopped webhosting_frontend:latest
      "
      
      echo "Frontend deployed successfully on port 80"
    '''
  }
}


  }

  post {
    success {
      echo '✅ Pipeline completed successfully.'
    }
    failure {
      echo '❌ Pipeline failed.'
    }
    always {
      echo 'Pipeline finished.'
    }
  }
}

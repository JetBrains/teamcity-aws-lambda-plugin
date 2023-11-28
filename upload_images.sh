#!/usr/bin/env bash

check_repo_exist(){
  repository_name=$1

  _=$(aws ecr describe-repositories --repository-name $repository_name)
  exit_code=$?
  echo $exit_code
}

create_repo(){
    repository_name=$1

    _=$(aws ecr create-repository --repository-name $repository_name)
}

upload_images(){
  region=$1
  image_name=$2
  version_tag=$3
  ./generate_images.sh

  account_id=$(aws sts get-caller-identity --query "Account" --output text)
  ecr_repo_prefix="$account_id.dkr.ecr.$region.amazonaws.com"
  repository_name="teamcity-lambda-runner-$image_name"
  docker_tag="$ecr_repo_prefix/$repository_name:$version_tag"

  aws ecr get-login-password --region $region | docker login --username AWS --password-stdin 913206223978.dkr.ecr.eu-west-1.amazonaws.com

  exit_code=$(check_repo_exist $repository_name)
  if [[ $exit_code != 0 ]];  then
      create_repo $repository_name
  fi

  set -e
  ./gradlew :aws-lambda-function:assemble

  docker build -t $docker_tag -f "images/build/Dockerfile-$image_name" .

  docker push $docker_tag
}

upload_images $@
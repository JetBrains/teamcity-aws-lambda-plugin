#!/usr/bin/env bash

generate_image(){
  image_name=$1
  images_folder="images"

  mkdir -p "$images_folder/build"
  cat "$images_folder/Dockerfile-common" "$images_folder/Dockerfile-$image_name" > "$images_folder/build/Dockerfile-$image_name"
}

generate_image gradle
generate_image maven
generate_image node14
generate_image python3_10
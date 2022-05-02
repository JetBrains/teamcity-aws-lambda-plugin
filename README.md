# TeamCity AWS Lambda plugin [![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

Teamcity plugin which allows running build agents on AWS Lambda.


## General Info 
| <!-- --> | <!-- -->                                                 |
|----------|----------------------------------------------------------|
| Author   | Andre Rocha / JetBrains                                  |
| License  | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| Type     | free, open-source                                        |
| Status   | Beta                                                     |

## Compatbility

The plugin is compatible with TeamCity 2021.2 and later.

## Installation
Download the required version from [JetBrains Marletplace](https://plugins.jetbrains.com/plugin/19023-aws-lambda/versions).

## Required IAM Role
The following IAM actions are required from the role assigned to the lambda function:
- lambda:UpdateFunctionConfiguration
- lambda:UpdateFunctionCode
- lambda:InvokeFunction
- lambda:GetFunction
- lambda:CreateFunction
- s3:CreateBucket
- s3:ListBucket
- s3:GetObject
- s3:PutObject
- iam:AttachRolePolicy
- iam:CreateRole
- iam:GetRole
- iam:ListRoles
- iam:PassRole

## Template Images
The project currently holds several template container images in the `images` folder. These can be used within a build step. Due to the limitations of AWS Lambda, this container must be available through a private ECR. In order to speed up this process,  the `upload_images.sh` script is provided that can be used in the following way:

```bash
./upload_images.sh <region> <image_name>

```

Where `region` is the AWS region where this image should be pushed to and `image_name` is the name of the image to build, which can currently be:
- gradle
- maven
- node14
- node16
- python3_10

The execution of the script assumes that the AWS CLI has been installed and that it is logged in into the account.

## License

Apache 2.0

## Feedback

Please feel free to post feedback in the repository [issues](https://youtrack.jetbrains.com/issues/TW).

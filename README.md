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
TBD

## Required IAM Role
The following IAM actions are required from the role assigned to the lambda function:
- lambda:UpdateFunctionConfiguration
- lambda:UpdateFunctionCode
- lambda:Invoke
- lambda:GetFunction
- lambda:CreateFunction
- s3:CreateBucket
- s3:GetObject
- s3:PutObject
- iam:AttachRolePolicy
- iam:CreateRole
- iam:GetRole
- iam:ListRoles

## License

Apache 2.0

## Feedback

Please feel free to post feedback in the repository [issues](https://youtrack.jetbrains.com/issues/TW).
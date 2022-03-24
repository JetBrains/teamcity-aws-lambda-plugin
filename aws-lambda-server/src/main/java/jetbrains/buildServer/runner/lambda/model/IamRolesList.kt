package jetbrains.buildServer.runner.lambda.model

data class IamRole(val roleArn: String, val roleName: String)
data class IamRolesList(val iamRoleList: List<IamRole>, val defaultRole: IamRole?)
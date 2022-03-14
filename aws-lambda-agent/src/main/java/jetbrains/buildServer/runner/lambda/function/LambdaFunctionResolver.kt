package jetbrains.buildServer.runner.lambda.function

interface LambdaFunctionResolver {
    fun resolveFunction(): String

    companion object {
        const val ARN_POLICY = "{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Sid\": \"VisualEditor0\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": \"lambda:*\",\n" +
                "            \"Resource\": \"arn:aws:lambda:*:913206223978:function:*\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"Sid\": \"VisualEditor1\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": \"lambda:*\",\n" +
                "            \"Resource\": \"arn:aws:lambda:*:913206223978:function:*:*\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"Sid\": \"VisualEditor2\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": [\n" +
                "                \"s3:GetObjectVersionTagging\",\n" +
                "                \"s3:GetStorageLensConfigurationTagging\",\n" +
                "                \"s3:GetObjectAcl\",\n" +
                "                \"s3:GetBucketObjectLockConfiguration\",\n" +
                "                \"s3:GetIntelligentTieringConfiguration\",\n" +
                "                \"s3:GetObjectVersionAcl\",\n" +
                "                \"s3:GetBucketPolicyStatus\",\n" +
                "                \"s3:GetObjectRetention\",\n" +
                "                \"s3:GetBucketWebsite\",\n" +
                "                \"s3:GetJobTagging\",\n" +
                "                \"s3:GetMultiRegionAccessPoint\",\n" +
                "                \"s3:GetObjectAttributes\",\n" +
                "                \"s3:GetObjectLegalHold\",\n" +
                "                \"s3:GetBucketNotification\",\n" +
                "                \"s3:DescribeMultiRegionAccessPointOperation\",\n" +
                "                \"s3:GetReplicationConfiguration\",\n" +
                "                \"s3:ListMultipartUploadParts\",\n" +
                "                \"s3:GetObject\",\n" +
                "                \"s3:DescribeJob\",\n" +
                "                \"s3:GetAnalyticsConfiguration\",\n" +
                "                \"s3:GetObjectVersionForReplication\",\n" +
                "                \"s3:GetAccessPointForObjectLambda\",\n" +
                "                \"s3:GetStorageLensDashboard\",\n" +
                "                \"s3:GetLifecycleConfiguration\",\n" +
                "                \"s3:GetInventoryConfiguration\",\n" +
                "                \"s3:GetBucketTagging\",\n" +
                "                \"s3:GetAccessPointPolicyForObjectLambda\",\n" +
                "                \"s3:GetBucketLogging\",\n" +
                "                \"s3:ListBucketVersions\",\n" +
                "                \"s3:ListBucket\",\n" +
                "                \"s3:GetAccelerateConfiguration\",\n" +
                "                \"s3:GetObjectVersionAttributes\",\n" +
                "                \"s3:GetBucketPolicy\",\n" +
                "                \"s3:GetEncryptionConfiguration\",\n" +
                "                \"s3:GetObjectVersionTorrent\",\n" +
                "                \"s3:GetBucketRequestPayment\",\n" +
                "                \"s3:GetAccessPointPolicyStatus\",\n" +
                "                \"s3:GetObjectTagging\",\n" +
                "                \"s3:GetMetricsConfiguration\",\n" +
                "                \"s3:GetBucketOwnershipControls\",\n" +
                "                \"s3:GetBucketPublicAccessBlock\",\n" +
                "                \"s3:GetMultiRegionAccessPointPolicyStatus\",\n" +
                "                \"s3:ListBucketMultipartUploads\",\n" +
                "                \"s3:GetMultiRegionAccessPointPolicy\",\n" +
                "                \"s3:GetAccessPointPolicyStatusForObjectLambda\",\n" +
                "                \"s3:GetBucketVersioning\",\n" +
                "                \"s3:GetBucketAcl\",\n" +
                "                \"s3:GetAccessPointConfigurationForObjectLambda\",\n" +
                "                \"s3:GetObjectTorrent\",\n" +
                "                \"s3:GetStorageLensConfiguration\",\n" +
                "                \"lambda:*\",\n" +
                "                \"s3:GetBucketCORS\",\n" +
                "                \"s3:GetBucketLocation\",\n" +
                "                \"s3:GetAccessPointPolicy\",\n" +
                "                \"s3:GetObjectVersion\"\n" +
                "            ],\n" +
                "            \"Resource\": [\n" +
                "                \"arn:aws:lambda:*:913206223978:function:*:*\",\n" +
                "                \"arn:aws:s3:::*\"\n" +
                "            ]\n" +
                "        },\n" +
                "        {\n" +
                "            \"Sid\": \"VisualEditor3\",\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": [\n" +
                "                \"s3:ListAccessPointsForObjectLambda\",\n" +
                "                \"s3:GetAccessPoint\",\n" +
                "                \"lambda:ListFunctions\",\n" +
                "                \"s3:ListAccessPoints\",\n" +
                "                \"s3:ListJobs\",\n" +
                "                \"lambda:GetAccountSettings\",\n" +
                "                \"lambda:CreateEventSourceMapping\",\n" +
                "                \"s3:ListMultiRegionAccessPoints\",\n" +
                "                \"s3:ListStorageLensConfigurations\",\n" +
                "                \"s3:GetAccountPublicAccessBlock\",\n" +
                "                \"s3:ListAllMyBuckets\",\n" +
                "                \"lambda:ListEventSourceMappings\",\n" +
                "                \"lambda:ListLayerVersions\",\n" +
                "                \"lambda:ListLayers\",\n" +
                "                \"lambda:ListCodeSigningConfigs\"\n" +
                "            ],\n" +
                "            \"Resource\": \"*\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": \"logs:CreateLogGroup\",\n" +
                "            \"Resource\": \"arn:aws:logs:*\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Action\": [\n" +
                "                \"logs:CreateLogStream\",\n" +
                "                \"logs:PutLogEvents\"\n" +
                "            ],\n" +
                "            \"Resource\": [\n" +
                "                \"arn:aws:logs:*\"\n" +
                "            ]\n" +
                "        }"+
                "    ]\n" +
                "}"

        const val ROLE_POLICY_DOCUMENT = "{\n" +
                "    \"Version\": \"2012-10-17\",\n" +
                "    \"Statement\": [\n" +
                "        {\n" +
                "            \"Effect\": \"Allow\",\n" +
                "            \"Principal\": {\n" +
                "                \"Service\": \"lambda.amazonaws.com\"\n" +
                "            },\n" +
                "            \"Action\": \"sts:AssumeRole\"\n" +
                "        }\n" +
                "    ]\n" +
                "}"
    }
}
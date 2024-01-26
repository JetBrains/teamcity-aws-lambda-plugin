
<%@include file="paramsConstants.jspf"%>

<jsp:include page="viewAWSCommonParams.jsp"/>


<div class="parameter">
    ${endpoint_url_label}: <props:displayValue name="${endpoint_url_param}" emptyValue="default"/>
</div>

<div class="parameter">
    ${ecr_image_label}: <props:displayValue name="${ecr_image_param}" emptyValue="default"/>
</div>

<div class="parameter">
    ${memory_size_label}: <props:displayValue name="${memory_size_param}" emptyValue="default"/>
</div>

<div class="parameter">
    ${storage_size_label}: <props:displayValue name="${storage_size_param}" emptyValue="default"/>
</div>

<div class="parameter">
    ${iam_role_label}: <props:displayValue name="${iam_role_param}" emptyValue="default"/>
</div>

<div class="parameter">
    Custom script: <props:displayValue name="script.content" emptyValue="<empty>" showInPopup="true" popupTitle="Script content" popupLinkText="view script content"/>
</div>
<%--
  ~ Copyright 2000-2021 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
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
    ${iam_role_label}: <props:displayValue name="${iam_role_param}" emptyValue="default"/>
</div>

<div class="parameter">
    Custom script: <props:displayValue name="script.content" emptyValue="<empty>" showInPopup="true" popupTitle="Script content" popupLinkText="view script content"/>
</div>

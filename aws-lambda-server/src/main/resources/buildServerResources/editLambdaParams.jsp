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

<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>

<%@include file="paramsConstants.jspf"%>

<jsp:include page="editAWSCommonParams.jsp">
    <jsp:param name="requireRegion" value="${true}"/>
    <jsp:param name="requireEnvironment" value="${false}"/>
</jsp:include>


<l:settingsGroup title="Lambda Settings">
    <tr data-steps="${lambda_settings_step}">
        <th><label for="${endpoint_url_param}">${endpoint_url_label}: </label></th>
        <td><props:textProperty name="${endpoint_url_param}" className="longField" />
            <span class="smallNote">${endpoint_url_note}</span><span class="error" id="error_${endpoint_url_param}"></span>

        </td>
    </tr>

    <tr id="script.content.container" class="scriptMode customScript" data-steps="${lambda_settings_step}">
        <th>
            <label for="${script_content_param}">${script_content_label}:<l:star/></label>
        </th>
        <td class="codeHighlightTD">
            <props:multilineProperty name="${script_content_param}" className="longField" cols="58" rows="10" expanded="true" linkTitle="Enter build script content"
                                     note="${script_content_note}"
                                     highlight="shell" />
        </td>
    </tr>
</l:settingsGroup>

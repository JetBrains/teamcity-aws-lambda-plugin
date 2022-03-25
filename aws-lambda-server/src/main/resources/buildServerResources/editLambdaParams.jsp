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

<%@include file="paramsConstants.jspf" %>

<jsp:include page="editAWSCommonParams.jsp">
    <jsp:param name="requireRegion" value="${true}"/>
    <jsp:param name="requireEnvironment" value="${false}"/>
</jsp:include>


<l:settingsGroup title="Lambda Settings">
    <tr data-steps="${lambda_settings_step}">
        <th><label for="${endpoint_url_param}">${endpoint_url_label}: </label></th>
        <td><props:textProperty name="${endpoint_url_param}" className="longField"/>
            <span class="smallNote">${endpoint_url_note}</span><span class="error"
                                                                     id="error_${endpoint_url_param}"></span>

        </td>
    </tr>

    <tr data-steps="${lambda_settings_step}">
        <th><label for="${ecr_image_param}">${ecr_image_label}: </label></th>
        <td><props:textProperty name="${ecr_image_param}" className="longField"/>
            <span class="smallNote">${ecr_image_note}</span><span class="error" id="error_${ecr_image_param}"></span>

        </td>
    </tr>

    <tr data-steps="${lambda_settings_step}">
        <th><label for="${memory_size_param}">${memory_size_label}: <l:star/></label></th>
        <td><props:textProperty name="${memory_size_param}" className="longField"/>
            <span class="smallNote">${memory_size_note}</span><span class="error"
                                                                    id="error_${memory_size_param}"></span>

        </td>
    </tr>

    <tr data-steps="${lambda_settings_step}">
        <th><label for="${iam_role_param}">${iam_role_label}: <l:star/></label></th>
        <td>
            <props:selectProperty name="${iam_role_param}" id="${iam_role_param}" className="longField">
                <props:option value="${null}">${iam_role_default_option}</props:option>
            </props:selectProperty>
            <i class="icon-magic" title="Create Default Execution Role" id="${iam_role_create_button}"></i>
            <span class="smallNote">${iam_role_note}</span><span class="error"
                                                                 id="error_${iam_role_param}"></span>
        </td>
    </tr>

    <tr id="script.content.container" class="scriptMode customScript" data-steps="${lambda_settings_step}">
        <th>
            <label for="${script_content_param}">${script_content_label}:<l:star/></label>
        </th>
        <td class="codeHighlightTD">
            <props:multilineProperty name="${script_content_param}" className="longField" cols="58" rows="10"
                                     expanded="true" linkTitle="Enter build script content"
                                     note="${script_content_note}"
                                     highlight="shell"/>
        </td>
    </tr>
</l:settingsGroup>

<script type="text/javascript">

    $j(document).ready(function () {

        const keyId = BS.Util.escapeId('aws.access.key.id');
        const keySecret = BS.Util.escapeId('secure:aws.secret.access.key');
        const iamRoleInput = $j(BS.Util.escapeId('${iam_role_param}'))
        const createIamRoleButton = $j(BS.Util.escapeId('${iam_role_create_button}'));

        let rolesList;


        function addOptionToSelector(selector, value, text) {
            return selector.append($j("<option data-title></option>").attr("value", value).text(text));
        }


        function drawRolesOptions(rolesList) {
            let rolesWithoutDefault
            if (rolesList.defaultRole) {
                addOptionToSelector(iamRoleInput, rolesList.defaultRole.roleArn, rolesList.defaultRole.roleName)
                rolesWithoutDefault = rolesList.iamRoleList.filter(role => role.roleArn.indexOf(rolesList.defaultRole.roleArn) === -1)
            } else {
                addOptionToSelector(iamRoleInput, "${iam_role_default_option}", "${iam_role_default_option}")
                rolesWithoutDefault = rolesList.iamRoleList
            }

            rolesWithoutDefault.forEach(role =>
                addOptionToSelector(iamRoleInput, role.roleArn, role.roleName)
            )
        }

        function getParameters() {
            const buildRunnerParams = BS.EditBuildRunnerForm.serializeParameters();
            const queryString = window.location.search;
            const urlParams = new URLSearchParams(queryString);

            return `id=` + urlParams.get('id') + '&' + buildRunnerParams
        }

        function loadIamRoles() {
            const parameters = getParameters();
            $j.post(window['base_uri'] + '${plugin_path}/${iam_roles_list_path}', parameters)
                .then(function (response) {
                    rolesList = $(response)
                    iamRoleInput.empty()

                    drawRolesOptions(rolesList)
                })
                .catch(error => {
                    if (error.status !== 403) {
                        throw error
                    }
                })
        }

        loadIamRoles()


        $j(document).on('change', keyId + ', ' + keySecret, function () {
            loadIamRoles()
        });

        function createIamRole() {
            const parameters = getParameters()
            if (!rolesList.defaultRole) {
                $j.post(window['base_uri'] + '${plugin_path}/${iam_roles_create_path}', parameters)
                    .then(function (response) {
                        rolesList.defaultRole = $(response)
                        iamRoleInput.empty()

                        drawRolesOptions(rolesList)
                    })
                    .catch(error => {
                        if (error.status !== 403) {
                            throw error
                        }
                    })
            }
        }

        createIamRoleButton.on('click', function () {
            createIamRole()
            return false
        })
    })
</script>

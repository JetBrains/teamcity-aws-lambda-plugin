

<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>

<%@include file="paramsConstants.jspf" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<c:set var="selected_iam_role" value="${propertiesBean.properties[iam_role_param]}"/>
<%@include file="awsConnection/awsConnectionConstants.jspf"%>
<jsp:useBean id="buildForm"  scope="request" type="jetbrains.buildServer.controllers.admin.projects.EditableBuildTypeSettingsForm"/>


<jsp:include page="awsConnection/availableAwsConnections/availableAwsConnections.jsp">
    <jsp:param name="projectId" value="${buildForm.project.externalId}"/>
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
        <th><label for="${storage_size_param}">${storage_size_label}: <l:star/></label></th>
        <td><props:textProperty name="${storage_size_param}" className="longField"/>
            <span class="smallNote">${storage_size_note}</span><span class="error"
                                                                    id="error_${storage_size_param}"></span>

        </td>
    </tr>

    <tr data-steps="${lambda_settings_step}">
        <th><label for="${iam_role_param}">${iam_role_label}: <l:star/></label></th>
        <td>
            <props:selectProperty name="${iam_role_param}" id="${iam_role_param}" className="longField">
                <props:option value="${null}">${iam_role_default_option}</props:option>
            </props:selectProperty>
            <i class="icon-magic" title="Create Default Execution Role" id="${iam_role_create_button}"></i>
            <span class="smallNote">${iam_role_note}</span>
            <span class="error"
                  id="error_${iam_role_param}"></span>
        </td>
    </tr>

    <tr id="script.content.container" class="scriptMode customScript" data-steps="${lambda_settings_step}">
        <th>
            <label for="${script_content_param}">${script_content_label}<l:star/></label>
        </th>
        <td class="codeHighlightTD">
            <props:multilineProperty name="${script_content_param}" className="longField" cols="58" rows="10"
                                     expanded="true" linkTitle="Enter build script content"
                                     note="${script_content_note}"
                                     highlight="shell"/>
        </td>
    </tr>
</l:settingsGroup>

<props:hiddenProperty name="${project_id_param}" value="${buildForm.project.externalId}"/>

<script type="text/javascript">

    $j(document).ready(function () {

        const iamRoleInput = $j(BS.Util.escapeId('${iam_role_param}'))
        const iamRoleError = $j(BS.Util.escapeId('error_${iam_role_param}'));
        const createIamRoleButton = $j(BS.Util.escapeId('${iam_role_create_button}'));
        const selectId = BS.Util.escape('${avail_connections_select_id}');
        const availableConnectionsSelectId = "#" + selectId;
        const availableConnectionsInput = "#-ufd-teamcity-ui-" + selectId;
        const chosenConnectionId = "&prop%3A" + '${chosen_aws_conn_id}';
        const selectedIamRole = '${selected_iam_role}'

        let rolesList;


        function addOptionToSelector(selector, value, text) {
            return selector.append($j("<option data-title></option>").attr("value", value).text(text));
        }


        function drawRolesOptions(rolesList) {
            let rolesWithoutDefault
            if (selectedIamRole.length !== 0){
              const selectedIamRoleSettings = rolesList.iamRoleList.find(role => role.roleArn === selectedIamRole);
              addOptionToSelector(iamRoleInput, selectedIamRoleSettings.roleArn, selectedIamRoleSettings.roleName);
              rolesWithoutDefault = rolesList.iamRoleList.filter(role => role.roleArn !== selectedIamRole);
            } else if (rolesList.defaultRole) {
                addOptionToSelector(iamRoleInput, rolesList.defaultRole.roleArn, rolesList.defaultRole.roleName)
                rolesWithoutDefault = rolesList.iamRoleList.filter(role => !role.roleArn.endsWith(rolesList.defaultRole.roleArn))
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

            return `${build_type_id_param}=` + urlParams.get('id') + '&' + buildRunnerParams
        }

        function clearForms() {
            iamRoleInput.empty()
            iamRoleError.empty()
        }

        function loadIamRoles() {
            const selectedConnection = $j(availableConnectionsInput).val()
            const parameters = getParameters();
            if (!selectedConnection || selectedConnection.empty() || parameters.indexOf(chosenConnectionId) == -1){
                return;
            }
            BS.ErrorsAwareListener.onBeginSave(BS.EditBuildRunnerForm);
            loadingChanges()
            $j.post(window['base_uri'] + '${plugin_path}/${iam_roles_list_path}', parameters)
                .then(function (response) {
                    rolesList = response
                    clearForms();

                    drawRolesOptions(rolesList)
                    BS.ErrorsAwareListener.onCompleteSave(BS.EditBuildRunnerForm)
                })
                .catch(error => {

                    iamRoleError.text("Error getting the IAM Roles: " + error.responseText);
                    BS.ErrorsAwareListener.onCompleteSave(BS.EditBuildRunnerForm, "<errors/>", true);
                    if (error.status !== 403) {
                        throw error
                    }
                }).always(() => {
                finishLoadingChanges();
            })
        }

        loadIamRoles();


        $j(availableConnectionsSelectId).change(function () {
            loadIamRoles();
        });

        $j(availableConnectionsSelectId).on("DOMNodeInserted", function () {
            loadIamRoles();
        });

        function loadingChanges() {
            createIamRoleButton
                .removeClass('icon-magic')
                .addClass("ring-loader-inline")
                .addClass("progressRing")
                .addClass("progressRingInline")
        }

        function finishLoadingChanges() {
            createIamRoleButton
                .removeClass("ring-loader-inline")
                .removeClass("progressRing")
                .removeClass("progressRingInline")
                .addClass('icon-magic')
        }

        function createIamRole() {
            const parameters = getParameters()
            loadingChanges();
            BS.ErrorsAwareListener.onBeginSave(BS.EditBuildRunnerForm)
            if (!rolesList?.defaultRole) {
                $j.post(window['base_uri'] + '${plugin_path}/${iam_roles_create_path}', parameters)
                    .then(function (response) {
                        rolesList.defaultRole = $(response)
                        clearForms()

                        drawRolesOptions(rolesList)
                        BS.ErrorsAwareListener.onCompleteSave(BS.EditBuildRunnerForm)
                    })
                    .catch(error => {
                        iamRoleError.text("Error creating the Default IAM Role: " + error.responseText)
                        BS.ErrorsAwareListener.onCompleteSave(BS.EditBuildRunnerForm, "<errors/>", true)
                        if (error.status !== 403) {
                            throw error
                        }
                    }).always(() => {
                    finishLoadingChanges();
                })
            }
        }

        createIamRoleButton.on('click', function () {
            createIamRole()
            return false
        })
    })
</script>
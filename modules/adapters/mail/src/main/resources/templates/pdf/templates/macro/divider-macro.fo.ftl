<#escape x as x?html>
    <#include "../constants.ftl">

    <#macro divider>
        <fo:block text-align-last="justify" margin-top="14px" margin-bottom="14px">
            <fo:leader leader-pattern="rule" leader-length="100%" rule-style="solid" rule-thickness="1px"
                       color="${LIGHT_GREEN}"/>
        </fo:block>
    </#macro>
</#escape>


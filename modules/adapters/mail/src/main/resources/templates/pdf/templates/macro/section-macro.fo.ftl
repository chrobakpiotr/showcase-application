<#escape x as x?html>
    <#include "../constants.ftl">

    <#macro section name>
        <fo:block keep-together.within-page="always">
            <fo:block background-color="${DARK_GREEN}">
                <fo:block color="${WHITE}" font-weight="bold" font-size="10pt"
                          padding-top="4px" padding-bottom="1px" margin-left="10px"
                          margin-right="10px">
                    ${name}
                </fo:block>
            </fo:block>
            <fo:block background-color="${LIGHT_GREEN}"
                      padding-top="6px" padding-bottom="6px">
                <fo:table table-layout="fixed" margin-left="10px"
                          margin-right="10px">
                    <fo:table-column column-width="80%"/>
                    <fo:table-column column-width="80%"/>
                    <fo:table-body start-indent="0" end-indent="0">
                        <#nested/>
                    </fo:table-body>
                </fo:table>
            </fo:block>
        </fo:block>
    </#macro>
</#escape>


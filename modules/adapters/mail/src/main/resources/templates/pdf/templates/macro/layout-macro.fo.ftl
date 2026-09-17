<#escape x as x?html>
    <#macro layout>
        <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
            <fo:layout-master-set>
                <fo:simple-page-master master-name="master" page-height="250mm" page-width="200mm"
                                       margin-top="10mm" margin-bottom="10mm" margin-left="20mm" margin-right="20mm">

                    <fo:region-body region-name="content" margin-top="25mm" margin-bottom="25mm"/>
                    <fo:region-before region-name="header" extent="25mm"/>
                    <fo:region-after region-name="footer" extent="25mm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <fo:page-sequence master-reference="master"
                              font-family="Arial">

                <fo:static-content flow-name="footer" font-size="8pt">
                    <fo:block text-align-last="justify">
                        <fo:inline>
                            ${orderNumber}
                        </fo:inline>
                    </fo:block>
                </fo:static-content>

                <fo:flow flow-name="content" font-size="10pt">
                    <#nested/>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </#macro>
</#escape>

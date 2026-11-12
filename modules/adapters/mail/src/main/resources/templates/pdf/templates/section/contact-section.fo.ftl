<#escape x as x?html>
    <@section.section name='${message("pdf.contact-information")}'>
        <fo:table-row font-weight="bold">
            <fo:table-cell>
                <fo:table table-layout="fixed">
                    <fo:table-body>
                        <fo:table-cell>
                            <#if contact.fullName??>
                                <fo:block margin-top="${BLOCK_MARGIN}">
                                    ${(contact.fullName)!}
                                </fo:block>
                            </#if>
                            <#if contact.phone??>
                                <fo:block margin-top="${BLOCK_MARGIN}">
                                    ${message("pdf.contact.phone")} ${(contact.phone)!}
                                </fo:block>
                            </#if>
                            <#if contact.email??>
                                <fo:block margin-top="${BLOCK_MARGIN}">
                                    ${message("pdf.contact.email")} ${(contact.email.address)!}
                                </fo:block>
                            </#if>
                        </fo:table-cell>
                    </fo:table-body>
                </fo:table>
            </fo:table-cell>
        </fo:table-row>
        <fo:table-row>
            <fo:table-cell>
                <fo:table table-layout="fixed">
                    <fo:table-body>
                        <fo:table-row>
                            <fo:table-cell>
                                <fo:block margin-top="${BLOCK_MARGIN}">
                                    ${(contact.address.street)!},
                                    ${(contact.address.city)!}<#if (contact.address.postalCode)??>,</#if>
                                    ${(contact.address.postalCode)!}
                                </fo:block>
                                <fo:block margin-top="${BLOCK_MARGIN}">
                                    ${(contact.address.countryCode)!}
                                </fo:block>
                            </fo:table-cell>
                        </fo:table-row>
                    </fo:table-body>
                </fo:table>
            </fo:table-cell>
        </fo:table-row>
    </@section.section>
</#escape>

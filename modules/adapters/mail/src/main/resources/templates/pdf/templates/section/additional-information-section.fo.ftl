<#escape x as x?html>
    <@section.section name='${message("pdf.additional-information")}'>
        <fo:table-row font-weight="bold">
            <fo:table-cell>
                <fo:table table-layout="fixed">
                    <fo:table-body>
                        <fo:table-cell>
                            <fo:block>
                                ${message("pdf.remarks.title")}: ${remarks!}
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-body>
                </fo:table>
            </fo:table-cell>
        </fo:table-row>
    </@section.section>
</#escape>

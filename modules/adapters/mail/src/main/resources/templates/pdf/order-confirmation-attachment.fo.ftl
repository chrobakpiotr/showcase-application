<?xml version="1.0" encoding="UTF-8"?>
<#escape x as x?html>
<#include "templates/constants.ftl">
<#import "templates/macro/section-macro.fo.ftl" as section>
<#import "templates/macro/layout-macro.fo.ftl" as layout>
<#import "templates/macro/divider-macro.fo.ftl" as divider>

<@layout.layout>
<fo:block  font-weight="bold" color="${DARK_GREEN}" font-size="14pt">
    <#noescape>${message("pdf.order.number")} ${orderNumber!}</#noescape>
</fo:block>
<fo:block>
    <#noescape>${message("pdf.order.date")} ${currentDate!}</#noescape>
</fo:block>
<@divider.divider/>
<#include "templates/section/contact-section.fo.ftl">
<@divider.divider/>
<#include "templates/section/additional-information-section.fo.ftl">
</@layout.layout>
</#escape>

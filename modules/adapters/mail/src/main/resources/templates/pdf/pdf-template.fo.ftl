<?xml version="1.0" encoding="utf-8"?>
<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
    <fo:layout-master-set>
        <fo:simple-page-master master-name="simple"
                               page-height="25cm"
                               page-width="20cm"
                               margin-top="1cm"
                               margin-bottom="1cm"
                               margin-left="2cm"
                               margin-right="2cm">
            <fo:region-body margin-top="3cm"/>
            <fo:region-before extent="3cm"/>
            <fo:region-after extent="2cm"/>
        </fo:simple-page-master>
    </fo:layout-master-set>
    <fo:page-sequence master-reference="simple">
        <fo:flow flow-name="xsl-region-body">
            <!-- title -->
            <fo:block font-size="17pt"
                      font-family="sans-serif"
                      line-height="20pt"
                      space-after.optimum="14pt"
                      background-color="white"
                      color="black"
                      text-align="center"
                      padding-top="2pt">
                ${message!}
            </fo:block>
            <!-- normal text -->
            <fo:block font-size="10pt"
                      font-family="sans-serif"
                      line-height="12pt"
                      space-after.optimum="3pt"
                      text-align="justify">
                Lorem ipsum dolor sit amet.
            </fo:block>

        </fo:flow>
    </fo:page-sequence>
</fo:root>

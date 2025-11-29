package com.arun.temporal.worker.constant;

public class ReportForm {
    private ReportForm() {
    }

    public static final String REPORT_FORM = """
             Summary Report
            +-----------------------------------------------------------------------------+
            |A. Software                                                                  |
            |-----------------------------------------------------------------------------|
            |1.  Report Certified        |2.  Certified Software        |3. Configuration  |
            |    Company Name            |    Name/Version             |                  |
            |                            |                             |                  |
            |    %s|    %s |   CAS            |
            |                            |    %s |                  |
            |B. Report                                                                      |
            |-----------------------------------------------------------------------------|
            |1. Customer Processor's Name|2. Date Data            |3. Date of Created    |
            |                            |   Processed            |   Product Used        |
            |   %s | %s  | %s |
            |   %s |                    |                      |
            |   %s |                    |                      |
            |-----------------------------------------------------------------------------|
            |D. Mailer                                                                    |
            |-----------------------------------------------------------------------------|
            |I certify that the mailing submitted  |3. Name and Address of Mailer         |
            |with this form has been coded (as     | %s |
            |indicated above) using CASS Certified | %s |
            |software meeting all the requirements | %s |
            |listed in the DMM Section 708.        | %s |
            |--------------------------------------| %s |
            |1. Mailer's Signature  |2. Date Signed| %s |
            |                       |              | %s |
            |                       |              | %s |
            """;
}

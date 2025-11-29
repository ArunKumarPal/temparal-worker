package com.arun.temporal.worker.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.naming.Named;

@ConfigurationProperties("report-config")
public interface ReportConfiguration extends Named {
    String getVendorName();

    String getSoftwareName();

    String getSoftwareVersion();

    String getDataVintage();
}

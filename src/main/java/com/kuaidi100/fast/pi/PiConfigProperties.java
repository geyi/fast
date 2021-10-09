package com.kuaidi100.fast.pi;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pi")
public class PiConfigProperties {
    private String piFilePath = "/root/fast/data/pi-200m.txt";
    private String piFileDir = "config/pi";
    private Integer readerCount = 8;
    private Integer piLength = 250000000;
    private Integer queryLength = 100;
    private Integer maxAllowMissCount = 9;

    public String getPiFilePath() {
        return piFilePath;
    }

    public void setPiFilePath(String piFilePath) {
        this.piFilePath = piFilePath;
    }

    public String getPiFileDir() {
        return piFileDir;
    }

    public void setPiFileDir(String piFileDir) {
        this.piFileDir = piFileDir;
    }

    public Integer getReaderCount() {
        return readerCount;
    }

    public void setReaderCount(Integer readerCount) {
        this.readerCount = readerCount;
    }

    public Integer getPiLength() {
        return piLength;
    }

    public void setPiLength(Integer piLength) {
        this.piLength = piLength;
    }

    public Integer getQueryLength() {
        return queryLength;
    }

    public void setQueryLength(Integer queryLength) {
        this.queryLength = queryLength;
    }

    public Integer getMaxAllowMissCount() {
        return maxAllowMissCount;
    }

    public void setMaxAllowMissCount(Integer maxAllowMissCount) {
        this.maxAllowMissCount = maxAllowMissCount;
    }
}

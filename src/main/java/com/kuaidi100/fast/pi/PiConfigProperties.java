package com.kuaidi100.fast.pi;

import com.kuaidi100.fast.server.Constant;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author xyg12
 */
@ConfigurationProperties(prefix = "pi")
public class PiConfigProperties {
    private String piFilePath = Constant.BASE_PATH + "pi-200m.txt";
    private Integer queryLength = 100;
    private Integer maxAllowMissCount = 9;
    private Integer queryThreadCount = 16;
    private Integer accumulationCount = 200000;
    private Boolean forceTiming = false;
    private Integer piLength = 250000000;

    public String getPiFilePath() {
        return piFilePath;
    }

    public void setPiFilePath(String piFilePath) {
        this.piFilePath = piFilePath;
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

    public Integer getQueryThreadCount() {
        return queryThreadCount;
    }

    public void setQueryThreadCount(Integer queryThreadCount) {
        this.queryThreadCount = queryThreadCount;
    }

    public Integer getAccumulationCount() {
        return accumulationCount;
    }

    public void setAccumulationCount(Integer accumulationCount) {
        this.accumulationCount = accumulationCount;
    }

    public Boolean getForceTiming() {
        return forceTiming;
    }

    public void setForceTiming(Boolean forceTiming) {
        this.forceTiming = forceTiming;
    }

    public Integer getPiLength() {
        return piLength;
    }

    public void setPiLength(Integer piLength) {
        this.piLength = piLength;
    }
}

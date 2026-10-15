package com.wavemaker.runtime.resolver;

/**
 * Stands in for the class of the same name in runtime-core, which runtime-db does not depend on. The registrar refers to
 * it by name, so the tests need a class with that name and the same properties.
 */
public class WMPageableHandlerMethodArgumentResolver {

    private String pageParameterName;
    private String sizeParameterName;
    private boolean oneIndexedParameters;
    private int maxPageSize;

    public void setPageParameterName(String pageParameterName) {
        this.pageParameterName = pageParameterName;
    }

    public void setSizeParameterName(String sizeParameterName) {
        this.sizeParameterName = sizeParameterName;
    }

    public void setOneIndexedParameters(boolean oneIndexedParameters) {
        this.oneIndexedParameters = oneIndexedParameters;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
}

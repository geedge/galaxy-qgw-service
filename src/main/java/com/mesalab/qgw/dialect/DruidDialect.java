package com.mesalab.qgw.dialect;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.QueryParam;

public class DruidDialect implements  Dialect {

    private String dbType;

    public DruidDialect(String dbType) {
        this.dbType = dbType;
    }

    @Override
    public boolean supportsLimit() {
        return false;
    }

    @Override
    public String getLimitString(String sql, int offset, int limit) {
        return null;
    }

    @Override
    public BaseResult executeHttpQuery(QueryParam param) {
        return null;
    }

    @Override
    public String getDbType() {
        return dbType;
    }
}

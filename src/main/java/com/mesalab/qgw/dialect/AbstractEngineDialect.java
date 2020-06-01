package com.mesalab.qgw.dialect;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.ApiParam;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;

@Data
public abstract class AbstractEngineDialect implements Dialect {
    public ApiParam param;
    public BaseResult baseResult;

    abstract void executeUDF();

    public void initialize(ApiParam param, BaseResult baseResult) {
        this.param = param;
        if (StringUtil.isNotEmpty(param.getEngineQuerySource())) {
            param.setQuery(param.getEngineQuerySource().getSqlBody());
        }

        this.baseResult = baseResult;

    }


}

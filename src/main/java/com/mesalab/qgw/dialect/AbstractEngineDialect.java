package com.mesalab.qgw.dialect;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.ApiParam;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;

import java.util.Optional;

@Data
public abstract class AbstractEngineDialect implements Dialect {
    public ApiParam param;
    public BaseResult baseResult;

    abstract  void init();

    public boolean isExecuteUDF() {
        return false;
    }

    abstract void executeUDF();

    /**
     * 解析并生成最终结果
     * @return
     */
    abstract  BaseResult generateBaseResult();


    @Override
    final public BaseResult executeQuery() {
        init();
        if (isExecuteUDF()) {
            executeUDF();
        }
        return generateBaseResult();
    }


    @Override
    final public BaseResult executeSyntaxCheck() {
        return  null;
    }

    @Override
    final public BaseResult executeAdministrativeQuery() {
        return  null;
    }




}

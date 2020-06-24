package com.mesalab.qgw.dialect;


import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.model.api.ApiParam;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;

@Data
public abstract class AbstractDataSourceDialect implements Dialect {
    public ApiParam param;
    public BaseResult baseResult;


    public void initialize(ApiParam param) {
        this.param = param;
        if (StringUtil.isNotEmpty(param.getDbQuerySource())) {
            param.setSql(param.getDbQuerySource().getSqlBody());
        } else {
            param.setSql(param.getQuery());
        }
        baseResult = BaseResultGenerator.success();
    }

    /**
     * SQL查询语句转换为方言所认知的查询语句。
     * @param sql
     * @return
     */
    abstract  String convertQuery(String sql);


    /**
     * 基于RBO(rule-base optimizer)获取最优化的SQL
     * @return
     */
    abstract  String findBestQuery();




}

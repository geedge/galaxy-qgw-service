package com.mesalab.calcite;

import com.mesalab.calcite.function.DateFunction;
import com.zdjizhi.calcite.schema.Schema;
import com.zdjizhi.calcite.schema.SchemaFactory;
import com.zdjizhi.calcite.schema.SchemaPlus;
import com.zdjizhi.calcite.schema.impl.ScalarFunctionImpl;

import java.util.Map;

public class InMemorySchemaFactory implements SchemaFactory {


    @Override
    public Schema create(SchemaPlus parentSchema, String name, Map<String, Object> operand) {
        parentSchema.add("FROM_UNIXTIME", ScalarFunctionImpl.create(DateFunction.class, "fromUnixTimeToDateTime24"));
        parentSchema.add("DATE_FORMAT", ScalarFunctionImpl.create(DateFunction.class, "dateFormat"));
        return new InMemorySchema(name, operand);
    }
}

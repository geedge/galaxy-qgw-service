package com.mesalab.calcite;

import com.mesalab.calcite.function.DateFunction;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaFactory;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;

import java.util.Map;

public class InMemorySchemaFactory implements SchemaFactory {


    @Override
    public Schema create(SchemaPlus parentSchema, String name, Map<String, Object> operand) {
        parentSchema.add("FROM_UNIXTIME", ScalarFunctionImpl.create(DateFunction.class, "fromUnixTimeToDateTime24"));
        return new InMemorySchema(name, operand);
    }
}

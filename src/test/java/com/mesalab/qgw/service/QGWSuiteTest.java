package com.mesalab.qgw.service;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({ ClickHouseTest.class, DruidTest.class, IpLocationTest.class,
        ProtocolTreeTest.class, AvroSchemaDynamicTest.class, AvroSchemaFormatTest.class})
public class QGWSuiteTest {


}




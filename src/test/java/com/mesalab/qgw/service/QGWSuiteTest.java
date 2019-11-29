package com.mesalab.qgw.service;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({GetSchemaInfo.class, MysqlCKDateTest.class, MysqlDruidDateTest.class, ClickHouseTest.class, DruidTest.class})
public class QGWSuiteTest {


}




package com.mesalab.qgw.service;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({MysqlCKDateTest.class, MysqlDruidDateTest.class, ClickHouseTest.class,SessionRecordQueryTest.class,PolicyEventLogQueryTest.class, ClickHouseReportTest.class, DruidTest.class})
public class QGWSuiteTest {


}




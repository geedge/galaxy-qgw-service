package com.mesalab.tsg.entity;

import lombok.Data;

import java.util.List;

@Data
public class QueryExp {

	protected String type;
	protected String fieldKey;
	protected List<Object> fieldValues;

	protected String operator;

	protected String qlSegment;

}
